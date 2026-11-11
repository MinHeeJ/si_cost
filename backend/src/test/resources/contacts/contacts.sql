INSERT INTO tb_hr_cand(cand_id,cand_nm,emp_type_cd,prvc_agre_yn,job_cd,grade_cd,hope_uprc,input_psbl_dt,cntc_stat_cd,last_cntc_dt,cand_src_cd,reg_user_id,reg_dttm)
VALUES ('CAND-900001','연락 시험 후보','FL','N','DEV','MD',1000000,DATE '2026-06-01','BEFORE',DATE '2026-04-01','SITE','admin',TIMESTAMP '2026-01-01 01:02:03.123456'),
       ('CAND-900002','보존 시험 후보','FL','N','DEV','MD',1000000,DATE '2026-06-01','ITVW',NULL,'SITE','admin',TIMESTAMP '2026-01-01 01:02:03.123456'),
       ('CAND-900003','번호 소진 후보','FL','N','DEV','MD',1000000,DATE '2026-06-01','DONE',DATE '2026-04-01','SITE','admin',TIMESTAMP '2026-01-01 01:02:03.123456');
INSERT INTO tb_hr_cand_skill(cand_id,skill_nm,sort_ord,reg_user_id) VALUES('CAND-900001','Java',1,'admin');
INSERT INTO tb_hr_cand_cntc(cand_id,cntc_sn,cntc_stat_cd,cntc_memo,cntc_dt,reg_user_id,reg_dttm)
SELECT 'CAND-900001',n,(ARRAY['BEFORE','DONE','ITVW','HOLD','CTRT'])[((n-1)%5)+1],
       CASE WHEN n=1 THEN '등록' WHEN n=2 THEN '정보 수정' ELSE '연락 사건 '||n END,
       DATE '2026-05-01'+(n/2),'admin',TIMESTAMP '2026-05-01 01:02:03.123456'
FROM generate_series(1,25) n;
INSERT INTO tb_hr_cand_cntc(cand_id,cntc_sn,cntc_stat_cd,cntc_memo,cntc_dt,reg_user_id)
VALUES('CAND-900003',99999,'DONE','번호 소진',DATE '2026-05-01','admin');
