package pt.bughunt.sandbox;

import java.util.Map;

/**
 * Pedido de execução de código submetido por um utilizador.
 *
 * @param language      linguagem do código (define a imagem Docker usada)
 * @param sourceCode    código-fonte submetido
 * @param stdin         input a passar ao programa (pode ser vazio)
 * @param timeoutMillis tempo máximo de execução
 */
public record ExecutionRequest(
        Language language,
        String sourceCode,
        String stdin,
        long timeoutMillis
) {

    public ExecutionRequest {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("sourceCode não pode ser vazio");
        }
        if (timeoutMillis <= 0 || timeoutMillis > 30_000) {
            throw new IllegalArgumentException("timeout tem de estar entre 1ms e 30s");
        }
        if (sourceCode.length() > 64_000) {
            throw new IllegalArgumentException("código demasiado longo (máx 64KB)");
        }
    }

    /**
     * Linguagens suportadas. Cada uma mapeia para uma imagem Docker
     * mínima e o comando de compilação/execução dentro do container.
     */
    public enum Language {
        JAVA("bughunt/java21:latest", "Main.java",
                "javac Main.java && java -Xmx128m Main"),
        PYTHON("bughunt/python312:latest", "main.py",
                "python3 main.py"),
        JAVASCRIPT("bughunt/node20:latest", "main.js",
                "node main.js");

        private final String dockerImage;
        private final String fileName;
        private final String runCommand;

        Language(String dockerImage, String fileName, String runCommand) {
            this.dockerImage = dockerImage;
            this.fileName = fileName;
            this.runCommand = runCommand;
        }

        public String dockerImage() { return dockerImage; }
        public String fileName() { return fileName; }
        public String runCommand() { return runCommand; }
    }

    /** Configurações default seguras para exercícios normais. */
    public static ExecutionRequest of(Language lang, String code) {
        return new ExecutionRequest(lang, code, "", 10_000);
    }

    public static final Map<String, String> RESOURCE_LIMITS = Map.of(
            "memory", "256m",
            "cpus", "0.5",
            "pids-limit", "64"
    );
}
