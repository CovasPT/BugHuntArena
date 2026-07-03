package pt.bughunt.sandbox;

/**
 * Resultado da execução de código no sandbox.
 * Sealed interface — o compilador obriga a tratar todos os casos
 * (pattern matching exaustivo no switch), o que evita esquecer
 * cenários de erro no resto da aplicação.
 */
public sealed interface ExecutionResult {

    /** Código compilou e correu com sucesso dentro dos limites. */
    record Success(String stdout, String stderr, long durationMillis) implements ExecutionResult {}

    /** Erro de compilação — devolvemos a mensagem do compilador. */
    record CompileError(String compilerOutput) implements ExecutionResult {}

    /** O código correu mas terminou com exit code != 0 ou lançou exceção. */
    record RuntimeError(String stdout, String stderr, int exitCode) implements ExecutionResult {}

    /** Excedeu o tempo máximo permitido — o container foi morto. */
    record Timeout(long limitMillis) implements ExecutionResult {}

    /**
     * O código tentou algo proibido: aceder à rede, escrever fora
     * do diretório permitido, fork bomb (limite de PIDs), etc.
     */
    record SecurityViolation(String reason) implements ExecutionResult {}

    /** Falha da infraestrutura (Docker indisponível, etc.) — não é culpa do utilizador. */
    record InfrastructureError(String message) implements ExecutionResult {}
}
