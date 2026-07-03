package pt.bughunt.teacher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.bughunt.teacher.TeacherAnalytics.Severity;
import pt.bughunt.teacher.TeacherAnalytics.StudentConceptStats;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TeacherAnalyticsTest {

    TeacherAnalytics analytics = new TeacherAnalytics();

    /** Turma fictícia de 3 alunos: ciclos = problema coletivo, sql = ok */
    List<StudentConceptStats> turma() {
        return List.of(
                // ciclos: turma em dificuldade (erros altos nos 3)
                new StudentConceptStats("a1", "Ana",   "ciclos", 10, 8),   // 80%
                new StudentConceptStats("a2", "Bruno", "ciclos", 10, 7),   // 70%
                new StudentConceptStats("a3", "Carla", "ciclos", 10, 6),   // 60%
                // sql-select: turma confortável
                new StudentConceptStats("a1", "Ana",   "sql-select", 8, 1), // 12.5%
                new StudentConceptStats("a2", "Bruno", "sql-select", 8, 2), // 25%
                // strings: dificuldade moderada, só 2 alunos tentaram
                new StudentConceptStats("a1", "Ana",   "strings", 6, 2),   // 33%
                new StudentConceptStats("a3", "Carla", "strings", 6, 3)    // 50%
        );
    }

    // ---------- Heatmap ----------

    @Test
    @DisplayName("Heatmap classifica severidade: ciclos CRITICAL, strings ATTENTION, sql OK")
    void heatmapSeverityClassification() {
        var heatmap = analytics.classHeatmap(turma());

        var ciclos = heatmap.stream().filter(h -> h.conceptId().equals("ciclos")).findFirst().orElseThrow();
        var sql = heatmap.stream().filter(h -> h.conceptId().equals("sql-select")).findFirst().orElseThrow();
        var strings = heatmap.stream().filter(h -> h.conceptId().equals("strings")).findFirst().orElseThrow();

        assertEquals(Severity.CRITICAL, ciclos.severity());
        assertEquals(Severity.OK, sql.severity());
        assertEquals(Severity.ATTENTION, strings.severity());
    }

    @Test
    @DisplayName("Heatmap vem ordenado do pior para o melhor — o professor vê primeiro o problema")
    void heatmapSortedWorstFirst() {
        var heatmap = analytics.classHeatmap(turma());
        assertEquals("ciclos", heatmap.get(0).conceptId());
        assertEquals("sql-select", heatmap.get(heatmap.size() - 1).conceptId());
    }

    @Test
    @DisplayName("Heatmap agrega tentativas e conta alunos por conceito")
    void heatmapAggregation() {
        var heatmap = analytics.classHeatmap(turma());
        var ciclos = heatmap.stream().filter(h -> h.conceptId().equals("ciclos")).findFirst().orElseThrow();

        assertEquals(3, ciclos.studentsAttempted());
        assertEquals(30, ciclos.totalAttempts());
        assertEquals(0.70, ciclos.avgErrorRate(), 0.001);
    }

    @Test
    @DisplayName("Alunos sem tentativas não entram no heatmap")
    void zeroAttemptsExcluded() {
        var stats = List.of(new StudentConceptStats("a1", "Ana", "grafos", 0, 0));
        assertTrue(analytics.classHeatmap(stats).isEmpty());
    }

    // ---------- Alertas de alunos ----------

    @Test
    @DisplayName("Alerta só para taxa de erro >= 60% com >= 5 tentativas")
    void alertsRespectThresholds() {
        var alerts = analytics.studentsNeedingHelp(turma());

        // Ana (80%), Bruno (70%) e Carla (60%) em ciclos qualificam
        assertEquals(3, alerts.size());
        assertTrue(alerts.stream().allMatch(a -> a.conceptId().equals("ciclos")));
    }

    @Test
    @DisplayName("Alertas ordenados por gravidade — o pior aluno primeiro")
    void alertsSortedByErrorRate() {
        var alerts = analytics.studentsNeedingHelp(turma());
        assertEquals("Ana", alerts.get(0).studentName());
        assertEquals(0.80, alerts.get(0).errorRate(), 0.001);
    }

    @Test
    @DisplayName("Amostra pequena não gera alerta mesmo com 100% de erro")
    void smallSampleNoAlert() {
        var stats = List.of(
                new StudentConceptStats("a1", "Ana", "grafos", 2, 2)); // 100% mas só 2 tentativas
        assertTrue(analytics.studentsNeedingHelp(stats).isEmpty());
    }

    // ---------- Recomendação de aula ----------

    @Test
    @DisplayName("Recomenda rever o conceito CRITICAL com mais alunos afetados")
    void recommendsCriticalConcept() {
        var rec = analytics.recommendNextLesson(turma());
        assertTrue(rec.isPresent());
        assertEquals("ciclos", rec.get().conceptId());
    }

    @Test
    @DisplayName("Sem CRITICAL, recomenda o pior ATTENTION")
    void recommendsAttentionWhenNoCritical() {
        var stats = List.of(
                new StudentConceptStats("a1", "Ana",   "strings", 10, 4),  // 40%
                new StudentConceptStats("a2", "Bruno", "strings", 10, 3),  // 30%
                new StudentConceptStats("a1", "Ana",   "sql-select", 10, 1)); // 10%
        var rec = analytics.recommendNextLesson(stats);
        assertTrue(rec.isPresent());
        assertEquals("strings", rec.get().conceptId());
    }

    @Test
    @DisplayName("Turma a dominar tudo → sem recomendação (avançar matéria)")
    void noRecommendationWhenClassIsFine() {
        var stats = List.of(
                new StudentConceptStats("a1", "Ana",   "ciclos", 10, 1),
                new StudentConceptStats("a2", "Bruno", "ciclos", 10, 2));
        assertTrue(analytics.recommendNextLesson(stats).isEmpty());
    }
}
