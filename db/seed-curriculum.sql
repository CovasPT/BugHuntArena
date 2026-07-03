-- =====================================================================
-- BugHunt Arena — Seed do currículo multi-áreas
-- O grafo de conceitos que demonstra o modelo "aprender todas as áreas":
-- cada área tem o seu trilho, e há pontes entre áreas (ex: segurança
-- exige fundamentos de web). É isto que o CurriculumGraph carrega.
-- =====================================================================

INSERT INTO concepts (id, title, description, area) VALUES
-- Trilho 1: Fundamentos (a raiz de tudo)
('variaveis',        'Variáveis e Tipos',        'Declaração, tipos primitivos, conversões', 'fundamentos'),
('condicionais',     'Condicionais',             'if/else, operadores lógicos',              'fundamentos'),
('ciclos',           'Ciclos',                   'for, while, off-by-one',                   'fundamentos'),
('funcoes',          'Funções',                  'Parâmetros, retorno, scope',               'fundamentos'),
('strings',          'Strings',                  'Manipulação, comparação, imutabilidade',   'fundamentos'),
('tipos',            'Sistema de Tipos',         'Divisão inteira, casting, overflow',       'fundamentos'),
('arrays',           'Arrays e Listas',          'Indexação, iteração, bounds',              'fundamentos'),
('recursividade',    'Recursividade',            'Caso base, stack, memoização',             'fundamentos'),

-- Trilho 2: Estruturas de dados e algoritmos
('estruturas-map',   'Maps e Sets',              'Hash tables, chaves, colisões',            'algoritmos'),
('complexidade',     'Complexidade',             'Big-O, trade-offs tempo/espaço',           'algoritmos'),
('ordenacao',        'Ordenação e Pesquisa',     'Sort, binary search',                      'algoritmos'),
('grafos',           'Grafos',                   'BFS, DFS, Dijkstra',                       'algoritmos'),

-- Trilho 3: Web
('http-basics',      'HTTP',                     'Métodos, status codes, headers',           'web'),
('closures',         'Closures e Scope JS',      'var/let, closures em ciclos',              'web'),
('dom',              'DOM e Eventos',            'Manipulação, event listeners',             'web'),
('rest-apis',        'REST APIs',                'Recursos, verbos, JSON',                   'web'),

-- Trilho 4: Bases de dados
('sql-select',       'SQL: Consultas',           'SELECT, WHERE, JOIN',                      'dados'),
('sql-modelacao',    'Modelação Relacional',     'Chaves, normalização, integridade',        'dados'),
('transacoes',       'Transações',               'ACID, isolamento, deadlocks',              'dados'),

-- Trilho 5: Segurança (Secure by Design — o diferenciador)
('seguranca-input',  'Validação de Input',       'SQL injection, XSS, sanitização',          'seguranca'),
('seguranca-segredos','Gestão de Segredos',      'Env vars, nunca hardcodar credenciais',    'seguranca'),
('seguranca-auth',   'Autenticação',             'Hashing de passwords, sessões, MFA',       'seguranca'),
('seguranca-owasp',  'OWASP Top 10',             'As 10 vulnerabilidades mais comuns',       'seguranca'),

-- Trilho 6: Concorrência e sistemas
('concorrencia',     'Concorrência',             'Threads, race conditions, atomicidade',    'sistemas'),
('memoria',          'Gestão de Memória',        'Heap/stack, leaks, GC',                    'sistemas');

-- ---------------------------------------------------------------------
-- Pré-requisitos: as arestas do DAG.
-- Nota como segurança e web PARTILHAM raízes com fundamentos —
-- é isto que torna o currículo um grafo e não árvores isoladas.
-- ---------------------------------------------------------------------
INSERT INTO concept_prerequisites (concept_id, prerequisite_id) VALUES
-- Fundamentos (cadeia principal)
('condicionais',      'variaveis'),
('ciclos',            'condicionais'),
('funcoes',           'condicionais'),
('strings',           'variaveis'),
('tipos',             'variaveis'),
('arrays',            'ciclos'),
('recursividade',     'funcoes'),
('recursividade',     'arrays'),

-- Algoritmos (constrói sobre fundamentos)
('estruturas-map',    'arrays'),
('complexidade',      'ciclos'),
('ordenacao',         'arrays'),
('ordenacao',         'complexidade'),
('grafos',            'recursividade'),
('grafos',            'estruturas-map'),

-- Web
('closures',          'funcoes'),
('dom',               'closures'),
('http-basics',       'funcoes'),
('rest-apis',         'http-basics'),

-- Dados
('sql-select',        'condicionais'),
('sql-modelacao',     'sql-select'),
('transacoes',        'sql-modelacao'),
('transacoes',        'concorrencia'),

-- Segurança (exige bases de várias áreas — ponte entre trilhos)
('seguranca-input',   'strings'),
('seguranca-input',   'sql-select'),
('seguranca-segredos','funcoes'),
('seguranca-auth',    'seguranca-segredos'),
('seguranca-auth',    'http-basics'),
('seguranca-owasp',   'seguranca-input'),
('seguranca-owasp',   'seguranca-auth'),

-- Sistemas
('concorrencia',      'funcoes'),
('memoria',           'arrays');

-- ---------------------------------------------------------------------
-- Badges seed
-- ---------------------------------------------------------------------
INSERT INTO badges (id, title, description) VALUES
('FIRST_BLOOD',     'Primeiro Bug',      'Resolveste o teu primeiro desafio'),
('WEEK_STREAK',     'Semana em Chamas',  '7 dias seguidos de atividade'),
('MONTH_STREAK',    'Imparável',         '30 dias seguidos de atividade'),
('BUG_SLAYER_10',   'Caçador',           '10 desafios resolvidos'),
('BUG_SLAYER_50',   'Exterminador',      '50 desafios resolvidos'),
('PURIST',          'Purista',           '10 desafios resolvidos sem dicas'),
('SECURITY_MINDED', 'Secure by Design',  '5 desafios de segurança resolvidos'),
('COMEBACK',        'Persistente',       'Resolveste após 5+ tentativas falhadas');
