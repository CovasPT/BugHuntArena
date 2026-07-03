-- =====================================================================
-- BugHunt Arena — Extensão do schema para contexto escolar (Ensiguarda)
-- Princípios RGPD para menores:
--   1. Minimização: alunos NÃO têm email pessoal — contas geridas
--      pela escola com identificador pseudonimizado.
--   2. Consentimento dos encarregados de educação registado e datado.
--   3. O professor vê progresso pedagógico, nunca o código completo
--      do histórico de outros contextos.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'aluno'
        CHECK (role IN ('aluno','professor','admin')),
    ADD COLUMN is_minor BOOLEAN NOT NULL DEFAULT false,
    -- Para menores: email é opcional (conta gerida pela escola)
    ALTER COLUMN email DROP NOT NULL;

-- Consentimento parental — obrigatório para menores antes de ativar a conta
CREATE TABLE parental_consent (
    user_id      BIGINT PRIMARY KEY REFERENCES users(id),
    guardian_name VARCHAR(160) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ,            -- RGPD: revogável a qualquer momento
    consent_document_ref VARCHAR(255)    -- referência ao papel assinado na escola
);

-- A conta de um menor só está ativa com consentimento válido:
CREATE OR REPLACE FUNCTION has_valid_consent(p_user_id BIGINT)
RETURNS BOOLEAN AS $$
DECLARE
    v_minor BOOLEAN;
    v_ok    BOOLEAN;
BEGIN
    SELECT is_minor INTO v_minor FROM users WHERE id = p_user_id;
    IF NOT v_minor THEN RETURN true; END IF;
    SELECT EXISTS (
        SELECT 1 FROM parental_consent
        WHERE user_id = p_user_id AND revoked_at IS NULL
    ) INTO v_ok;
    RETURN v_ok;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- Turmas
-- ---------------------------------------------------------------------
CREATE TABLE school_classes (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(60) NOT NULL,       -- ex: "11ºB Informática"
    school_year VARCHAR(9) NOT NULL,       -- ex: "2026/2027"
    teacher_id BIGINT NOT NULL REFERENCES users(id),
    UNIQUE (name, school_year)
);

CREATE TABLE class_members (
    class_id BIGINT NOT NULL REFERENCES school_classes(id),
    user_id  BIGINT NOT NULL REFERENCES users(id),
    PRIMARY KEY (class_id, user_id)
);

-- Trabalhos de casa / atribuições: o professor atribui desafios à turma
CREATE TABLE class_assignments (
    id           BIGSERIAL PRIMARY KEY,
    class_id     BIGINT      NOT NULL REFERENCES school_classes(id),
    challenge_id VARCHAR(60) NOT NULL REFERENCES challenges(id),
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at       TIMESTAMPTZ
);

-- ---------------------------------------------------------------------
-- Vista do professor: heatmap turma × conceito
-- (agregados pedagógicos — nunca dados pessoais desnecessários)
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW class_concept_heatmap AS
SELECT
    cm.class_id,
    cs.concept_id,
    COUNT(DISTINCT cs.user_id)                    AS alunos_com_tentativas,
    SUM(cs.total_attempts)                        AS tentativas_totais,
    ROUND(AVG(cs.total_errors::NUMERIC
              / NULLIF(cs.total_attempts, 0)), 2) AS taxa_erro_media,
    COUNT(DISTINCT ucm.user_id)                   AS alunos_que_dominam
FROM class_members cm
JOIN concept_stats cs  ON cs.user_id = cm.user_id
LEFT JOIN user_concept_mastery ucm
       ON ucm.user_id = cm.user_id AND ucm.concept_id = cs.concept_id
GROUP BY cm.class_id, cs.concept_id;

-- Alunos que precisam de atenção: taxa de erro alta com amostra suficiente
CREATE OR REPLACE FUNCTION students_needing_help(
    p_class_id BIGINT,
    p_error_threshold NUMERIC DEFAULT 0.6,
    p_min_attempts INTEGER DEFAULT 5
) RETURNS TABLE (user_id BIGINT, username VARCHAR, concept_id VARCHAR, taxa_erro NUMERIC) AS $$
BEGIN
    RETURN QUERY
    SELECT u.id, u.username, cs.concept_id,
           ROUND(cs.total_errors::NUMERIC / cs.total_attempts, 2)
    FROM class_members cm
    JOIN users u          ON u.id = cm.user_id
    JOIN concept_stats cs ON cs.user_id = u.id
    WHERE cm.class_id = p_class_id
      AND cs.total_attempts >= p_min_attempts
      AND cs.total_errors::NUMERIC / cs.total_attempts >= p_error_threshold
    ORDER BY 4 DESC;
END;
$$ LANGUAGE plpgsql;
