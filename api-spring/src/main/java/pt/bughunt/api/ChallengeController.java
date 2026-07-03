package pt.bughunt.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.bughunt.challenge.Challenge;
import pt.bughunt.challenge.SeedChallenges;
import pt.bughunt.sandbox.ExecutionRequest;
import pt.bughunt.sandbox.ExecutionResult;
import pt.bughunt.sandbox.SandboxExecutor;

import java.util.List;
import java.util.Map;

/**
 * API REST do BugHunt Arena.
 *
 * Endpoints:
 *   GET  /api/challenges                → lista de desafios (sem a solução!)
 *   GET  /api/challenges/{id}           → detalhe de um desafio
 *   POST /api/challenges/{id}/submit    → submete código, corre no sandbox
 *
 * NOTA DE SEGURANÇA: o expectedOutput NUNCA é exposto na API —
 * senão o utilizador podia fazer print direto do output esperado
 * sem corrigir o bug. O DTO ChallengeView omite esse campo.
 */
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final SandboxExecutor executor;

    public ChallengeController(SandboxExecutor executor) {
        this.executor = executor;
    }

    // ---------- DTOs (o que sai para o exterior) ----------

    public record ChallengeView(String id, String conceptId, String title,
                                String description, String language,
                                String buggyCode, int difficulty, int basePoints) {
        static ChallengeView from(Challenge c) {
            return new ChallengeView(c.id(), c.conceptId(), c.title(),
                    c.description(), c.language().name(),
                    c.buggyCode(), c.difficulty(), c.basePoints());
        }
    }

    public record SubmissionRequest(String code) {}

    public record SubmissionResponse(
            boolean solved,
            String resultType,     // SUCCESS, COMPILE_ERROR, WRONG_OUTPUT, ...
            String stdout,
            String stderr,
            long durationMillis
    ) {}

    // ---------- Endpoints ----------

    @GetMapping
    public List<ChallengeView> listChallenges(
            @RequestParam(defaultValue = "5") int maxDifficulty) {
        return SeedChallenges.byMaxDifficulty(maxDifficulty).stream()
                .map(ChallengeView::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChallengeView> getChallenge(@PathVariable String id) {
        return SeedChallenges.byId(id)
                .map(c -> ResponseEntity.ok(ChallengeView.from(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(@PathVariable String id,
                                    @RequestBody SubmissionRequest request) {
        var challengeOpt = SeedChallenges.byId(id);
        if (challengeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Challenge challenge = challengeOpt.get();

        ExecutionRequest execRequest;
        try {
            execRequest = ExecutionRequest.of(challenge.language(), request.code());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        ExecutionResult result = executor.execute(execRequest);

        // Pattern matching exaustivo sobre a sealed interface —
        // se um novo tipo de resultado for adicionado, isto não compila
        // até ser tratado. Type safety a trabalhar a nosso favor.
        SubmissionResponse response = switch (result) {
            case ExecutionResult.Success s -> {
                boolean solved = challenge.isSolvedBy(s.stdout());
                yield new SubmissionResponse(solved,
                        solved ? "SUCCESS" : "WRONG_OUTPUT",
                        s.stdout(), s.stderr(), s.durationMillis());
            }
            case ExecutionResult.CompileError e ->
                    new SubmissionResponse(false, "COMPILE_ERROR", "", e.compilerOutput(), 0);
            case ExecutionResult.RuntimeError e ->
                    new SubmissionResponse(false, "RUNTIME_ERROR", e.stdout(), e.stderr(), 0);
            case ExecutionResult.Timeout t ->
                    new SubmissionResponse(false, "TIMEOUT", "",
                            "Excedeu o limite de " + t.limitMillis() + "ms", 0);
            case ExecutionResult.SecurityViolation v ->
                    new SubmissionResponse(false, "SECURITY_VIOLATION", "", v.reason(), 0);
            case ExecutionResult.InfrastructureError e ->
                    new SubmissionResponse(false, "INFRASTRUCTURE_ERROR", "",
                            "Erro interno — tenta outra vez", 0);
        };

        return ResponseEntity.ok(response);
    }
}
