package pt.bughunt.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pt.bughunt.sandbox.CommandRunner;
import pt.bughunt.sandbox.ExecutionRequest.Language;
import pt.bughunt.sandbox.SandboxExecutor;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa o pipeline de validação de desafios gerados —
 * o mecanismo que impede LLM hallucinations de chegarem aos alunos.
 * O ScriptedCommandRunner devolve resultados pré-programados,
 * simulando as execuções sandbox do código buggy e do fix.
 */
class ChallengeGeneratorTest {

    @TempDir
    Path tempDir;

    ScriptedCommandRunner runner;
    ChallengeGenerator generator;
    FakeSource source;

    static final ChallengeGenerator.GeneratedChallenge CANDIDATE =
            new ChallengeGenerator.GeneratedChallenge(
                    "Soma das notas",
                    "Corrige o bug no cálculo",
                    "buggy code here",
                    "fixed code here",
                    "Media: 15.5");

    @BeforeEach
    void setup() {
        runner = new ScriptedCommandRunner();
        source = new FakeSource();
        generator = new ChallengeGenerator(source,
                new SandboxExecutor(runner, tempDir));
    }

    @Test
    @DisplayName("Candidato válido: bug manifesta-se E fix produz o output esperado")
    void validCandidatePasses() {
        runner.script(1, "Media: 7.0", "");     // buggy: output ERRADO ✓
        runner.script(0, "Media: 15.5", "");    // fix: output correto ✓

        var outcome = generator.validate(CANDIDATE, "tipos", Language.JAVA, 2);

        assertInstanceOf(ChallengeGenerator.ValidationOutcome.Valid.class, outcome);
        var valid = (ChallengeGenerator.ValidationOutcome.Valid) outcome;
        assertEquals("tipos", valid.challenge().conceptId());
        assertEquals(150, valid.challenge().basePoints(), "dificuldade 2 → 150 pts");
    }

    @Test
    @DisplayName("REJEITADO: código 'buggy' que produz o output correto (bug não existe)")
    void buggyCodeThatWorksIsRejected() {
        runner.script(0, "Media: 15.5", "");    // buggy produz o output esperado ✗

        var outcome = generator.validate(CANDIDATE, "tipos", Language.JAVA, 2);

        assertInstanceOf(ChallengeGenerator.ValidationOutcome.BugDoesNotManifest.class, outcome);
    }

    @Test
    @DisplayName("REJEITADO: fix canónico que não produz o output esperado")
    void brokenFixIsRejected() {
        runner.script(1, "Media: 7.0", "");     // buggy falha ✓
        runner.script(0, "Media: 99.9", "");    // fix produz output errado ✗

        var outcome = generator.validate(CANDIDATE, "tipos", Language.JAVA, 2);

        assertInstanceOf(ChallengeGenerator.ValidationOutcome.FixDoesNotWork.class, outcome);
    }

    @Test
    @DisplayName("REJEITADO: fix que nem sequer compila")
    void fixThatDoesNotCompileIsRejected() {
        runner.script(1, "Media: 7.0", "");
        runner.script(1, "", "Main.java:5: error: ';' expected");  // fix não compila ✗

        var outcome = generator.validate(CANDIDATE, "tipos", Language.JAVA, 2);

        assertInstanceOf(ChallengeGenerator.ValidationOutcome.FixDoesNotWork.class, outcome);
    }

    @Test
    @DisplayName("Comparação de output normaliza \\r\\n e whitespace")
    void outputComparisonNormalizes() {
        runner.script(1, "errado", "");
        runner.script(0, "  Media: 15.5\r\n", "");   // fix com whitespace extra

        var outcome = generator.validate(CANDIDATE, "tipos", Language.JAVA, 2);

        assertInstanceOf(ChallengeGenerator.ValidationOutcome.Valid.class, outcome);
    }

    @Test
    @DisplayName("generateValidated faz retry: 1ª geração má, 2ª boa → devolve a 2ª")
    void retryUntilValid() {
        // 1ª candidata: buggy funciona (inválida)
        runner.script(0, "Media: 15.5", "");
        // 2ª candidata: buggy falha, fix funciona (válida)
        runner.script(1, "errado", "");
        runner.script(0, "Media: 15.5", "");

        var result = generator.generateValidated("tipos", Language.JAVA, 2, 3);

        assertTrue(result.isPresent());
        assertEquals(2, source.calls, "deve ter pedido 2 gerações ao LLM");
    }

    @Test
    @DisplayName("generateValidated desiste após maxAttempts e devolve empty")
    void givesUpAfterMaxAttempts() {
        // Todas as candidatas inválidas (buggy sempre funciona)
        for (int i = 0; i < 3; i++) runner.script(0, "Media: 15.5", "");

        var result = generator.generateValidated("tipos", Language.JAVA, 2, 3);

        assertTrue(result.isEmpty());
        assertEquals(3, source.calls);
    }

    @Test
    @DisplayName("Pontos base escalam com a dificuldade")
    void pointsScaleWithDifficulty() {
        assertEquals(100, ChallengeGenerator.basePointsFor(1));
        assertEquals(400, ChallengeGenerator.basePointsFor(4));
        assertEquals(600, ChallengeGenerator.basePointsFor(5));
    }

    // ---------- Parse do JSON do LLM ----------

    @Test
    @DisplayName("Parse extrai os 5 campos, incluindo escapes de \\n no código")
    void jsonParseWithEscapes() {
        String json = """
                {"title":"Teste","description":"Desc","buggyCode":"linha1\\nlinha2",\
                "fixedCode":"fix","expectedOutput":"ok"}""";

        var parsed = LlmChallengeSource.parse(json);

        assertEquals("Teste", parsed.title());
        assertEquals("linha1\nlinha2", parsed.buggyCode());
    }

    @Test
    @DisplayName("Parse rejeita JSON sem campos obrigatórios — força retry")
    void jsonParseMissingFieldThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                LlmChallengeSource.parse("{\"title\":\"só isto\"}"));
    }

    // ---------- Fakes ----------

    static class FakeSource implements ChallengeGenerator.ChallengeSource {
        int calls = 0;
        @Override
        public ChallengeGenerator.GeneratedChallenge generate(
                String conceptId, Language language, int difficulty) {
            calls++;
            return CANDIDATE;
        }
    }

    /** Runner com fila de resultados pré-programados (um por execução). */
    static class ScriptedCommandRunner implements CommandRunner {
        final Deque<CommandResult> queue = new ArrayDeque<>();

        void script(int exitCode, String stdout, String stderr) {
            queue.addLast(new CommandResult(exitCode, stdout, stderr));
        }

        @Override
        public CommandResult run(List<String> command, String stdin, long timeoutMillis) {
            if (queue.isEmpty()) throw new IllegalStateException("resultado não programado");
            return queue.removeFirst();
        }
    }
}
