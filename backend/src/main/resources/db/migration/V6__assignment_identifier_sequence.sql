-- Flyway initializes the allocator once; startup and retries never reset it.
DO $$
BEGIN
    IF to_regclass('sq_tb_pj_asgn_id') IS NULL THEN
        CREATE SEQUENCE IF NOT EXISTS SQ_TB_PJ_ASGN_ID AS BIGINT
            MINVALUE 1 MAXVALUE 999999999999999999 START WITH 1 INCREMENT BY 1 NO CYCLE;
        PERFORM setval('SQ_TB_PJ_ASGN_ID', GREATEST(COALESCE(MAX(substring(ASGN_ID FROM 3)::numeric), 0), 1)::bigint,
                       COALESCE(MAX(substring(ASGN_ID FROM 3)::numeric), 0) >= 1)
        FROM TB_PJ_ASGN WHERE ASGN_ID ~ '^A-[0-9]+$';
    END IF;
END
$$;

COMMENT ON SEQUENCE SQ_TB_PJ_ASGN_ID IS '투입 ID 숫자 발급. 최초 기존 숫자 최댓값 다음부터 발급하며 롤백 결번을 재사용하지 않는다.';
