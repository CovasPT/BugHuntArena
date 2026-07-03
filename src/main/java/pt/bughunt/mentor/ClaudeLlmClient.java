package pt.bughunt.mentor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Implementação real do LlmClient para a Anthropic API,
 * usando APENAS java.net.http — zero dependências externas.
 *
 * A API key vem de variável de ambiente (ANTHROPIC_API_KEY),
 * nunca hardcoded — a mesma lição da injeção de credenciais
 * do reddit-tiktok-pipeline.
 *
 * Nota: o parse do JSON é feito à mão para evitar dependências
 * no core. Na camada Spring Boot usa Jackson normalmente.
 */
public class ClaudeLlmClient implements SocraticMentor.LlmClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 500; // dicas são curtas por design

    private final HttpClient http;
    private final String apiKey;

    public ClaudeLlmClient() {
        this(System.getenv("ANTHROPIC_API_KEY"));
    }

    public ClaudeLlmClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY não definida no ambiente");
        }
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String body = """
                {
                  "model": "%s",
                  "max_tokens": %d,
                  "system": %s,
                  "messages": [{"role": "user", "content": %s}]
                }
                """.formatted(MODEL, MAX_TOKENS,
                jsonString(systemPrompt), jsonString(userPrompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException("API devolveu HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            return extractText(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new LlmException("Falha na chamada ao LLM: " + e.getMessage(), e);
        }
    }

    /** Escapa uma string para JSON (suficiente para prompts de texto). */
    static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Extrai o campo content[0].text da resposta.
     * Parser minimalista: procura "text":" e lê até à aspa final,
     * respeitando escapes.
     */
    static String extractText(String json) {
        int idx = json.indexOf("\"text\":");
        if (idx < 0) throw new LlmException("Resposta sem campo text: " + json);
        int start = json.indexOf('"', idx + 7) + 1;
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
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(json, i + 1, i + 5, 16));
                        i += 4;
                    }
                    default -> sb.append(next);
                }
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        throw new LlmException("JSON malformado");
    }

    public static class LlmException extends RuntimeException {
        public LlmException(String message) { super(message); }
        public LlmException(String message, Throwable cause) { super(message, cause); }
    }
}
