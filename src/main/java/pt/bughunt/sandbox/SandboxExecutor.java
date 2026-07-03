package pt.bughunt.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Executa código de utilizadores dentro de containers Docker descartáveis.
 *
 * Princípios de segurança aplicados (defense in depth):
 *  1. --network=none        → sem acesso à rede (impede exfiltração/download)
 *  2. --memory / --cpus     → limites de recursos (impede DoS à máquina host)
 *  3. --pids-limit          → impede fork bombs
 *  4. --read-only + tmpfs   → filesystem imutável exceto /tmp e /workspace
 *  5. --user 1000:1000      → nunca corre como root dentro do container
 *  6. --cap-drop=ALL        → remove todas as capabilities do kernel
 *  7. --security-opt no-new-privileges → impede escalada de privilégios
 *  8. timeout externo       → o processo é morto se exceder o limite
 *
 * O comando Docker é construído mas executado através de CommandRunner,
 * uma abstração que permite testar toda esta classe sem Docker instalado
 * (nos testes injeta-se um FakeCommandRunner).
 */
public class SandboxExecutor {

    private final CommandRunner runner;
    private final Path workspaceRoot;

    public SandboxExecutor(CommandRunner runner, Path workspaceRoot) {
        this.runner = runner;
        this.workspaceRoot = workspaceRoot;
    }

    public ExecutionResult execute(ExecutionRequest request) {
        Path workDir = null;
        try {
            // 1. Diretório temporário único por submissão
            workDir = Files.createTempDirectory(workspaceRoot, "submission-");
            Path sourceFile = workDir.resolve(request.language().fileName());
            Files.writeString(sourceFile, request.sourceCode());

            // 2. Construir o comando docker run com todas as proteções
            List<String> command = buildDockerCommand(request, workDir);

            // 3. Executar com timeout
            long start = System.currentTimeMillis();
            CommandRunner.CommandResult result =
                    runner.run(command, request.stdin(), request.timeoutMillis());
            long duration = System.currentTimeMillis() - start;

            // 4. Interpretar o resultado
            return interpret(result, duration, request);

        } catch (CommandRunner.CommandTimeoutException e) {
            return new ExecutionResult.Timeout(request.timeoutMillis());
        } catch (IOException e) {
            return new ExecutionResult.InfrastructureError(
                    "Falha ao preparar o ambiente: " + e.getMessage());
        } finally {
            cleanup(workDir);
        }
    }

    List<String> buildDockerCommand(ExecutionRequest request, Path workDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");                                    // container descartável
        cmd.add("--network=none");                          // sem rede
        cmd.add("--memory=" + ExecutionRequest.RESOURCE_LIMITS.get("memory"));
        cmd.add("--cpus=" + ExecutionRequest.RESOURCE_LIMITS.get("cpus"));
        cmd.add("--pids-limit=" + ExecutionRequest.RESOURCE_LIMITS.get("pids-limit"));
        cmd.add("--read-only");                             // filesystem imutável
        cmd.add("--tmpfs");
        cmd.add("/tmp:size=32m,noexec");                    // /tmp limitado, sem execução
        cmd.add("--user");
        cmd.add("1000:1000");                               // nunca root
        cmd.add("--cap-drop=ALL");                          // sem capabilities
        cmd.add("--security-opt");
        cmd.add("no-new-privileges");
        cmd.add("-v");
        cmd.add(workDir.toAbsolutePath() + ":/workspace");  // só o código submetido
        cmd.add("-w");
        cmd.add("/workspace");
        cmd.add(request.language().dockerImage());
        cmd.add("sh");
        cmd.add("-c");
        cmd.add(request.language().runCommand());
        return cmd;
    }

    private ExecutionResult interpret(CommandRunner.CommandResult result,
                                      long duration,
                                      ExecutionRequest request) {
        // Erros de compilação: exit != 0 e o stderr menciona o compilador
        if (result.exitCode() != 0 && looksLikeCompileError(result.stderr(), request)) {
            return new ExecutionResult.CompileError(truncate(result.stderr()));
        }

        // Padrões de violação de segurança detetáveis no output
        if (containsSecuritySignal(result.stderr())) {
            return new ExecutionResult.SecurityViolation(
                    "Tentativa de operação não permitida detetada");
        }

        if (result.exitCode() == 137) { // SIGKILL — normalmente OOM killer
            return new ExecutionResult.SecurityViolation(
                    "Limite de memória excedido (256MB)");
        }

        if (result.exitCode() != 0) {
            return new ExecutionResult.RuntimeError(
                    truncate(result.stdout()), truncate(result.stderr()), result.exitCode());
        }

        return new ExecutionResult.Success(
                truncate(result.stdout()), truncate(result.stderr()), duration);
    }

    private boolean looksLikeCompileError(String stderr, ExecutionRequest request) {
        return switch (request.language()) {
            case JAVA -> stderr.contains("error:") && stderr.contains(".java");
            case PYTHON -> stderr.contains("SyntaxError");
            case JAVASCRIPT -> stderr.contains("SyntaxError");
        };
    }

    private boolean containsSecuritySignal(String stderr) {
        return stderr.contains("Operation not permitted")
                || stderr.contains("Permission denied")
                || stderr.contains("Network is unreachable")
                || stderr.contains("Temporary failure in name resolution");
    }

    /** Limita o output devolvido ao utilizador para evitar floods de MBs. */
    private String truncate(String text) {
        final int MAX = 16_000;
        if (text == null) return "";
        return text.length() <= MAX ? text : text.substring(0, MAX) + "\n... [output truncado]";
    }

    private void cleanup(Path workDir) {
        if (workDir == null) return;
        try (var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
