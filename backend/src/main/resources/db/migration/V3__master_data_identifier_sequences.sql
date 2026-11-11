-- Source: https://github.com/MinHeeJ/si_cost
-- Forward-only allocation; Flyway owns initialization, never application startup.
CREATE SEQUENCE SQ_TB_BS_CLNT_ID AS BIGINT MINVALUE 1 MAXVALUE 999999999999999999 START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE SQ_TB_BS_PART_ID AS BIGINT MINVALUE 1 MAXVALUE 999999999999999 START WITH 1 INCREMENT BY 1 NO CYCLE;

SELECT setval('SQ_TB_BS_CLNT_ID', GREATEST(COALESCE(MAX(substring(CLNT_ID FROM 3)::bigint), 0), 1),
              COALESCE(MAX(substring(CLNT_ID FROM 3)::bigint), 0) >= 1)
FROM TB_BS_CLNT WHERE CLNT_ID ~ '^C-[0-9]{3,18}$';
SELECT setval('SQ_TB_BS_PART_ID', GREATEST(COALESCE(MAX(substring(PART_ID FROM 6)::bigint), 0), 1),
              COALESCE(MAX(substring(PART_ID FROM 6)::bigint), 0) >= 1)
FROM TB_BS_PART WHERE PART_ID ~ '^PART-[0-9]{3,15}$';

COMMENT ON SEQUENCE SQ_TB_BS_CLNT_ID IS '거래처 ID의 숫자 부분 발급. 롤백 결번을 허용하며 재사용하지 않는다.';
COMMENT ON SEQUENCE SQ_TB_BS_PART_ID IS 'HQ를 제외한 일반 영업파트 ID의 숫자 부분 발급. 롤백 결번을 허용하며 재사용하지 않는다.';
