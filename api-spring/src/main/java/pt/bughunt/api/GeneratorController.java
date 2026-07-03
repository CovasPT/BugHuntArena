package pt.bughunt.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.bughunt.generator.ChallengeGenerator;
import pt.bughunt.mentor.SocraticMentor;
import pt.bughunt.sandbox.ExecutionRequest;

import java.util.List;
import java.util.Map;

/**
 * Endpoints do gerador de desafios e do chat livre com o mentor.
 *
 *   POST /api/generator/{area}   → gera + valida um desafio novo
 *   POST /api/mentor/chat        → conversa livre (sempre socrática)
 */
@RestController
@RequestMapping("/api")
public class GeneratorController {

    private final ChallengeGenerator generator;
    private final SocraticMentor mentor;

    /** Mapeamento área → conceito/linguagem para geração. */
    private static final Map<String, GenTarget> AREA_TARGETS = Map.of(
            "logica",    new GenTarget("ciclos", ExecutionRequest.Language.JAVA),
            "backend",   new GenTarget("strings", ExecutionRequest.Language.JAVA),
            "seguranca", new GenTarget("seguranca-input", ExecutionRequest.Language.PYTHON),
            "web",       new GenTarget("closures", ExecutionRequest.Language.JAVASCRIPT));

    private record GenTarget(String conceptId, ExecutionRequest.Language language) {}

    public GeneratorController(ChallengeGenerator generator, SocraticMentor mentor) {
        this.generator = generator;
        this.mentor = mentor;
    }

    @PostMapping("/generator/{area}")
    public ResponseEntity<?> generate(@PathVariable String area,
                                      @RequestParam(defaultValue = "1") int difficulty) {
        GenTarget target = AREA_TARGETS.get(area);
        if (target == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "área desconhecida: " + area));
        }
        return generator.generateValidated(
                        target.conceptId(), target.language(), difficulty, 3)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(502).body(Map.of(
                        "error", "não foi possível gerar um desafio válido — tenta outra vez")));
    }

    // ---------- Chat livre com o mentor ----------

    public record ChatRequest(String challengeId, String message,
                              String code, int failedAttempts) {}

    @PostMapping("/mentor/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        // O chat livre usa SEMPRE o nível socrático — perguntas do aluno
        // recebem perguntas de volta. Dicas estruturadas vão pelo
        // endpoint /api/mentor/hint com a progressão de níveis.
        var context = new SocraticMentor.StudentContext(
                "iniciante", List.of(), request.failedAttempts());

        String reply = mentor.provideHint(
                "Pergunta livre do aluno durante o desafio " + request.challengeId()
                        + ": " + request.message(),
                request.code() == null ? "" : request.code(),
                "",
                context,
                SocraticMentor.HintLevel.SOCRATIC_QUESTION);

        return ResponseEntity.ok(Map.of("reply", reply));
    }
}
