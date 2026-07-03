package pt.bughunt.sandbox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Abstração sobre a execução de processos do sistema.
 * Existe por uma razão: permite testar o SandboxExecutor
 * sem Docker instalado, injetando uma implementação fake.
 * (Dependency Inversion — o mesmo princípio que usaste
 * na arquitetura hexagonal do reddit-tiktok-pipeline.)
 */
public interface CommandRunner {

    CommandResult run(List<String> command, String stdin, long timeoutMillis)
            throws IOException, CommandTimeoutException;

    record CommandResult(int exitCode, String stdout, String stderr) {}

    class CommandTimeoutException extends Exception {
        public CommandTimeoutException(String message) { super(message); }
    }

    /** Implementação real usando ProcessBuilder. */
    class SystemCommandRunner implements CommandRunner {
        @Override
        public CommandResult run(List<String> command, String stdin, long timeoutMillis)
                throws IOException, CommandTimeoutException {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            if (stdin != null && !stdin.isEmpty()) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();

            try {
                boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new CommandTimeoutException(
                            "Processo excedeu " + timeoutMillis + "ms");
                }
                String stdout = new String(process.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                String stderr = new String(process.getErrorStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                return new CommandResult(process.exitValue(), stdout, stderr);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException("Execução interrompida", e);
            }
        }
    }
}
