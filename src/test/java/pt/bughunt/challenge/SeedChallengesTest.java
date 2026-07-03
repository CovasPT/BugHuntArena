package pt.bughunt.challenge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.bughunt.sandbox.ExecutionRequest.Language;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SeedChallengesTest {

    // ---------- Integridade do catálogo ----------

    @Test
    @DisplayName("Todos os IDs de desafios são únicos")
    void uniqueIds() {
        Set<String> ids = new HashSet<>();
        for (var c : SeedChallenges.ALL) {
            assertTrue(ids.add(c.id()), "ID duplicado: " + c.id());
        }
    }

    @Test
    @DisplayName("O catálogo cobre as três linguagens do sandbox")
    void coversAllLanguages() {
        var languages = SeedChallenges.ALL.stream()
                .map(Challenge::language)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(Language.JAVA, Language.PYTHON, Language.JAVASCRIPT),
                languages);
    }

    @Test
    @DisplayName("Há progressão de dificuldade: existem desafios fáceis E difíceis")
    void difficultyProgression() {
        assertTrue(SeedChallenges.ALL.stream().anyMatch(c -> c.difficulty() == 1),
                "falta nível de entrada");
        assertTrue(SeedChallenges.ALL.stream().anyMatch(c -> c.difficulty() >= 3),
                "falta nível avançado");
    }

    @Test
    @DisplayName("Desafios mais difíceis valem mais pontos")
    void harderChallengesWorthMore() {
        var easy = SeedChallenges.ALL.stream()
                .filter(c -> c.difficulty() == 1)
                .mapToInt(Challenge::basePoints).max().orElseThrow();
        var hard = SeedChallenges.ALL.stream()
                .filter(c -> c.difficulty() >= 4)
                .mapToInt(Challenge::basePoints).min().orElseThrow();
        assertTrue(hard > easy);
    }

    @Test
    @DisplayName("Existem desafios de segurança (o foco do enunciado)")
    void securityChallengesExist() {
        long securityCount = SeedChallenges.ALL.stream()
                .filter(c -> c.conceptId().startsWith("seguranca"))
                .count();
        assertTrue(securityCount >= 2);
    }

    // ---------- Lookups ----------

    @Test
    @DisplayName("byId encontra desafios existentes e devolve empty para inexistentes")
    void byIdLookup() {
        assertTrue(SeedChallenges.byId("off-by-one-01").isPresent());
        assertTrue(SeedChallenges.byId("nao-existe").isEmpty());
    }

    @Test
    @DisplayName("byMaxDifficulty filtra corretamente")
    void byMaxDifficultyFilters() {
        var beginner = SeedChallenges.byMaxDifficulty(1);
        assertFalse(beginner.isEmpty());
        assertTrue(beginner.stream().allMatch(c -> c.difficulty() <= 1));
    }

    // ---------- Validação de output (isSolvedBy) ----------

    @Test
    @DisplayName("isSolvedBy aceita o output exato")
    void exactOutputAccepted() {
        var challenge = SeedChallenges.byId("off-by-one-01").orElseThrow();
        assertTrue(challenge.isSolvedBy("Soma: 150"));
    }

    @Test
    @DisplayName("isSolvedBy normaliza \\r\\n e whitespace nas pontas")
    void outputNormalization() {
        var challenge = SeedChallenges.byId("off-by-one-01").orElseThrow();
        assertTrue(challenge.isSolvedBy("Soma: 150\n"));
        assertTrue(challenge.isSolvedBy("  Soma: 150  \r\n"));
    }

    @Test
    @DisplayName("isSolvedBy rejeita output errado — incluindo o output do bug")
    void wrongOutputRejected() {
        var challenge = SeedChallenges.byId("off-by-one-01").orElseThrow();
        assertFalse(challenge.isSolvedBy("Soma: 100"));
        assertFalse(challenge.isSolvedBy(""));
        assertFalse(challenge.isSolvedBy(null));
    }

    @Test
    @DisplayName("Output multilinhas é comparado corretamente")
    void multilineOutputComparison() {
        var challenge = SeedChallenges.byId("closure-loop-01").orElseThrow();
        assertTrue(challenge.isSolvedBy("0\n1\n2"));
        assertTrue(challenge.isSolvedBy("0\r\n1\r\n2\r\n"));
        assertFalse(challenge.isSolvedBy("3\n3\n3"), "o output do bug tem de falhar");
    }

    // ---------- Validação do record ----------

    @Test
    @DisplayName("Challenge rejeita dificuldade fora de 1-5")
    void invalidDifficultyRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new Challenge("x", "c", "t", "d", Language.JAVA, "code", "out", 0, 100));
        assertThrows(IllegalArgumentException.class, () ->
                new Challenge("x", "c", "t", "d", Language.JAVA, "code", "out", 6, 100));
    }

    @Test
    @DisplayName("Challenge rejeita código vazio e pontos inválidos")
    void invalidFieldsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new Challenge("x", "c", "t", "d", Language.JAVA, " ", "out", 1, 100));
        assertThrows(IllegalArgumentException.class, () ->
                new Challenge("x", "c", "t", "d", Language.JAVA, "code", "out", 1, 0));
    }
}
