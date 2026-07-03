package pt.bughunt.gamification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GamificationEngineTest {

    GamificationEngine engine = new GamificationEngine();

    // ---------- Pontos ----------

    @Test
    @DisplayName("Sem dicas nem streak, pontos = base")
    void basePointsUnmodified() {
        assertEquals(100, engine.calculatePoints(100, 0, 0));
    }

    @Test
    @DisplayName("Cada dica custa 20% dos pontos")
    void hintsReducePoints() {
        assertEquals(80, engine.calculatePoints(100, 1, 0));
        assertEquals(60, engine.calculatePoints(100, 2, 0));
        assertEquals(40, engine.calculatePoints(100, 3, 0));
    }

    @Test
    @DisplayName("Penalização por dicas nunca desce abaixo de 20% dos pontos base")
    void hintPenaltyHasFloor() {
        // 10 dicas seria -200%, mas o mínimo é 20%
        assertEquals(20, engine.calculatePoints(100, 10, 0));
    }

    @Test
    @DisplayName("Streak dá +5% por dia até ao teto de +50%")
    void streakBonus() {
        assertEquals(105, engine.calculatePoints(100, 0, 1));
        assertEquals(150, engine.calculatePoints(100, 0, 10));
        assertEquals(150, engine.calculatePoints(100, 0, 100), "teto de +50%");
    }

    @Test
    @DisplayName("Pontos nunca são zero — mínimo é 1")
    void pointsNeverZero() {
        assertTrue(engine.calculatePoints(1, 10, 0) >= 1);
    }

    @Test
    @DisplayName("Valores inválidos são rejeitados")
    void invalidInputsRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine.calculatePoints(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> engine.calculatePoints(100, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> engine.calculatePoints(100, 0, -1));
    }

    // ---------- Streak ----------

    @Test
    @DisplayName("Dias consecutivos incrementam o streak")
    void consecutiveDaysGrowStreak() {
        var day1 = LocalDate.of(2026, 7, 1);
        var state = GamificationEngine.StreakState.fresh()
                .recordActivity(day1)
                .recordActivity(day1.plusDays(1))
                .recordActivity(day1.plusDays(2));

        assertEquals(3, state.currentDays());
        assertEquals(3, state.longestDays());
    }

    @Test
    @DisplayName("Atividade repetida no mesmo dia não conta duas vezes")
    void sameDayCountsOnce() {
        var day = LocalDate.of(2026, 7, 1);
        var state = GamificationEngine.StreakState.fresh()
                .recordActivity(day)
                .recordActivity(day)
                .recordActivity(day);

        assertEquals(1, state.currentDays());
    }

    @Test
    @DisplayName("Falhar um dia faz reset do streak mas preserva o recorde")
    void missedDayResetsButKeepsLongest() {
        var day1 = LocalDate.of(2026, 7, 1);
        var state = GamificationEngine.StreakState.fresh()
                .recordActivity(day1)
                .recordActivity(day1.plusDays(1))
                .recordActivity(day1.plusDays(2))   // streak 3
                .recordActivity(day1.plusDays(5));  // falhou dias 4 e 5 → reset

        assertEquals(1, state.currentDays());
        assertEquals(3, state.longestDays(), "o recorde mantém-se");
    }

    @Test
    @DisplayName("isBrokenAsOf deteta streaks quebrados")
    void brokenStreakDetection() {
        var day1 = LocalDate.of(2026, 7, 1);
        var state = GamificationEngine.StreakState.fresh().recordActivity(day1);

        assertFalse(state.isBrokenAsOf(day1.plusDays(1)), "ontem ainda conta");
        assertTrue(state.isBrokenAsOf(day1.plusDays(2)), "dois dias sem atividade = quebrado");
    }

    // ---------- Badges ----------

    @Test
    @DisplayName("Primeiro desafio resolvido ganha FIRST_BLOOD")
    void firstBloodBadge() {
        var stats = new GamificationEngine.PlayerStats(1, 0, 0, 1, 0);
        var badges = engine.evaluateNewBadges(stats, Set.of());
        assertTrue(badges.contains(GamificationEngine.Badge.FIRST_BLOOD));
    }

    @Test
    @DisplayName("Badges já atribuídas não são devolvidas outra vez")
    void alreadyAwardedExcluded() {
        var stats = new GamificationEngine.PlayerStats(15, 0, 0, 1, 0);
        var badges = engine.evaluateNewBadges(stats,
                Set.of(GamificationEngine.Badge.FIRST_BLOOD));

        assertFalse(badges.contains(GamificationEngine.Badge.FIRST_BLOOD));
        assertTrue(badges.contains(GamificationEngine.Badge.BUG_SLAYER_10));
    }

    @Test
    @DisplayName("Marcos múltiplos podem ser atingidos em simultâneo")
    void multipleBadgesAtOnce() {
        var stats = new GamificationEngine.PlayerStats(50, 12, 6, 30, 7);
        List<GamificationEngine.Badge> badges = engine.evaluateNewBadges(stats, Set.of());

        assertTrue(badges.containsAll(List.of(
                GamificationEngine.Badge.BUG_SLAYER_50,
                GamificationEngine.Badge.MONTH_STREAK,
                GamificationEngine.Badge.PURIST,
                GamificationEngine.Badge.SECURITY_MINDED,
                GamificationEngine.Badge.COMEBACK)));
    }

    // ---------- Níveis ----------

    @Test
    @DisplayName("Nível 1 começa em 0 XP")
    void levelOneAtZeroXp() {
        assertEquals(1, engine.levelForXp(0));
        assertEquals(0, engine.xpRequiredForLevel(1));
    }

    @Test
    @DisplayName("A curva de níveis é monótona crescente")
    void levelCurveIsMonotonic() {
        for (int level = 2; level <= 20; level++) {
            assertTrue(engine.xpRequiredForLevel(level) > engine.xpRequiredForLevel(level - 1),
                    "nível " + level + " deve custar mais que " + (level - 1));
        }
    }

    @Test
    @DisplayName("levelForXp é consistente com xpRequiredForLevel")
    void levelAndXpAreConsistent() {
        for (int level = 1; level <= 10; level++) {
            int xp = engine.xpRequiredForLevel(level);
            assertEquals(level, engine.levelForXp(xp),
                    "com exatamente o XP do nível " + level);
        }
    }
}
