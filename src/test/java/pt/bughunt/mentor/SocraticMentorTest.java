package pt.bughunt.mentor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa a LÓGICA do mentor sem chamar nenhum LLM real —
 * o FakeLlm captura os prompts para podermos verificar
 * que o prompt engineering está correto.
 */
class SocraticMentorTest {

    FakeLlm fakeLlm;
    SocraticMentor mentor;

    @BeforeEach
    void setup() {
        fakeLlm = new FakeLlm();
        mentor = new SocraticMentor(fakeLlm);
    }

    // ---------- Anti-vibecoding: o cap de nível ----------

    @Test
    @DisplayName("Explicação direta é BLOQUEADA antes de 3 tentativas falhadas")
    void directExplanationCappedBeforeThreeFailures() {
        var context = new SocraticMentor.StudentContext(
                "iniciante", List.of(), 1); // só 1 tentativa falhada

        var effective = mentor.capLevel(
                SocraticMentor.HintLevel.DIRECT_EXPLANATION, context);

        assertEquals(SocraticMentor.HintLevel.CONCEPT_EXPLANATION, effective,
                "utilizador não pode saltar direto para a resposta");
    }

    @Test
    @DisplayName("Explicação direta é permitida após 3 tentativas falhadas")
    void directExplanationAllowedAfterThreeFailures() {
        var context = new SocraticMentor.StudentContext(
                "iniciante", List.of(), 3);

        var effective = mentor.capLevel(
                SocraticMentor.HintLevel.DIRECT_EXPLANATION, context);

        assertEquals(SocraticMentor.HintLevel.DIRECT_EXPLANATION, effective);
    }

    @Test
    @DisplayName("Níveis baixos nunca são alterados pelo cap")
    void lowerLevelsPassThrough() {
        var context = new SocraticMentor.StudentContext("avancado", List.of(), 0);

        for (var level : List.of(
                SocraticMentor.HintLevel.SOCRATIC_QUESTION,
                SocraticMentor.HintLevel.LOCATION_HINT,
                SocraticMentor.HintLevel.CONCEPT_EXPLANATION)) {
            assertEquals(level, mentor.capLevel(level, context));
        }
    }

    // ---------- Prompt engineering ----------

    @Test
    @DisplayName("System prompt proíbe sempre escrever código corrigido")
    void systemPromptForbidsCode() {
        var context = new SocraticMentor.StudentContext("iniciante", List.of(), 0);

        for (var level : SocraticMentor.HintLevel.values()) {
            String prompt = mentor.buildSystemPrompt(level, context);
            assertTrue(prompt.contains("NUNCA escrevas código corrigido"),
                    "regra anti-código em falta no nível " + level);
        }
    }

    @Test
    @DisplayName("Nível socrático instrui o LLM a responder só com pergunta")
    void socraticLevelDemandsQuestion() {
        var context = new SocraticMentor.StudentContext("iniciante", List.of(), 0);
        String prompt = mentor.buildSystemPrompt(
                SocraticMentor.HintLevel.SOCRATIC_QUESTION, context);

        assertTrue(prompt.contains("APENAS com uma pergunta"));
    }

    @Test
    @DisplayName("Padrões de erro históricos do aluno entram no prompt — a personalização")
    void problematicConceptsInjectedIntoPrompt() {
        var context = new SocraticMentor.StudentContext(
                "intermedio",
                List.of("off-by-one", "null-pointer"),
                0);

        String prompt = mentor.buildSystemPrompt(
                SocraticMentor.HintLevel.LOCATION_HINT, context);

        assertTrue(prompt.contains("off-by-one"));
        assertTrue(prompt.contains("null-pointer"));
        assertTrue(prompt.contains("intermedio"));
    }

    @Test
    @DisplayName("Sem histórico problemático, o prompt não menciona padrões")
    void noHistoryNoPatternMention() {
        var context = new SocraticMentor.StudentContext("iniciante", List.of(), 0);
        String prompt = mentor.buildSystemPrompt(
                SocraticMentor.HintLevel.SOCRATIC_QUESTION, context);

        assertFalse(prompt.contains("dificuldade com"));
    }

    // ---------- Fluxo completo ----------

    @Test
    @DisplayName("provideHint envia desafio, código e output ao LLM")
    void fullFlowSendsAllContext() {
        var context = new SocraticMentor.StudentContext("iniciante", List.of(), 0);
        fakeLlm.cannedResponse = "O que acontece a i na última iteração?";

        String hint = mentor.provideHint(
                "Corrige o bug no ciclo",
                "for(int i=0;i<=arr.length;i++)",
                "ArrayIndexOutOfBoundsException: 5",
                context,
                SocraticMentor.HintLevel.SOCRATIC_QUESTION);

        assertEquals("O que acontece a i na última iteração?", hint);
        assertTrue(fakeLlm.lastUserPrompt.contains("Corrige o bug no ciclo"));
        assertTrue(fakeLlm.lastUserPrompt.contains("i<=arr.length"));
        assertTrue(fakeLlm.lastUserPrompt.contains("ArrayIndexOutOfBoundsException"));
    }

    // ---------- Fake ----------

    static class FakeLlm implements SocraticMentor.LlmClient {
        String cannedResponse = "resposta simulada";
        String lastSystemPrompt;
        String lastUserPrompt;

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.lastSystemPrompt = systemPrompt;
            this.lastUserPrompt = userPrompt;
            return cannedResponse;
        }
    }
}
