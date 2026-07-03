package pt.bughunt.teacher;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analytics pedagógicos para o professor — o argumento de venda
 * às escolas: informação que nenhum teste tradicional dá.
 *
 * Responde a três perguntas:
 *   1. Em que conceitos é que A TURMA está a ter dificuldade? (heatmap)
 *   2. Que ALUNOS precisam de atenção individual, e em quê?
 *   3. O que devo ensinar/rever na próxima aula? (recomendação)
 *
 * Recebe agregados anónimos por aluno×conceito (vêm da BD —
 * ver class_concept_heatmap em school-schema.sql). Lógica pura,
 * sem dependências, totalmente testável.
 */
public class TeacherAnalytics {

    /** Uma linha de estatísticas de um aluno num conceito. */
    public record StudentConceptStats(
            String studentId,
            String studentName,
            String conceptId,
            int attempts,
            int errors
    ) {
        public double errorRate() {
            return attempts == 0 ? 0.0 : (double) errors / attempts;
        }
    }

    /** Linha do heatmap da turma para um conceito. */
    public record ConceptHeat(
            String conceptId,
            int studentsAttempted,
            int totalAttempts,
            double avgErrorRate,
            Severity severity
    ) {}

    public enum Severity {
        OK,        // turma domina (< 30% erro médio)
        ATTENTION, // dificuldade moderada (30-60%)
        CRITICAL   // a turma está perdida (> 60%) — rever na aula
    }

    public record StudentAlert(
            String studentId,
            String studentName,
            String conceptId,
            double errorRate,
            int attempts
    ) {}

    private static final double ATTENTION_THRESHOLD = 0.30;
    private static final double CRITICAL_THRESHOLD = 0.60;
    private static final int MIN_ATTEMPTS_FOR_ALERT = 5;

    // ---------- 1. Heatmap da turma ----------

    public List<ConceptHeat> classHeatmap(List<StudentConceptStats> stats) {
        Map<String, List<StudentConceptStats>> byConcept = stats.stream()
                .filter(s -> s.attempts() > 0)
                .collect(Collectors.groupingBy(StudentConceptStats::conceptId));

        return byConcept.entrySet().stream()
                .map(e -> {
                    var rows = e.getValue();
                    double avgError = rows.stream()
                            .mapToDouble(StudentConceptStats::errorRate)
                            .average().orElse(0);
                    int totalAttempts = rows.stream()
                            .mapToInt(StudentConceptStats::attempts).sum();
                    return new ConceptHeat(e.getKey(), rows.size(),
                            totalAttempts, avgError, severityOf(avgError));
                })
                .sorted(Comparator.comparingDouble(ConceptHeat::avgErrorRate).reversed())
                .toList();
    }

    static Severity severityOf(double avgErrorRate) {
        if (avgErrorRate >= CRITICAL_THRESHOLD) return Severity.CRITICAL;
        if (avgErrorRate >= ATTENTION_THRESHOLD) return Severity.ATTENTION;
        return Severity.OK;
    }

    // ---------- 2. Alunos que precisam de atenção ----------

    /**
     * Alunos com taxa de erro alta num conceito, com amostra mínima
     * (mesmo princípio anti-falsos-positivos do ErrorPatternTracker).
     * Ordenados por gravidade — o professor vê primeiro quem está pior.
     */
    public List<StudentAlert> studentsNeedingHelp(List<StudentConceptStats> stats) {
        return stats.stream()
                .filter(s -> s.attempts() >= MIN_ATTEMPTS_FOR_ALERT)
                .filter(s -> s.errorRate() >= CRITICAL_THRESHOLD)
                .map(s -> new StudentAlert(s.studentId(), s.studentName(),
                        s.conceptId(), s.errorRate(), s.attempts()))
                .sorted(Comparator.comparingDouble(StudentAlert::errorRate).reversed())
                .toList();
    }

    // ---------- 3. Recomendação para a próxima aula ----------

    /**
     * O conceito que mais beneficia de revisão coletiva:
     * o CRITICAL com mais alunos afetados. Se não houver CRITICAL,
     * o pior ATTENTION. Se a turma domina tudo, devolve empty —
     * hora de desbloquear conceitos novos.
     */
    public java.util.Optional<ConceptHeat> recommendNextLesson(
            List<StudentConceptStats> stats) {
        var heatmap = classHeatmap(stats);
        return heatmap.stream()
                .filter(h -> h.severity() != Severity.OK)
                .max(Comparator
                        .comparing((ConceptHeat h) -> h.severity() == Severity.CRITICAL)
                        .thenComparingInt(ConceptHeat::studentsAttempted)
                        .thenComparingDouble(ConceptHeat::avgErrorRate));
    }
}
