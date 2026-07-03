package pt.bughunt.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do SandboxExecutor SEM precisar de Docker instalado —
 * o FakeCommandRunner simula os resultados do container.
 * Isto permite correr a suite no CI (GitHub Actions) em segundos.
 */
class SandboxExecutorTest {

    @TempDir
    Path tempDir;

    FakeCommandRunner fakeRunner;
    SandboxExecutor executor;

    @BeforeEach
    void setup() {
        fakeRunner = new FakeCommandRunner();
        executor = new SandboxExecutor(fakeRunner, tempDir);
    }

    // ---------- Casos de sucesso ----------

    @Test
    @DisplayName("Código válido devolve Success com o stdout")
    void successfulExecution() {
        fakeRunner.nextResult = new CommandRunner.CommandResult(0, "Hello BugHunt\n", "");

        var result = executor.execute(ExecutionRequest.of(
                ExecutionRequest.Language.JAVA,
                "public class Main { public static void main(String[] a){ System.out.println(\"Hello BugHunt\"); } }"));

        assertInstanceOf(ExecutionResult.Success.class, result);
        var success = (ExecutionResult.Success) result;
        assertEquals("Hello BugHunt\n", success.stdout());
    }

    // ---------- Erros de compilação ----------

    @Test
    @DisplayName("Erro de compilação Java é classificado como CompileError")
    void javaCompileError() {
        fakeRunner.nextResult = new CommandRunner.CommandResult(1, "",
                "Main.java:3: error: ';' expected\n        System.out.println(\"oops\")\n");

        var result = executor.execute(ExecutionRequest.of(
                ExecutionRequest.Language.JAVA, "codigo com erro de sintaxe"));

        assertInstanceOf(ExecutionResult.CompileError.class, result);
        var error = (ExecutionResult.CompileError) result;
        assertTrue(error.compilerOutput().contains("';' expected"));
    }

    @Test
    @DisplayName("SyntaxError em Python é classificado como CompileError")
    void pythonSyntaxError() {
        fakeRunner.nextResult = new CommandRunner.CommandResult(1, "",
                "  File \"main.py\", line 2\n    print(x\nSyntaxError: unexpected EOF");

        var result = executor.execute(ExecutionRequest.of(
                ExecutionRequest.Language.PYTHON, "print(x"));

        assertInstanceOf(ExecutionResult.CompileError.class, result);
    }

    // ---------- Erros de runtime ----------

    @Test
    @DisplayName("Exit code != 0 sem sinais de compilação é RuntimeError")
    void runtimeError() {
        fakeRunner.nextResult = new CommandRunner.CommandResult(1,
                "a correr...\n",
                "Exception in thread \"main\" java.lang.ArithmeticException: / by zero");

        var result = executor.execute(ExecutionRequest.of(
                ExecutionRequest.Language.JAVA, "int x = 1/0;"));

        assertInstanceOf(ExecutionResult.RuntimeError.class, result);
        var error = (ExecutionResult.RuntimeError) result;
        assertEquals(1, error.exitCode());
        assertTrue(error.stderr().contains("ArithmeticException"));
    }

    // ---------- Segurança ----------

    @Test
    @DisplayName("Tentativa de acesso à rede é detetada como SecurityViolation")
    void networkAccessBlocked() {
        fakeRunner.nextResult = new CommandRunner.CommandResult(1, "",
                "java.net.SocketException: Network is unreachable");

        var result = executor.execute(ExecutionRequest.of(
                ExecutionRequest.Language.JAVA,
                "new java.net.Socket(\"evil.com\", 80);"));

        assertInstanceOf(ExecutionResult.SecurityViolation.class, result);
    }

    @Test
    @DisplayName("Exit code 137 (OOM kill) é SecurityViolation de memória")
    void memoryLimitExceeded() {
        fakeRunner.nextResult = new CommandRunner.CommandResult(137, "", "");

        var result = executor.execute(ExecutionRequest.of(
                ExecutionRequest.Language.PYTHON,
                "x = 'a' * 10**10"));

        assertInstanceOf(ExecutionResult.SecurityViolation.class, result);
        var violation = (ExecutionResult.SecurityViolation) result;
        assertTrue(violation.reason().toLowerCase().contains("memória"));
    }

    @Test
    @DisplayName("Timeout mata o container e devolve Timeout")
    void timeoutKillsProcess() {
        fakeRunner.simulateTimeout = true;

        var result = executor.execute(new ExecutionRequest(
                ExecutionRequest.Language.PYTHON,
                "while True: pass", "", 5_000));

        assertInstanceOf(ExecutionResult.Timeout.class, result);
        assertEquals(5_000, ((ExecutionResult.Timeout) result).limitMillis());
    }

    // ---------- Construção do comando Docker (as flags de segurança) ----------

    @Test
    @DisplayName("Comando Docker inclui TODAS as flags de segurança obrigatórias")
    void dockerCommandHasAllSecurityFlags() {
        var request = ExecutionRequest.of(ExecutionRequest.Language.JAVA, "class Main {}");
        List<String> cmd = executor.buildDockerCommand(request, tempDir);

        assertTrue(cmd.contains("--network=none"), "rede tem de estar bloqueada");
        assertTrue(cmd.contains("--read-only"), "filesystem tem de ser imutável");
        assertTrue(cmd.contains("--cap-drop=ALL"), "capabilities têm de ser removidas");
        assertTrue(cmd.contains("no-new-privileges"), "escalada de privilégios bloqueada");
        assertTrue(cmd.contains("--rm"), "container tem de ser descartável");
        assertTrue(cmd.stream().anyMatch(f -> f.startsWith("--memory=")), "limite de memória");
        assertTrue(cmd.stream().anyMatch(f -> f.startsWith("--pids-limit=")), "proteção fork bomb");
        assertTrue(cmd.containsAll(List.of("--user", "1000:1000")), "nunca correr como root");
    }

    @Test
    @DisplayName("Cada linguagem usa a imagem Docker correta")
    void correctImagePerLanguage() {
        for (var lang : ExecutionRequest.Language.values()) {
            var cmd = executor.buildDockerCommand(
                    ExecutionRequest.of(lang, "x"), tempDir);
            assertTrue(cmd.contains(lang.dockerImage()),
                    "imagem errada para " + lang);
        }
    }

    // ---------- Validação de input ----------

    @Test
    @DisplayName("Código vazio é rejeitado na construção do request")
    void emptyCodeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ExecutionRequest.of(ExecutionRequest.Language.JAVA, "  "));
    }

    @Test
    @DisplayName("Timeout acima de 30s é rejeitado (proteção anti-abuso)")
    void excessiveTimeoutRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExecutionRequest(ExecutionRequest.Language.JAVA,
                        "class Main {}", "", 60_000));
    }

    @Test
    @DisplayName("Código acima de 64KB é rejeitado")
    void oversizedCodeRejected() {
        String huge = "x".repeat(65_000);
        assertThrows(IllegalArgumentException.class, () ->
                ExecutionRequest.of(ExecutionRequest.Language.JAVA, huge));
    }

    @Test
    @DisplayName("Output gigante é truncado para proteger o frontend")
    void hugeOutputIsTruncated() {
        fakeRunner.nextResult = new CommandRunner.CommandResult(
                0, "y".repeat(100_000), "");

        var result = executor.execute(ExecutionRequest.of(
                ExecutionRequest.Language.PYTHON, "print('y'*100000)"));

        var success = (ExecutionResult.Success) result;
        assertTrue(success.stdout().length() < 20_000);
        assertTrue(success.stdout().endsWith("[output truncado]"));
    }

    // ---------- Fake para os testes ----------

    static class FakeCommandRunner implements CommandRunner {
        CommandRunner.CommandResult nextResult =
                new CommandRunner.CommandResult(0, "", "");
        boolean simulateTimeout = false;
        List<String> lastCommand;

        @Override
        public CommandResult run(List<String> command, String stdin, long timeoutMillis)
                throws CommandTimeoutException {
            this.lastCommand = command;
            if (simulateTimeout) {
                throw new CommandTimeoutException("simulated timeout");
            }
            return nextResult;
        }
    }
}
