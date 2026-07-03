package pt.bughunt.generator;

import pt.bughunt.mentor.SocraticMentor;
import pt.bughunt.sandbox.ExecutionRequest;

/**
 * Implementação do ChallengeSource que usa um LLM.
 * O prompt pede JSON estrito; o parse é validado e, em caso de
 * JSON inválido, lança — o ChallengeGenerator faz retry.
 *
 * Nota pedagógica no prompt: os desafios são para alunos do
 * ensino secundário — vocabulário acessível, contexto português.
 */
public class LlmChallengeSource implements ChallengeGenerator.ChallengeSource {

    private final SocraticMentor.LlmClient llm;

    public LlmChallengeSource(SocraticMentor.LlmClient llm) {
        this.llm = llm;
    }

    private static final String SYSTEM_PROMPT = """
            Geras desafios de debugging para alunos do ensino secundário português.
            Cada desafio é um programa CURTO (máx 25 linhas) com UM bug plantado
            do conceito pedido. O programa é autocontido: corre sem input,
            sem ficheiros, sem rede, e imprime output determinístico.

            REGRAS:
            - O bug tem de ser realista (o tipo de erro que um aluno comete),
              não artificial.
            - O output esperado refere-se ao código CORRIGIDO.
            - O código buggy tem de compilar OU falhar de forma pedagógica.
            - Vocabulário acessível, exemplos com contexto português
              (notas de 0-20, nomes portugueses, euros).
            - Nada de conteúdo impróprio para menores.

            Responde APENAS com JSON válido, sem markdown, neste formato exato:
            {"title":"...","description":"...","buggyCode":"...","fixedCode":"...","expectedOutput":"..."}
            """;

    @Override
    public ChallengeGenerator.GeneratedChallenge generate(
            String conceptId, ExecutionRequest.Language language, int difficulty) {

        String userPrompt = """
                Conceito: %s
                Linguagem: %s
                Dificuldade: %d/5
                Gera um desafio novo e original.
                """.formatted(conceptId, language, difficulty);

        String json = llm.complete(SYSTEM_PROMPT, userPrompt);
        return parse(json);
    }

    /** Parse minimalista dos 5 campos do JSON. Lança se faltar algum. */
    static ChallengeGenerator.GeneratedChallenge parse(String json) {
        return new ChallengeGenerator.GeneratedChallenge(
                extract(json, "title"),
                extract(json, "description"),
                extract(json, "buggyCode"),
                extract(json, "fixedCode"),
                extract(json, "expectedOutput"));
    }

    private static String extract(String json, String field) {
        String marker = "\"" + field + "\":";
        int idx = json.indexOf(marker);
        if (idx < 0) throw new IllegalArgumentException("Campo em falta: " + field);
        int start = json.indexOf('"', idx + marker.length()) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u' -> { sb.append((char) Integer.parseInt(json, i + 1, i + 5, 16)); i += 4; }
                    default -> sb.append(next);
                }
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("JSON malformado no campo: " + field);
    }
}
