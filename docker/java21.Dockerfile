# Imagem mínima para executar submissões Java no sandbox.
# Alpine + JDK headless = ~200MB em vez de ~600MB.
FROM eclipse-temurin:21-jdk-alpine

# Utilizador não-root com UID 1000 (corresponde ao --user 1000:1000
# passado pelo SandboxExecutor)
RUN addgroup -g 1000 sandbox && adduser -u 1000 -G sandbox -D -H sandbox

# Sem shell interativa desnecessária, sem package manager acessível
RUN rm -f /sbin/apk /usr/bin/wget 2>/dev/null || true

USER sandbox
WORKDIR /workspace

# O comando real é injetado pelo SandboxExecutor via `sh -c`
CMD ["sh"]
