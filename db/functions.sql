-- =====================================================================
-- BugHunt Arena — Funções PL/pgSQL
-- Lógica de negócio crítica na BD (o requisito "PL/SQL" do enunciado,
-- adaptado a PostgreSQL — mencionar essa decisão no relatório).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Regista uma submissão E atualiza atomicamente o perfil,
--    o streak e os pontos — tudo numa transação.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION record_submission(
    p_user_id      BIGINT,
    p_challenge_id VARCHAR,
    p_code         TEXT,
    p_result_type  VARCHAR,
    p_stdout       TEXT,
    p_stderr       TEXT,
    p_duration_ms  INTEGER,
    p_hints_used   SMALLINT
) RETURNS BIGINT AS $$
DECLARE
    v_submission_id BIGINT;
    v_concept_id    VARCHAR;
    v_correct       BOOLEAN;
    v_points        INTEGER;
BEGIN
    v_correct := (p_result_type = 'SUCCESS');

    INSERT INTO submissions
        (user_id, challenge_id, code, result_type, stdout, stderr, duration_ms, hints_used)
    VALUES
        (p_user_id, p_challenge_id, p_code, p_result_type, p_stdout, p_stderr, p_duration_ms, p_hints_used)
    RETURNING id INTO v_submission_id;

    SELECT concept_id INTO v_concept_id FROM challenges WHERE id = p_challenge_id;

    -- Atualizar estatísticas do conceito (upsert)
    INSERT INTO concept_stats (user_id, concept_id, total_attempts, total_errors,
                               consecutive_correct, streak_level, last_attempt, next_review)
    VALUES (p_user_id, v_concept_id, 1,
            CASE WHEN v_correct THEN 0 ELSE 1 END,
            CASE WHEN v_correct THEN 1 ELSE 0 END,
            CASE WHEN v_correct THEN 1 ELSE 0 END,
            now(),
            now() + spaced_interval(CASE WHEN v_correct THEN 0 ELSE 0 END))
    ON CONFLICT (user_id, concept_id) DO UPDATE SET
        total_attempts      = concept_stats.total_attempts + 1,
        total_errors        = concept_stats.total_errors + CASE WHEN v_correct THEN 0 ELSE 1 END,
        consecutive_correct = CASE WHEN v_correct THEN concept_stats.consecutive_correct + 1 ELSE 0 END,
        next_review         = now() + spaced_interval(
                                  CASE WHEN v_correct THEN concept_stats.streak_level ELSE 0 END),
        streak_level        = CASE WHEN v_correct
                                   THEN LEAST(concept_stats.streak_level + 1, 4)
                                   ELSE 0 END,
        last_attempt        = now();

    -- Streak diário + pontos só em caso de sucesso
    IF v_correct THEN
        PERFORM update_daily_streak(p_user_id);
        v_points := calculate_points(p_challenge_id, p_hints_used, p_user_id);
        INSERT INTO point_events (user_id, submission_id, points, reason)
        VALUES (p_user_id, v_submission_id, v_points, 'Desafio resolvido: ' || p_challenge_id);
        UPDATE users SET xp = xp + v_points WHERE id = p_user_id;
    END IF;

    RETURN v_submission_id;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 2. Intervalos de spaced repetition (espelha o ErrorPatternTracker)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION spaced_interval(p_streak_level SMALLINT)
RETURNS INTERVAL AS $$
BEGIN
    RETURN CASE LEAST(GREATEST(p_streak_level, 0), 4)
        WHEN 0 THEN INTERVAL '1 day'
        WHEN 1 THEN INTERVAL '3 days'
        WHEN 2 THEN INTERVAL '7 days'
        WHEN 3 THEN INTERVAL '14 days'
        ELSE        INTERVAL '30 days'
    END;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ---------------------------------------------------------------------
-- 3. Cálculo de pontos (espelha o GamificationEngine em Java —
--    manter as duas implementações sincronizadas ou escolher uma
--    como fonte de verdade; recomendo a BD para evitar race conditions)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION calculate_points(
    p_challenge_id VARCHAR,
    p_hints_used   SMALLINT,
    p_user_id      BIGINT
) RETURNS INTEGER AS $$
DECLARE
    v_base       INTEGER;
    v_streak     INTEGER;
    v_multiplier NUMERIC;
    v_penalty    NUMERIC;
BEGIN
    SELECT base_points INTO v_base FROM challenges WHERE id = p_challenge_id;
    SELECT current_streak_days INTO v_streak FROM users WHERE id = p_user_id;

    -- Multiplicador de streak: +5% por dia, máximo +50%
    v_multiplier := 1 + LEAST(v_streak * 0.05, 0.5);

    -- Penalização por dicas: -20% por dica, mínimo 20% dos pontos base
    v_penalty := GREATEST(1 - p_hints_used * 0.20, 0.20);

    RETURN GREATEST(ROUND(v_base * v_multiplier * v_penalty)::INTEGER, 1);
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 4. Streak diário estilo Duolingo
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_daily_streak(p_user_id BIGINT)
RETURNS VOID AS $$
DECLARE
    v_last DATE;
BEGIN
    SELECT last_activity_date INTO v_last FROM users WHERE id = p_user_id;

    IF v_last = CURRENT_DATE THEN
        RETURN;                                   -- já contou hoje
    ELSIF v_last = CURRENT_DATE - 1 THEN
        UPDATE users SET
            current_streak_days = current_streak_days + 1,
            longest_streak_days = GREATEST(longest_streak_days, current_streak_days + 1),
            last_activity_date  = CURRENT_DATE
        WHERE id = p_user_id;
    ELSE
        UPDATE users SET                          -- quebrou o streak
            current_streak_days = 1,
            longest_streak_days = GREATEST(longest_streak_days, 1),
            last_activity_date  = CURRENT_DATE
        WHERE id = p_user_id;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 5. Fila de revisão de um utilizador (para o ecrã "rever hoje")
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION due_for_review(p_user_id BIGINT)
RETURNS TABLE (concept_id VARCHAR, total_errors INTEGER, next_review TIMESTAMPTZ) AS $$
BEGIN
    RETURN QUERY
    SELECT cs.concept_id, cs.total_errors, cs.next_review
    FROM concept_stats cs
    WHERE cs.user_id = p_user_id
      AND cs.next_review <= now()
    ORDER BY cs.total_errors DESC, cs.next_review ASC;
END;
$$ LANGUAGE plpgsql;
