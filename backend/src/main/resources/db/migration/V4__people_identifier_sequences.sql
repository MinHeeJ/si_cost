-- Flyway initializes absent allocators once; application startup never resets them.
DO $$
BEGIN
    IF to_regclass('sq_tb_hr_dev_id') IS NULL THEN
        CREATE SEQUENCE IF NOT EXISTS SQ_TB_HR_DEV_ID AS BIGINT
            MINVALUE 1 MAXVALUE 9999999999999999 START WITH 1 INCREMENT BY 1 NO CYCLE;
        PERFORM setval('SQ_TB_HR_DEV_ID', GREATEST(COALESCE(MAX(substring(DEV_ID FROM 5)::bigint), 0), 1),
                       COALESCE(MAX(substring(DEV_ID FROM 5)::bigint), 0) >= 1)
        FROM TB_HR_DEV WHERE DEV_ID ~ '^DEV-[0-9]{6,16}$';
    END IF;
    IF to_regclass('sq_tb_hr_cand_id') IS NULL THEN
        CREATE SEQUENCE IF NOT EXISTS SQ_TB_HR_CAND_ID AS BIGINT
            MINVALUE 1 MAXVALUE 999999999999999 START WITH 1 INCREMENT BY 1 NO CYCLE;
        PERFORM setval('SQ_TB_HR_CAND_ID', GREATEST(COALESCE(MAX(substring(CAND_ID FROM 6)::bigint), 0), 1),
                       COALESCE(MAX(substring(CAND_ID FROM 6)::bigint), 0) >= 1)
        FROM TB_HR_CAND WHERE CAND_ID ~ '^CAND-[0-9]{6,15}$';
    END IF;
    IF to_regclass('sq_tb_hr_dev_mng_no') IS NULL THEN
        CREATE SEQUENCE IF NOT EXISTS SQ_TB_HR_DEV_MNG_NO AS BIGINT
            MINVALUE 1 MAXVALUE 999999999999999999 START WITH 1 INCREMENT BY 1 NO CYCLE;
        PERFORM setval('SQ_TB_HR_DEV_MNG_NO', GREATEST(COALESCE(MAX(substring(MNG_NO FROM 3)::bigint), 0), 1),
                       COALESCE(MAX(substring(MNG_NO FROM 3)::bigint), 0) >= 1)
        FROM TB_HR_DEV WHERE MNG_NO ~ '^K-[0-9]{4,18}$';
    END IF;
END
$$;

COMMENT ON SEQUENCE SQ_TB_HR_DEV_ID IS '개발자 ID 숫자 발급. 롤백 결번을 허용하며 재사용하지 않는다.';
COMMENT ON SEQUENCE SQ_TB_HR_CAND_ID IS '후보 ID 숫자 발급. 롤백 결번을 허용하며 재사용하지 않는다.';
COMMENT ON SEQUENCE SQ_TB_HR_DEV_MNG_NO IS '개발자 자동 관리번호 숫자 발급. 직접 입력 번호는 발급 시 건너뛴다.';
