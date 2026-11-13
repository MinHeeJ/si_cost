-- Contract reinforcement only: no new business tables, columns, seed replay or snapshot writes.
-- Document headings (Entity Registry, BASIC-137, etc.) are not physical entities.
DO $$
DECLARE
    contract RECORD;
    actual_columns TEXT[];
BEGIN
    FOR contract IN
        SELECT * FROM (VALUES
            ('tb_co_mon_cost', ARRAY['cost_ym','dev_id','proj_id','asgn_id']::TEXT[]),
            ('tb_hr_mon_pay', ARRAY['pay_ym','dev_id']::TEXT[]),
            ('tb_pj_asgn_mon', ARRAY['asgn_id','input_ym']::TEXT[]),
            ('tb_pj_tk_dstrb', ARRAY['proj_id','dstrb_step_cd','row_sn','dstrb_ym']::TEXT[]),
            ('tb_sy_user_pref', ARRAY['user_id','pref_type_cd','pref_key']::TEXT[])
        ) AS required(table_name, key_columns)
    LOOP
        SELECT array_agg(a.attname::TEXT ORDER BY k.ordinality) INTO actual_columns
        FROM pg_constraint c
        JOIN pg_class t ON t.oid=c.conrelid
        JOIN pg_namespace n ON n.oid=t.relnamespace
        CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(attnum,ordinality)
        JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.attnum
        WHERE n.nspname=current_schema() AND t.relname=contract.table_name AND c.contype='p';
        IF actual_columns IS DISTINCT FROM contract.key_columns THEN
            RAISE EXCEPTION 'DB_SCHEMA_CONTRACT_MISMATCH: %.PRIMARY_KEY', contract.table_name;
        END IF;
    END LOOP;
END
$$;

COMMENT ON TABLE TB_CO_MON_COST IS '사람·월·프로젝트·투입별 마감 원가 사본. 월별 조회는 기존 사본을 읽으며 마감·고정 사본을 재계산하거나 저장하지 않는다.';
COMMENT ON TABLE TB_HR_MON_PAY IS '사람별 월급여 입력과 당시 계산 결과 사본. 개인 원가 조회는 이 금액과 당시 분류를 읽으며 FIX_YN 열을 사용하지 않는다.';
COMMENT ON TABLE TB_PJ_ASGN_MON IS '투입별 월 MM·적용단가·매출의 원천 및 마감 사본. 개인 원가는 실제 DEV_ID를 기준으로 조회하며 조회 중 재일할하거나 수정하지 않는다.';
COMMENT ON TABLE TB_PJ_TK_DSTRB IS '프로젝트·배분단계·행·월별 턴키 배분 원천. 개인 원가 조회의 배분 참고액이며 청구·입금 실매출 원장이 아니다.';
