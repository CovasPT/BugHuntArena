-- =====================================================================
-- BugHunt Arena — Schema PostgreSQL
-- Corresponde 1:1 aos módulos Java: curriculum, profile, gamification,
-- challenge e submissões do sandbox.
-- =====================================================================

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,          -- bcrypt, nunca plaintext
    skill_level   VARCHAR(20)  NOT NULL DEFAULT 'iniciante'
                  CHECK (skill_level IN ('iniciante','intermedio','avancado')),
    xp            INTEGER      NOT NULL DEFAULT 0,
    current_streak_days INTEGER NOT NULL DEFAULT 0,
    longest_streak_days INTEGER NOT NULL DEFAULT 0,
    last_activity_date  DATE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- Currículo: grafo de conceitos (o DAG do CurriculumGraph)
-- ---------------------------------------------------------------------
CREATE TABLE concepts (
    id          VARCHAR(60) PRIMARY KEY,          -- ex: 'ciclos-for'
    title       VARCHAR(120) NOT NULL,
    description TEXT,
    area        VARCHAR(40) NOT NULL              -- ex: 'fundamentos', 'seguranca'
);

CREATE TABLE concept_prerequisites (
    concept_id      VARCHAR(60) NOT NULL REFERENCES concepts(id),
    prerequisite_id VARCHAR(60) NOT NULL REFERENCES concepts(id),
    PRIMARY KEY (concept_id, prerequisite_id),
    CHECK (concept_id <> prerequisite_id)         -- auto-dependência proibida
);

CREATE TABLE user_concept_mastery (
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    concept_id VARCHAR(60) NOT NULL REFERENCES concepts(id),
    mastered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, concept_id)
);

-- ---------------------------------------------------------------------
-- Perfil: spaced repetition (o ErrorPatternTracker persistido)
-- ---------------------------------------------------------------------
CREATE TABLE concept_stats (
    user_id             BIGINT      NOT NULL REFERENCES users(id),
    concept_id          VARCHAR(60) NOT NULL REFERENCES concepts(id),
    total_attempts      INTEGER     NOT NULL DEFAULT 0,
    total_errors        INTEGER     NOT NULL DEFAULT 0,
    consecutive_correct INTEGER     NOT NULL DEFAULT 0,
    streak_level        SMALLINT    NOT NULL DEFAULT 0,
    last_attempt        TIMESTAMPTZ,
    next_review         TIMESTAMPTZ,
    PRIMARY KEY (user_id, concept_id)
);

CREATE INDEX idx_concept_stats_due
    ON concept_stats (user_id, next_review)
    WHERE next_review IS NOT NULL;

-- ---------------------------------------------------------------------
-- Desafios e submissões
-- ---------------------------------------------------------------------
CREATE TABLE challenges (
    id           VARCHAR(60) PRIMARY KEY,          -- ex: 'off-by-one-01'
    concept_id   VARCHAR(60) NOT NULL REFERENCES concepts(id),
    title        VARCHAR(160) NOT NULL,
    description  TEXT        NOT NULL,
    language     VARCHAR(20) NOT NULL CHECK (language IN ('JAVA','PYTHON','JAVASCRIPT')),
    buggy_code   TEXT        NOT NULL,             -- o código com o bug plantado
    expected_output TEXT     NOT NULL,             -- output correto após fix
    difficulty   SMALLINT    NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
    base_points  INTEGER     NOT NULL
);

-- Histórico IMUTÁVEL de submissões (append-only — nunca UPDATE/DELETE).
-- É este histórico que alimenta o perfil cognitivo e o mentor.
CREATE TABLE submissions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    challenge_id VARCHAR(60) NOT NULL REFERENCES challenges(id),
    code         TEXT        NOT NULL,
    result_type  VARCHAR(30) NOT NULL CHECK (result_type IN
        ('SUCCESS','COMPILE_ERROR','RUNTIME_ERROR','TIMEOUT',
         'SECURITY_VIOLATION','WRONG_OUTPUT','INFRASTRUCTURE_ERROR')),
    stdout       TEXT,
    stderr       TEXT,
    duration_ms  INTEGER,
    hints_used   SMALLINT    NOT NULL DEFAULT 0,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_submissions_user_time ON submissions (user_id, submitted_at DESC);
CREATE INDEX idx_submissions_challenge ON submissions (challenge_id);

-- Impedir alterações ao histórico (imutabilidade garantida na BD,
-- não só na aplicação):
CREATE OR REPLACE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'submissions é append-only: % proibido', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER submissions_immutable
    BEFORE UPDATE OR DELETE ON submissions
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ---------------------------------------------------------------------
-- Gamificação
-- ---------------------------------------------------------------------
CREATE TABLE badges (
    id          VARCHAR(40) PRIMARY KEY,
    title       VARCHAR(80) NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE user_badges (
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    badge_id   VARCHAR(40) NOT NULL REFERENCES badges(id),
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, badge_id)
);

CREATE TABLE point_events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT  NOT NULL REFERENCES users(id),
    submission_id BIGINT REFERENCES submissions(id),
    points      INTEGER NOT NULL,
    reason      VARCHAR(120) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
