# Imagem mínima para submissões JavaScript.
FROM node:20-alpine

RUN addgroup -g 1000 sandbox 2>/dev/null || true; \
    adduser -u 1000 -G sandbox -D -H sandbox 2>/dev/null || true

# npm/npx removidos — submissões não instalam pacotes
RUN rm -f /usr/local/bin/npm /usr/local/bin/npx /sbin/apk /usr/bin/wget 2>/dev/null || true

USER 1000
WORKDIR /workspace
CMD ["sh"]
