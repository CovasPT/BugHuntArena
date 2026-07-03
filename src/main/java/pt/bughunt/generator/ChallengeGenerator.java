package pt.bughunt.generator;

import pt.bughunt.challenge.Challenge;
import pt.bughunt.sandbox.ExecutionRequest;
import pt.bughunt.sandbox.ExecutionResult;
import pt.bughunt.sandbox.SandboxExecutor;

import java.util.Optional;

/**
 * Gerador automático de desafios com validação no sandbox —
 * a peça que resolve o problema de escalar conteúdo para
 * "todas as áreas" sem escrever centenas de desafios à mão.
 *
 * Pipeline:
 *   1. O LLM gera uma variação de desafio (código buggy + fix canónico
 *      + output esperado) a partir de um conceito e dificuldade.
 *   2. O sandbox VALIDA a variação com duas execuções:
 *        a) o código buggy NÃO pode produzir o output esperado
 *           (senão o "bug" não existe)
 *        b) o fix canónico TEM de produzir exatamente o output esperado
 *           (senão a solução de referência está errada)
 *   3. Só variações que passam ambas as verificações entram no catálogo.
 *
 * Resultado: a IA gera conteúdo, mas a MÁQUINA verifica-o —
 * zero desafios quebrados chegam aos alunos, mesmo que o LLM alucine.
 */
public class ChallengeGenerator {

    /**
     * Fonte de variações — na prática um LLM; nos testes um fake.
     * Separar a geração (não determinística) da validação
     * (determinística) é o que torna isto testável.
     */
    public interface ChallengeSource {
        GeneratedChallenge generate(String conceptId,
                                    ExecutionRequest.Language language,
                                    int difficulty);
    }

    /** O que o LLM devolve: desafio + a solução de referência. */
    public record GeneratedChallenge(
            String title,
            String description,
            String buggyCode,
            String fixedCode,       // fix canónico — só para validação, nunca exposto
            String expectedOutput
    ) {}

    public sealed interface ValidationOutcome {
        record Valid(Challenge challenge) implements ValidationOutcome {}
        record BugDoesNotManifest(String actualOutput) implements ValidationOutcome {}
        record FixDoesNotWork(String actualOutput) implements ValidationOutcome {}
        record ExecutionFailed(String reason) implements ValidationOutcome {}
    }

    private final ChallengeSource source;
    private final SandboxExecutor sandbox;

    public ChallengeGenerator(ChallengeSource source, SandboxExecutor sandbox) {
        this.source = source;
        this.sandbox = sandbox;
    }

    /**
     * Gera e valida uma variação. Tenta até maxAttempts vezes —
     * LLMs falham às vezes; o retry com validação automática
     * transforma uma fonte não fiável num pipeline fiável.
     */
    public Optional<Challenge> generateValidated(String conceptId,
                                                 ExecutionRequest.Language language,
                                                 int difficulty,
                                                 int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            GeneratedChallenge candidate = source.generate(conceptId, language, difficulty);
            ValidationOutcome outcome = validate(candidate, conceptId, language, difficulty);
            if (outcome instanceof ValidationOutcome.Valid v) {
                return Optional.of(v.challenge());
            }
            // outcome != Valid → nova tentativa (logar o motivo em produção)
        }
        return Optional.empty();
    }

    public ValidationOutcome validate(GeneratedChallenge candidate,
                                      String conceptId,
                                      ExecutionRequest.Language language,
                                      int difficulty) {
        // Verificação (a): o bug tem de manifestar-se
        ExecutionResult buggyRun = run(candidate.buggyCode(), language);
        if (buggyRun instanceof ExecutionResult.InfrastructureError e) {
            return new ValidationOutcome.ExecutionFailed(e.message());
        }
        if (buggyRun instanceof ExecutionResult.Success s
                && matches(s.stdout(), candidate.expectedOutput())) {
            return new ValidationOutcome.BugDoesNotManifest(s.stdout());
        }

        // Verificação (b): o fix canónico tem de funcionar
        ExecutionResult fixedRun = run(candidate.fixedCode(), language);
        if (!(fixedRun instanceof ExecutionResult.Success s)
                || !matches(s.stdout(), candidate.expectedOutput())) {
            String actual = fixedRun instanceof ExecutionResult.Success ok
                    ? ok.stdout() : fixedRun.getClass().getSimpleName();
            return new ValidationOutcome.FixDoesNotWork(actual);
        }

        String id = conceptId + "-gen-" + Math.abs(candidate.buggyCode().hashCode());
        return new ValidationOutcome.Valid(new Challenge(
                id, conceptId, candidate.title(), candidate.description(),
                language, candidate.buggyCode(), candidate.expectedOutput(),
                difficulty, basePointsFor(difficulty)));
    }

    private ExecutionResult run(String code, ExecutionRequest.Language language) {
        return sandbox.execute(ExecutionRequest.of(language, code));
    }

    private boolean matches(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    private String normalize(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").strip();
    }

    static int basePointsFor(int difficulty) {
        return switch (difficulty) {
            case 1 -> 100;
            case 2 -> 150;
            case 3 -> 250;
            case 4 -> 400;
            default -> 600;
        };
    }
}
