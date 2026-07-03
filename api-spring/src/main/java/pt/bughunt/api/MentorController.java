package pt.bughunt.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.bughunt.challenge.SeedChallenges;
import pt.bughunt.mentor.SocraticMentor;

import java.util.List;
import java.util.Map;

/**
 * Endpoint do mentor socrático.
 *
 *   POST /api/mentor/hint
 *
 * O nível de dica pedido é validado pelo capLevel do SocraticMentor —
 * o frontend PODE pedir DIRECT_EXPLANATION, mas o backend recusa
 * se ainda não houver 3 tentativas falhadas. Nunca confiar no cliente.
 */
@RestController
@RequestMapping("/api/mentor")
public class MentorController {

    private final SocraticMentor mentor;

    public MentorController(SocraticMentor mentor) {
        this.mentor = mentor;
    }

    public record HintRequest(
            String challengeId,
            String code,
            String lastOutput,
            String hintLevel,          // SOCRATIC_QUESTION | LOCATION_HINT | ...
            String skillLevel,         // vem do perfil na BD na versão final
            List<String> problematicConcepts,
            int failedAttempts         // idem — na versão final vem da BD,
                                       // NUNCA do cliente (senão é contornável)
    ) {}

    @PostMapping("/hint")
    public ResponseEntity<?> hint(@RequestBody HintRequest request) {
        var challenge = SeedChallenges.byId(request.challengeId());
        if (challenge.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SocraticMentor.HintLevel level;
        try {
            level = SocraticMentor.HintLevel.valueOf(request.hintLevel());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "hintLevel inválido",
                    "valid", SocraticMentor.HintLevel.values()));
        }

        var context = new SocraticMentor.StudentContext(
                request.skillLevel() == null ? "iniciante" : request.skillLevel(),
                request.problematicConcepts() == null ? List.of() : request.problematicConcepts(),
                request.failedAttempts());

        String hint = mentor.provideHint(
                challenge.get().description(),
                request.code(),
                request.lastOutput() == null ? "" : request.lastOutput(),
                context,
                level);

        return ResponseEntity.ok(Map.of("hint", hint, "levelUsed", level.name()));
    }
}
