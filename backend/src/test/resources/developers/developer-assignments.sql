INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id)
SELECT 'DEV-000901','K-0901','투입검증','FT','DEV','DRCT',part_id,'IDLE','admin'
FROM tb_bs_part WHERE hq_yn='Y' ORDER BY part_id LIMIT 1;
INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id)
VALUES ('P-901','동명 프로젝트','SI','ING','2025-01-01','2026-12-31',0,0,0,'admin'),
('P-902','동명 프로젝트','SI','END','2025-01-01','2026-12-31',0,0,0,'admin');
INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id)
SELECT 'A-'||lpad(n::text,4,'0'),CASE WHEN n%2=0 THEN 'P-901' ELSE 'P-902' END,'DEV-000901','DEV',
DATE '2025-01-01'+(n/2),DATE '2025-03-01'+n,2.50,'NORM',0,CASE WHEN n%3=0 THEN 'CNCL' ELSE 'ACT' END,'admin'
FROM generate_series(1,55) n;
