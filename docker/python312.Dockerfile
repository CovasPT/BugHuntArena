# Imagem mínima para submissões Python.
FROM python:3.12-alpine

RUN addgroup -g 1000 sandbox && adduser -u 1000 -G sandbox -D -H sandbox

# Remover pip do PATH do utilizador sandbox — submissões não instalam pacotes
RUN rm -f /usr/local/bin/pip* /sbin/apk /usr/bin/wget 2>/dev/null || true

USER sandbox
WORKDIR /workspace
CMD ["sh"]
