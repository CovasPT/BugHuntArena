# BugHunt Arena — Estado do Projeto

> Última atualização: 03/07/2026 · Destino: Escola Ensiguarda (ensino secundário)

---

## ✅ O que já está feito

### Core Java 21 (zero dependências · ~60 testes JUnit 5)

| Módulo | O que faz | Testes |
|---|---|---|
| `sandbox/` | Execução de código em containers Docker com 8 camadas de segurança (sem rede, sem root, read-only, limites RAM/CPU/PIDs, cap-drop, timeout) | 13 |
| `curriculum/` | DAG de conceitos com pré-requisitos, deteção de ciclos, desbloqueio progressivo, caminhos de aprendizagem em ordem topológica | 9 |
| `profile/` | Spaced repetition (intervalos 1→3→7→14→30 dias), reset ao errar, fila de revisão, deteção de padrões de erro sistemáticos | 8 |
| `mentor/` | Mentor socrático com 4 níveis progressivos, cap anti-vibecoding (resposta direta só após 3 tentativas), personalização com perfil do aluno, cliente Claude API sem dependências | 8 |
| `gamification/` | Pontos (dicas custam 20%, streak dá até +50%), streaks diários, 8 badges, curva de níveis | 16 |
| `challenge/` | Modelo de desafios + 8 desafios seed com bugs reais (off-by-one, ==/equals, mutable defaults, closures, SQL injection, segredos, race conditions) | 14 |
| `generator/` | **Gerador IA de desafios com validação automática no sandbox** — o código buggy tem de falhar E o fix canónico tem de passar, senão a variação é rejeitada e regenerada | 11 |
| `teacher/` | Analytics da turma: heatmap conceito×severidade, alertas de alunos em risco, recomendação da próxima aula | 11 |

### API REST (Spring Boot 3 · módulo `api-spring/`)
- `GET /api/challenges` + `GET /api/challenges/{id}` (o output esperado nunca é exposto)
- `POST /api/challenges/{id}/submit` — corre no sandbox, classifica o resultado
- `POST /api/mentor/hint` — dicas com progressão de níveis validada no servidor
- `POST /api/mentor/chat` — chat livre, sempre em modo socrático
- `POST /api/generator/{area}` — gera + valida desafios novos

### Base de dados (PostgreSQL)
- `schema.sql` — utilizadores, conceitos, desafios, submissões imutáveis (trigger anti-UPDATE/DELETE), badges, pontos
- `functions.sql` — PL/pgSQL: registo atómico de submissões, spaced repetition, cálculo de pontos, streaks
- `school-schema.sql` — **turmas, professores, RGPD para menores** (consentimento parental registado e revogável, contas sem email pessoal, vista de heatmap agregada)
- `seed-curriculum.sql` — 26 conceitos em 6 áreas ligados em grafo com pontes entre áreas

### Frontend (SPA vanilla JS, hash routing, single-file)
- **Página principal** com as 5 áreas (Backend, Lógica, Segurança, Web, Dados)
- **Páginas de área** com filtro por linguagem (Java/Python/JS ativos; C#, Kotlin, SQL marcados "em breve")
- **Arena** com editor, consola com **botão limpar**, e **chat do mentor** (podes escrever dúvidas livres + botão de dica com progressão de níveis)
- **Botão "Gerar desafio novo (IA)"** por área — em demo gera variações reais e resolvíveis
- **Painel do professor** (Escola Ensiguarda): recomendação da próxima aula, heatmap da turma, alunos em risco, nota RGPD
- **Logo clicável** → volta sempre à página principal
- Modo demo funciona sem backend; `DEMO=false` liga à API

### Infraestrutura
- 3 Dockerfiles de sandbox hardened (Java 21, Python 3.12, Node 20) + build script
- `docker-compose.yml` (Postgres + Adminer, schema aplicado automaticamente)
- CI GitHub Actions: testes + build das imagens + smoke test do sandbox com as flags de segurança reais

---

## ❌ O que falta fazer

### Crítico (sem isto não há piloto na Ensiguarda)
1. **Autenticação e sessões** — login de alunos/professores, hash bcrypt, sessões JWT ou cookies. Nada disto existe ainda; a API está aberta.
2. **Ligar a API à base de dados** — os controllers usam os SeedChallenges em memória; falta a camada de persistência (Spring Data JPA ou JDBC) para users, submissões, stats e turmas.
3. **Correr os testes** — o ambiente onde o código foi gerado não tinha JDK completo; correr `mvn test` localmente e corrigir o que falhar é o primeiro passo.
4. **Endpoint do professor** — `GET /api/teacher/classes/{id}/heatmap` e `/alerts` a ligar o `TeacherAnalytics` à BD (a lógica e a view SQL já existem).
5. **Documentação RGPD para a escola** — minuta de consentimento para encarregados de educação, registo de tratamento de dados, política de retenção. A direção da Ensiguarda vai pedir isto antes de tudo.

### Importante
6. **Sandbox para C#** — imagem Docker com .NET SDK + entrada no enum `Language`. Kotlin e SQL idem (SQL precisa de abordagem diferente: validar queries contra uma BD efémera).
7. **Persistir desafios gerados pela IA** — hoje vivem só na sessão; devem ir para a tabela `challenges` com flag `generated=true` e revisão do professor antes de ficarem visíveis.
8. **Rate limiting no sandbox** — um aluno pode fazer spam de submissões; fila de execução com limite por utilizador (ex: 1 execução simultânea, 10/minuto).
9. **Testes de integração reais do sandbox** — os testes atuais usam fakes; falta uma suite que corra com Docker real e tente ativamente escapar do container (rede, filesystem, fork bomb).
10. **Deploy no Azure** — App Service ou VM para a API, Azure Database for PostgreSQL, e decidir onde correm os containers de sandbox (VM dedicada é o mais simples e seguro).

### Desejável
11. Modo "explicar ao mentor" — o aluno explica o bug por palavras e a IA avalia a explicação (a mensagem pós-vitória do chat já aponta nesta direção).
12. Ligas/torneios entre turmas (o lado "arena" competitivo ainda não existe).
13. Notificações de streak (email à escola ou notificação na app).
14. Página de perfil do aluno com o "diário de erros" pessoal.

---

## 💡 O que pode ser melhorado

1. **Editor de código** — o `<textarea>` funciona, mas o Monaco Editor (o motor do VS Code) dá syntax highlighting, números de linha e autocompletar. É um upgrade de uma tarde com impacto enorme na experiência.
2. **Duplicação de lógica de pontos** — existe em Java (`GamificationEngine`) e em PL/pgSQL (`calculate_points`). Escolher a BD como única fonte de verdade e reduzir o Java a leitura, ou vice-versa. Manter as duas sincronizadas é um bug à espera de acontecer.
3. **Parse de JSON à mão** no `ClaudeLlmClient` e `LlmChallengeSource` — funciona e evita dependências no core, mas é frágil. Na camada Spring, delegar o parse ao Jackson que já lá está.
4. **`solvedIf` no frontend demo é contornável** — a validação real TEM de ser sempre a execução no sandbox + comparação de output (já é assim no backend; o frontend demo é só para demonstração).
5. **Prompt do mentor** — testar com alunos reais e iterar. A tendência dos LLMs é ser demasiado prestável; o system prompt vai precisar de afinação contínua para manter a linha socrática.
6. **Acessibilidade** — a base existe (roles, aria-labels, focus visible, reduced-motion), mas falta testar com leitor de ecrã e teclado a fundo — relevante para uma escola pública... e para qualquer escola.
7. **Heatmap do professor com dados reais** — a página usa dados demo; ligar ao endpoint quando existir (ponto 4 acima).
8. **Divisão do trabalho com o Miguel** — sugestão de fronteira limpa: um fica com sandbox + API + BD + deploy; o outro com frontend + mentor + gerador + conteúdo/currículo. As interfaces entre módulos já estão definidas.

---

## Ordem de ataque sugerida (próximas 4 semanas)

| Semana | Foco |
|---|---|
| 1 | Correr testes localmente, corrigir falhas, construir imagens Docker, smoke test real do sandbox |
| 2 | Persistência (JPA) + autenticação básica + ligar submissões à BD |
| 3 | Endpoint do professor + Monaco Editor + persistir desafios gerados |
| 4 | Deploy Azure + documentação RGPD + demo interna para a Ensiguarda |

---

## v4 — Atualizações (03/07/2026, tarde)

**Novo no frontend:**
- Menu lateral: Arena · Caderno de Estudo · Comunidade · O Meu Perfil · Painel do Professor (colapsável em mobile)
- **Caderno de Estudo**: diário de erros automático (por conceito, com padrão detetado e taxa de erro), fila "para rever hoje" (spaced repetition) e notas pessoais
- **Comunidade**: feed estilo StackOverflow para publicar código com bugs; regra da casa "respostas guiam, não resolvem"; likes e respostas; o mentor participa
- **Perfil**: avatar hexagonal, barra de nível grande, barras de progressão POR LINGUAGEM (Java/Python/JS), grelha de conquistas (desbloqueadas/bloqueadas), estatísticas
- **Sandbox mostra a linguagem** na consola e tem **seletor para trocar** — o desafio "O array rebelde" existe em Java, Python e JavaScript como demonstração de variantes
- **Modal de level-up**: resumo do percurso (o que resolveste, onde erraste mais) + recomendações do que estudar a seguir (ex: nível 4 → "hora de deixar os switches: Strategy, Observer, Factory")
- Toasts de conquistas; barra de progresso de nível no header
- Novo desafio de segurança: **"A firewall furada"** (regras first-match com ALLOW any/any no topo — o erro clássico)

**Falta (novo):**
- Persistir caderno/notas/posts na BD (hoje é estado de sessão)
- Moderação da comunidade (obrigatório com menores: aprovação do professor ou filtro automático antes de publicar)
- Variantes multi-linguagem nos desafios do backend (tabela challenge_variants)
- Desafios de cloud/firewall reais — ver secção seguinte

**Cloud/firewalls (resposta à questão "é possível?"):**
- **Sim, em 3 níveis de realismo**: (1) simuladores em código como "A firewall furada" — já feito, corre no sandbox atual; (2) validação de configs reais — o aluno escreve regras iptables/nftables ou um script Azure CLI/Terraform, e um container valida a política com testes (iptables corre DENTRO de um container com --cap-add=NET_ADMIN em rede isolada — fazível com cuidado); (3) emuladores — LocalStack (AWS) e Azurite (Azure) correm em Docker e aceitam comandos reais da CLI sem custos nem risco. Contas cloud reais para alunos: não recomendado (custos, RGPD, superfície de risco).
