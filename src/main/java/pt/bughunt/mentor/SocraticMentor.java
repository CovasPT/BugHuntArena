package pt.bughunt.mentor;

import java.util.List;

/**
 * O mentor socrático — a peça que distingue o BugHunt Arena
 * de "mais um ChatGPT wrapper".
 *
 * Regras de design:
 *  1. NUNCA dá a resposta diretamente. O system prompt proíbe-o
 *     e o nível de ajuda é progressivo (HintLevel).
 *  2. Recebe o CONTEXTO do perfil do utilizador (taxa de erro,
 *     padrões detetados pelo ErrorPatternTracker) para adaptar
 *     a explicação — isto é o que o ChatGPT genérico não faz.
 *  3. O LlmClient é uma interface → testável sem chamadas reais.
 */
public class SocraticMentor {

    /** Níveis progressivos de ajuda — o utilizador "gasta" pontos por nível. */
    public enum HintLevel {
        /** Só uma pergunta orientadora. Ex: "O que acontece ao índice na última iteração?" */
        SOCRATIC_QUESTION,
        /** Aponta a ZONA do problema sem dizer o quê. Ex: "Olha com atenção para a condição do ciclo." */
        LOCATION_HINT,
        /** Explica o CONCEITO envolvido, não o erro específico. */
        CONCEPT_EXPLANATION,
        /** Explica o erro concreto — mas só depois de 3 tentativas falhadas. */
        DIRECT_EXPLANATION
    }

    public interface LlmClient {
        String complete(String systemPrompt, String userPrompt);
    }

    public record StudentContext(
            String skillLevel,              // "iniciante", "intermedio", "avancado"
            List<String> problematicConcepts,
            int failedAttemptsOnThisChallenge
    ) {}

    private final LlmClient llm;

    public SocraticMentor(LlmClient llm) {
        this.llm = llm;
    }

    public String provideHint(String challengeDescription,
                              String submittedCode,
                              String executionOutput,
                              StudentContext context,
                              HintLevel requestedLevel) {

        HintLevel effective = capLevel(requestedLevel, context);
        String systemPrompt = buildSystemPrompt(effective, context);
        String userPrompt = buildUserPrompt(challengeDescription, submittedCode, executionOutput);
        return llm.complete(systemPrompt, userPrompt);
    }

    /**
     * Anti-vibecoding: explicação direta só é permitida após
     * 3 tentativas falhadas. Antes disso, o nível é limitado
     * mesmo que o utilizador peça mais.
     */
    HintLevel capLevel(HintLevel requested, StudentContext context) {
        if (requested == HintLevel.DIRECT_EXPLANATION
                && context.failedAttemptsOnThisChallenge() < 3) {
            return HintLevel.CONCEPT_EXPLANATION;
        }
        return requested;
    }

    String buildSystemPrompt(HintLevel level, StudentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                És um mentor de programação numa plataforma de aprendizagem.
                A tua missão é fazer o aluno PENSAR, nunca dar código pronto.

                REGRAS ABSOLUTAS:
                - NUNCA escrevas código corrigido, nem parcial.
                - NUNCA digas diretamente qual é a linha do erro, exceto no nível DIRECT.
                - Responde em português europeu.
                - Máximo 4 frases.
                """);

        sb.append("\nNÍVEL DE AJUDA PERMITIDO: ").append(switch (level) {
            case SOCRATIC_QUESTION -> """
                    SOCRATIC — responde APENAS com uma pergunta que guie o aluno
                    a descobrir o problema sozinho. Nada de afirmações.""";
            case LOCATION_HINT -> """
                    LOCATION — indica a zona geral do código onde deve olhar
                    (ex: 'a condição do ciclo', 'a inicialização'), sem dizer o erro.""";
            case CONCEPT_EXPLANATION -> """
                    CONCEPT — explica o conceito teórico envolvido no erro
                    (ex: como funcionam índices de arrays), sem apontar o erro concreto.""";
            case DIRECT_EXPLANATION -> """
                    DIRECT — podes explicar o erro concreto e porquê,
                    mas continua sem escrever o código da solução.""";
        });

        sb.append("\n\nPERFIL DO ALUNO: nível ").append(context.skillLevel());
        if (!context.problematicConcepts().isEmpty()) {
            sb.append(". Historicamente tem dificuldade com: ")
              .append(String.join(", ", context.problematicConcepts()))
              .append(". Se o erro atual estiver relacionado com um destes padrões, ")
              .append("menciona-o com empatia (ex: 'já vimos este padrão antes').");
        }
        return sb.toString();
    }

    private String buildUserPrompt(String challenge, String code, String output) {
        return """
                DESAFIO:
                %s

                CÓDIGO SUBMETIDO:
                ```
                %s
                ```

                OUTPUT DA EXECUÇÃO:
                %s
                """.formatted(challenge, code, output);
    }
}
