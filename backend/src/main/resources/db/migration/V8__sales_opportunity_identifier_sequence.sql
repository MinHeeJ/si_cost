DO $$
DECLARE highest NUMERIC;
BEGIN
  IF to_regclass('sq_tb_sl_opp_id') IS NULL THEN
    SELECT COALESCE(max(substring(opp_id FROM 3)::numeric),0) INTO highest
    FROM tb_sl_opp WHERE opp_id ~ '^O-[0-9]+$';
    IF highest >= 999999999999999999 THEN
      CREATE SEQUENCE sq_tb_sl_opp_id MINVALUE 1 MAXVALUE 999999999999999999 NO CYCLE;
      PERFORM setval('sq_tb_sl_opp_id',999999999999999999,true);
    ELSE
      CREATE SEQUENCE sq_tb_sl_opp_id MINVALUE 1 MAXVALUE 999999999999999999 NO CYCLE;
      PERFORM setval('sq_tb_sl_opp_id',(highest+1)::bigint,false);
    END IF;
  END IF;
END $$;
COMMENT ON SEQUENCE sq_tb_sl_opp_id IS '영업 기회 ID의 증가 번호. 기존 기회 이후부터 발급하며 삭제한 번호를 재사용하지 않는다.';
