package pt.bughunt.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorPatternTrackerTest {

    ErrorPatternTracker tracker;
    Instant now;

    @BeforeEach
    void setup() {
        tracker = new ErrorPatternTracker();
        now = Instant.parse("2026-07-03T10:00:00Z");
    }

    @Test
    @DisplayName("Acerto agenda revisão para 1 dia depois (primeiro intervalo)")
    void firstCorrectSchedulesOneDayReview() {
        tracker.recordAttempt("ciclos", true, now);
        assertEquals(now.plus(Duration.ofDays(1)), tracker.nextReview("ciclos"));
    }

    @Test
    @DisplayName("Acertos consecutivos aumentam o intervalo: 1 → 3 → 7 dias")
    void consecutiveCorrectGrowsInterval() {
        tracker.recordAttempt("ciclos", true, now);                          // 1º acerto → +1 dia
        tracker.recordAttempt("ciclos", true, now.plus(Duration.ofDays(1))); // 2º acerto → +3 dias
        tracker.recordAttempt("ciclos", true, now.plus(Duration.ofDays(4))); // 3º acerto → +7 dias

        assertEquals(now.plus(Duration.ofDays(4)).plus(Duration.ofDays(7)),
                tracker.nextReview("ciclos"));
        assertEquals(3, tracker.consecutiveCorrect("ciclos"));
    }

    @Test
    @DisplayName("Errar faz reset do intervalo para 1 dia — princípio central do spaced repetition")
    void errorResetsInterval() {
        tracker.recordAttempt("ciclos", true, now);
        tracker.recordAttempt("ciclos", true, now.plus(Duration.ofDays(1)));
        tracker.recordAttempt("ciclos", false, now.plus(Duration.ofDays(4))); // erro!

        assertEquals(0, tracker.consecutiveCorrect("ciclos"));
        assertEquals(now.plus(Duration.ofDays(4)).plus(Duration.ofDays(1)),
                tracker.nextReview("ciclos"));
    }

    @Test
    @DisplayName("dueForReview devolve só conceitos vencidos")
    void dueForReviewFiltersByDate() {
        tracker.recordAttempt("ciclos", true, now);   // revisão daqui a 1 dia
        tracker.recordAttempt("arrays", true, now);   // idem

        // Meio dia depois: nada vencido
        assertTrue(tracker.dueForReview(now.plus(Duration.ofHours(12))).isEmpty());

        // Dois dias depois: ambos vencidos
        List<String> due = tracker.dueForReview(now.plus(Duration.ofDays(2)));
        assertTrue(due.containsAll(List.of("ciclos", "arrays")));
    }

    @Test
    @DisplayName("Conceitos com mais erros aparecem primeiro na fila de revisão")
    void reviewQueueOrderedByErrorCount() {
        // "ponteiros": 3 erros
        tracker.recordAttempt("ponteiros", false, now);
        tracker.recordAttempt("ponteiros", false, now);
        tracker.recordAttempt("ponteiros", false, now);
        // "strings": 1 erro
        tracker.recordAttempt("strings", false, now);

        List<String> due = tracker.dueForReview(now.plus(Duration.ofDays(2)));
        assertEquals(List.of("ponteiros", "strings"), due);
    }

    @Test
    @DisplayName("errorRate calcula corretamente a taxa de erro")
    void errorRateCalculation() {
        tracker.recordAttempt("off-by-one", false, now);
        tracker.recordAttempt("off-by-one", false, now);
        tracker.recordAttempt("off-by-one", true, now);
        tracker.recordAttempt("off-by-one", false, now);

        assertEquals(0.75, tracker.errorRate("off-by-one"), 0.001);
        assertEquals(0.0, tracker.errorRate("conceito-nunca-visto"));
    }

    @Test
    @DisplayName("problematicConcepts exige mínimo de tentativas — evita falsos positivos")
    void problematicConceptsRequiresMinimumSample() {
        // 1 erro em 1 tentativa = 100% de erro, mas amostra minúscula
        tracker.recordAttempt("novo-conceito", false, now);

        // Com minAttempts=5, não deve aparecer
        assertTrue(tracker.problematicConcepts(0.5, 5).isEmpty());

        // Com minAttempts=1, aparece
        assertEquals(List.of("novo-conceito"),
                tracker.problematicConcepts(0.5, 1));
    }

    @Test
    @DisplayName("problematicConcepts ordena por taxa de erro descendente")
    void problematicConceptsSortedByRate() {
        // "recursividade": 4/5 erros = 80%
        for (int i = 0; i < 4; i++) tracker.recordAttempt("recursividade", false, now);
        tracker.recordAttempt("recursividade", true, now);
        // "ciclos": 3/5 erros = 60%
        for (int i = 0; i < 3; i++) tracker.recordAttempt("ciclos", false, now);
        for (int i = 0; i < 2; i++) tracker.recordAttempt("ciclos", true, now);

        assertEquals(List.of("recursividade", "ciclos"),
                tracker.problematicConcepts(0.5, 5));
    }
}
