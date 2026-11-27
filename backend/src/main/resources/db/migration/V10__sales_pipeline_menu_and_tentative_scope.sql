UPDATE tb_sy_menu
SET sort_ord = sort_ord + 1
WHERE upper_menu_id = 'G003' AND menu_id <> 'M011';

UPDATE tb_sy_menu
SET upper_menu_id = 'G003', sort_ord = 1
WHERE menu_id = 'M011';

CREATE UNIQUE INDEX IF NOT EXISTS ux_tb_sl_tent_asgn_active_opportunity_developer
ON tb_sl_tent_asgn (opp_id, dev_id)
WHERE tent_stat_cd = 'ACT';

COMMENT ON COLUMN tb_sl_tent_asgn.tent_stat_cd IS
'가배정 상태: ACT 가배정 / REL 해제 / CNVT 배정 후보 / DONE 배정 확정 / DROP 후보 제외. 개발자는 서로 다른 영업 기회에 동시에 가배정할 수 있으며 같은 기회에는 활성 가배정 1건만 유지합니다.';

ALTER TABLE tb_pj_proj_chg ADD COLUMN IF NOT EXISTS chg_rsn varchar(300);
COMMENT ON COLUMN tb_pj_proj_chg.chg_rsn IS '계약 변경 사유. 프로젝트 연장 시 이전·새 종료일과 함께 기록합니다.';
