package pt.bughunt.challenge;

import pt.bughunt.sandbox.ExecutionRequest.Language;

/**
 * Um desafio de debugging: código com bug plantado + output esperado.
 * A validação é comportamental — o código submetido está correto se,
 * ao correr no sandbox, produzir exatamente o expectedOutput.
 */
public record Challenge(
        String id,
        String conceptId,
        String title,
        String description,
        Language language,
        String buggyCode,
        String expectedOutput,
        int difficulty,       // 1-5
        int basePoints
) {
    public Challenge {
        if (difficulty < 1 || difficulty > 5)
            throw new IllegalArgumentException("difficulty entre 1 e 5");
        if (basePoints <= 0)
            throw new IllegalArgumentException("basePoints > 0");
        if (buggyCode == null || buggyCode.isBlank())
            throw new IllegalArgumentException("buggyCode obrigatório");
        if (expectedOutput == null)
            throw new IllegalArgumentException("expectedOutput obrigatório");
    }

    /** Compara o output real com o esperado (normaliza fins de linha). */
    public boolean isSolvedBy(String actualStdout) {
        return normalize(actualStdout).equals(normalize(expectedOutput));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").strip();
    }
}
