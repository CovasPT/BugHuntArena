package pt.bughunt.profile;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rastreia padrões de erro por utilizador e decide quando um conceito
 * deve ser revisto — a base do sistema de spaced repetition.
 *
 * Modelo simplificado inspirado no SM-2 (algoritmo do Anki):
 *  - Cada conceito tem um "intervalo de revisão" que cresce com acertos
 *    consecutivos (1 dia → 3 → 7 → 14 → 30) e faz reset quando erra.
 *  - Conceitos com mais erros acumulados têm prioridade na fila de revisão.
 *
 * Isto é lógica de negócio pura, sem IA — a IA (mentor) só entra
 * para EXPLICAR; decidir O QUE rever é responsabilidade deste motor.
 */
public class ErrorPatternTracker {

    private static final List<Duration> INTERVALS = List.of(
            Duration.ofDays(1), Duration.ofDays(3), Duration.ofDays(7),
            Duration.ofDays(14), Duration.ofDays(30)
    );

    private final Map<String, ConceptStats> stats = new HashMap<>();

    /** Regista o resultado de um exercício sobre um conceito. */
    public void recordAttempt(String conceptId, boolean correct, Instant when) {
        ConceptStats s = stats.computeIfAbsent(conceptId, k -> new ConceptStats());
        s.totalAttempts++;
        if (correct) {
            s.consecutiveCorrect++;
            // usa o intervalo do nível ATUAL e só depois sobe de nível
            // (1º acerto → 1 dia, 2º → 3 dias, 3º → 7 dias, ...)
            s.nextReview = when.plus(INTERVALS.get(s.streakLevel));
            s.streakLevel = Math.min(s.streakLevel + 1, INTERVALS.size() - 1);
        } else {
            s.totalErrors++;
            s.consecutiveCorrect = 0;
            s.streakLevel = 0; // errar faz reset do intervalo — revê já amanhã
            s.nextReview = when.plus(INTERVALS.get(0));
        }
        s.lastAttempt = when;
    }

    /** Conceitos cuja revisão já venceu, ordenados por urgência (mais erros primeiro). */
    public List<String> dueForReview(Instant now) {
        return stats.entrySet().stream()
                .filter(e -> e.getValue().nextReview != null
                        && !e.getValue().nextReview.isAfter(now))
                .sorted(Comparator.comparingInt(
                        (Map.Entry<String, ConceptStats> e) -> e.getValue().totalErrors)
                        .reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Taxa de erro de um conceito — usada para detetar padrões sistemáticos.
     * Ex: se "off-by-one" tem 70% de erro em 10+ tentativas, o mentor
     * recebe essa informação no contexto e adapta a explicação.
     */
    public double errorRate(String conceptId) {
        ConceptStats s = stats.get(conceptId);
        if (s == null || s.totalAttempts == 0) return 0.0;
        return (double) s.totalErrors / s.totalAttempts;
    }

    /**
     * Conceitos problemáticos: taxa de erro acima do limiar
     * com um mínimo de tentativas (para evitar falsos positivos
     * com amostras pequenas).
     */
    public List<String> problematicConcepts(double errorThreshold, int minAttempts) {
        return stats.entrySet().stream()
                .filter(e -> e.getValue().totalAttempts >= minAttempts)
                .filter(e -> errorRate(e.getKey()) >= errorThreshold)
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<String, ConceptStats> e) -> errorRate(e.getKey()))
                        .reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    public int consecutiveCorrect(String conceptId) {
        ConceptStats s = stats.get(conceptId);
        return s == null ? 0 : s.consecutiveCorrect;
    }

    public Instant nextReview(String conceptId) {
        ConceptStats s = stats.get(conceptId);
        return s == null ? null : s.nextReview;
    }

    private static class ConceptStats {
        int totalAttempts;
        int totalErrors;
        int consecutiveCorrect;
        int streakLevel;          // índice em INTERVALS
        Instant lastAttempt;
        Instant nextReview;
    }
}
