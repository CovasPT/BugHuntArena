package pt.bughunt.gamification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Motor de gamificação — pontos, streaks diários e badges.
 *
 * Espelha as funções PL/pgSQL em db/functions.sql. Em produção
 * a fonte de verdade deve ser a BD (transacional); esta classe
 * serve o protótipo e os testes de regras.
 *
 * Filosofia de pontos anti-vibecoding:
 *  - dicas custam pontos (-20% cada) → pedir ajuda tem trade-off real
 *  - streak diário multiplica (+5%/dia até +50%) → recompensa hábito,
 *    não maratonas
 */
public class GamificationEngine {

    public static final double HINT_PENALTY = 0.20;
    public static final double MIN_POINTS_FRACTION = 0.20;
    public static final double STREAK_BONUS_PER_DAY = 0.05;
    public static final double MAX_STREAK_BONUS = 0.50;

    // ---------- Pontos ----------

    public int calculatePoints(int basePoints, int hintsUsed, int streakDays) {
        if (basePoints <= 0) throw new IllegalArgumentException("basePoints deve ser > 0");
        if (hintsUsed < 0 || streakDays < 0) throw new IllegalArgumentException("valores negativos");

        double streakMultiplier = 1 + Math.min(streakDays * STREAK_BONUS_PER_DAY, MAX_STREAK_BONUS);
        double hintPenalty = Math.max(1 - hintsUsed * HINT_PENALTY, MIN_POINTS_FRACTION);
        return Math.max((int) Math.round(basePoints * streakMultiplier * hintPenalty), 1);
    }

    // ---------- Streak diário ----------

    public record StreakState(int currentDays, int longestDays, LocalDate lastActivity) {

        public static StreakState fresh() {
            return new StreakState(0, 0, null);
        }

        /** Regista atividade num dia e devolve o novo estado. */
        public StreakState recordActivity(LocalDate today) {
            if (today.equals(lastActivity)) {
                return this;                                   // já contou hoje
            }
            int newCurrent = today.minusDays(1).equals(lastActivity)
                    ? currentDays + 1                          // dia seguido
                    : 1;                                       // quebrou (ou primeiro dia)
            return new StreakState(newCurrent,
                    Math.max(longestDays, newCurrent), today);
        }

        public boolean isBrokenAsOf(LocalDate today) {
            return lastActivity != null
                    && lastActivity.isBefore(today.minusDays(1));
        }
    }

    // ---------- Badges ----------

    public enum Badge {
        FIRST_BLOOD("Primeiro Bug", "Resolveste o teu primeiro desafio"),
        WEEK_STREAK("Semana em Chamas", "7 dias seguidos de atividade"),
        MONTH_STREAK("Imparável", "30 dias seguidos de atividade"),
        BUG_SLAYER_10("Caçador", "10 desafios resolvidos"),
        BUG_SLAYER_50("Exterminador", "50 desafios resolvidos"),
        PURIST("Purista", "10 desafios resolvidos sem usar dicas"),
        SECURITY_MINDED("Secure by Design", "Resolveste 5 desafios de segurança"),
        COMEBACK("Persistente", "Resolveste um desafio após 5+ tentativas falhadas");

        private final String title;
        private final String description;

        Badge(String title, String description) {
            this.title = title;
            this.description = description;
        }
        public String title() { return title; }
        public String description() { return description; }
    }

    public record PlayerStats(
            int totalSolved,
            int solvedWithoutHints,
            int securityChallengesSolved,
            int currentStreakDays,
            int maxFailedAttemptsBeforeSolve
    ) {}

    /** Avalia que badges NOVAS o jogador ganhou (ignora as já atribuídas). */
    public List<Badge> evaluateNewBadges(PlayerStats stats, Set<Badge> alreadyAwarded) {
        List<Badge> earned = new ArrayList<>();

        if (stats.totalSolved() >= 1)  earned.add(Badge.FIRST_BLOOD);
        if (stats.totalSolved() >= 10) earned.add(Badge.BUG_SLAYER_10);
        if (stats.totalSolved() >= 50) earned.add(Badge.BUG_SLAYER_50);
        if (stats.currentStreakDays() >= 7)  earned.add(Badge.WEEK_STREAK);
        if (stats.currentStreakDays() >= 30) earned.add(Badge.MONTH_STREAK);
        if (stats.solvedWithoutHints() >= 10) earned.add(Badge.PURIST);
        if (stats.securityChallengesSolved() >= 5) earned.add(Badge.SECURITY_MINDED);
        if (stats.maxFailedAttemptsBeforeSolve() >= 5) earned.add(Badge.COMEBACK);

        earned.removeAll(alreadyAwarded);
        return List.copyOf(earned);
    }

    // ---------- Níveis (XP → nível, curva quadrática suave) ----------

    /** XP necessário para atingir um nível: 100 * n^1.5 (arredondado). */
    public int xpRequiredForLevel(int level) {
        if (level <= 1) return 0;
        return (int) Math.round(100 * Math.pow(level - 1, 1.5));
    }

    public int levelForXp(int xp) {
        int level = 1;
        while (xpRequiredForLevel(level + 1) <= xp) level++;
        return level;
    }
}
