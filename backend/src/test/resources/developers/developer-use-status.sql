INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,use_yn,retire_dt,reg_user_id)
SELECT 'DEV-'||lpad(n::text,6,'0'),'K-'||n,'중지검증'||n,'FT','DEV','DRCT',p.part_id,'IDLE',
CASE WHEN n IN (903,906) THEN 'N' ELSE 'Y' END,CASE WHEN n=906 THEN DATE '2026-06-15' ELSE NULL END,'admin'
FROM generate_series(902,908) n CROSS JOIN (SELECT part_id FROM tb_bs_part WHERE hq_yn='Y' ORDER BY part_id LIMIT 1) p;
INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id)
VALUES ('P-903','중지참조','SI','ING','2026-01-01','2027-12-31',0,0,0,'admin');
INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id)
VALUES ('A-903','P-903','DEV-000904','DEV','2026-07-01','2026-08-01',1,'NORM',0,'ACT','admin'),
('A-904','P-903','DEV-000905','DEV','2026-05-01','2026-06-16',1,'NORM',0,'ACT','admin'),
('A-905','P-903','DEV-000907','DEV','2026-07-01','2026-08-01',1,'NORM',0,'CNCL','admin'),
('A-906','P-903','DEV-000908','DEV','2026-05-01','2026-06-15',1,'NORM',0,'ACT','admin');
