INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES ('PART-HISTORY','이력 검증 파트','admin');
INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id)
VALUES ('DEV-000901','HISTORY-901','이력 검증 개발자','FT','DEV','DRCT','PART-HISTORY','IDLE','admin'),
       ('DEV-000902','HISTORY-902','빈 이력 개발자','FT','DEV','DRCT','PART-HISTORY','IDLE','admin');
INSERT INTO tb_hr_dev_prc(dev_id,aply_bgn_ym,mon_uprc,reg_user_id) VALUES ('DEV-000901','202601',100,'admin');
INSERT INTO tb_hr_dev_stat(dev_id,stat_sn,work_stat_cd,bgn_dt,end_dt,reg_user_id)
VALUES ('DEV-000901',1,'LEAVE','2026-01-01','2026-01-02','admin');
INSERT INTO tb_hr_dev_skill(dev_id,skill_nm,sort_ord,reg_user_id) VALUES ('DEV-000901','Java',1,'admin');
INSERT INTO tb_hr_cand(cand_id,cand_nm,emp_type_cd,job_cd,grade_cd,hope_uprc,input_psbl_dt,cntc_stat_cd,cand_src_cd,reg_user_id)
VALUES ('CAND-000901','이력 검증 후보','FL','DEV','MD',100,'2026-01-01','BEFORE','SITE','admin');
INSERT INTO tb_hr_cand_skill(cand_id,skill_nm,sort_ord,reg_user_id) VALUES ('CAND-000901','SQL',1,'admin');
INSERT INTO tb_hr_cand_cntc(cand_id,cntc_sn,cntc_stat_cd,cntc_memo,cntc_dt,reg_user_id)
VALUES ('CAND-000901',1,'DONE','실제 연락','2026-01-02','admin');
INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,aft_val,reg_user_id,reg_dttm)
VALUES ('TB_HR_DEV','DEV-000901','NEW','등록',NULL,'등록 당시 이름','admin','2026-01-01 09:00:59'),
       ('TB_HR_DEV_PRC','DEV-000901:202601','NEW','월 단가',NULL,'100','admin','2026-01-02 09:00:00'),
       ('TB_HR_DEV_STAT','DEV-000901:1','NEW','근무 상태',NULL,'LEAVE','admin','2026-01-02 09:00:00'),
       ('TB_HR_DEV_SKILL','DEV-000901','UPD','보유 스킬',NULL,'["Java"]','admin','2026-01-02 09:00:01'),
       ('TB_HR_CAND','CAND-000901','NEW','등록',NULL,'등록 당시 후보','admin','2026-01-01 09:00:00'),
       ('TB_HR_CAND_SKILL','CAND-000901','UPD','보유 스킬',NULL,'["SQL"]','admin','2026-01-02 09:00:00'),
       ('TB_HR_CAND_CNTC','CAND-000901:1','NEW','연락',NULL,'DONE','admin','2026-01-02 09:00:00'),
       ('TB_HR_CAND','CAND-000901','UPD','TELNO','[비공개]','[비공개]','admin','2026-01-03 09:00:00'),
       ('TB_HR_CAND','CAND-000901','UPD','EMAIL_ADDR',NULL,'[비공개]','admin','2026-01-03 09:00:00');
