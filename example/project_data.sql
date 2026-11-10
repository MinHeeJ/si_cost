-- 초기 데이터: 공통코드·운영 기준값·역할·메뉴·역할별 권한 처음 값·첫 시스템 관리자 계정
-- 근거: 06 5장(공통코드 초기값)·6장(운영 기준값 초기값), 04 SFR-124-01~04(역할·권한 처음 값), 04 SFR-125-01·03·06(메뉴), 시안 v1.7
-- 실행 순서: 07_DDL_PostgreSQL.sql → 이 파일 → 07_DML_행정구역_초기데이터.sql(순서는 서로 상관없음)
-- 운영 기준값의 적용 시작월(APLY_BGN_YM)은 시스템을 쓰기 시작한 달(CASH_BASE_YM과 같게)로 둔다. 다른 달에 시작하면 이 파일의 202601을 모두 바꾼다
BEGIN;

-- 1. 공통코드 그룹 (64개, 시스템 여부 Y 44개)
INSERT INTO TB_SY_CODE_GRP (CODE_GRP_ID, CODE_GRP_NM, SYS_CODE_YN, VAL_CODE_YN, SORT_ORD, USE_YN, REG_USER_ID) VALUES
('CTRT_TYPE', '계약유형', 'Y', 'N', 1, 'Y', 'SYSTEM'),
('EMP_TYPE', '직원구분', 'Y', 'N', 2, 'Y', 'SYSTEM'),
('JOB', '직무', 'N', 'N', 3, 'Y', 'SYSTEM'),
('GRADE', '등급', 'N', 'N', 4, 'Y', 'SYSTEM'),
('JBPS', '직위', 'N', 'N', 5, 'Y', 'SYSTEM'),
('INPUT_TYPE', '투입 구분', 'Y', 'N', 6, 'Y', 'SYSTEM'),
('DSTRB_STEP', '배분 단계', 'Y', 'N', 7, 'Y', 'SYSTEM'),
('EXT_PSBL', '연장 가능성', 'N', 'N', 8, 'Y', 'SYSTEM'),
('SGA_ITEM', '판관비 항목', 'N', 'N', 9, 'Y', 'SYSTEM'),
('DRCT_EXPS_ITEM', '직접 경비 항목', 'N', 'N', 10, 'Y', 'SYSTEM'),
('CHG_ITEM', '변경 항목', 'N', 'N', 11, 'Y', 'SYSTEM'),
('ALWNC', '수당코드', 'N', 'Y', 12, 'Y', 'SYSTEM'),
('PROJ_STAT', '프로젝트 상태', 'Y', 'N', 13, 'Y', 'SYSTEM'),
('WORK_STAT', '근무 상태', 'Y', 'N', 14, 'Y', 'SYSTEM'),
('FIN_CLS', '재무 구분', 'Y', 'N', 15, 'Y', 'SYSTEM'),
('BILL_STAT', '청구 상태', 'Y', 'N', 16, 'Y', 'SYSTEM'),
('SALES_STEP', '영업 단계', 'Y', 'N', 17, 'Y', 'SYSTEM'),
('APRV_STAT', '견적 승인 상태', 'Y', 'N', 18, 'Y', 'SYSTEM'),
('ISSUE_STAT', '이슈 상태', 'Y', 'N', 19, 'Y', 'SYSTEM'),
('IMPR', '이슈 중요도', 'N', 'N', 20, 'Y', 'SYSTEM'),
('PROJ_STEP', '프로젝트 단계명', 'N', 'N', 21, 'Y', 'SYSTEM'),
('CLOSE_STAT', '마감 상태', 'Y', 'N', 22, 'Y', 'SYSTEM'),
('CNTC_STAT', '후보 연락 상태', 'N', 'N', 23, 'Y', 'SYSTEM'),
('CAND_SRC', '후보 출처', 'N', 'N', 24, 'Y', 'SYSTEM'),
('ASGN_CHG_RSN', '투입 변경 사유', 'N', 'N', 25, 'Y', 'SYSTEM'),
('LOSE_RSN', '실주 사유', 'N', 'N', 26, 'Y', 'SYSTEM'),
('RCMD_EXCL', '추천 제외 사유', 'N', 'N', 27, 'Y', 'SYSTEM'),
('SKILL', '스킬', 'N', 'N', 28, 'Y', 'SYSTEM'),
('BANK', '은행', 'N', 'N', 29, 'Y', 'SYSTEM'),
('INDUTY', '업종', 'N', 'N', 30, 'Y', 'SYSTEM'),
('CLNT_TYPE', '거래처 구분', 'Y', 'N', 31, 'Y', 'SYSTEM'),
('DATA_SCOPE', '데이터 범위', 'Y', 'N', 32, 'Y', 'SYSTEM'),
('USER_STAT', '사용자 상태', 'Y', 'N', 33, 'Y', 'SYSTEM'),
('SCOPE_TYPE', '담당 범위 유형', 'Y', 'N', 34, 'Y', 'SYSTEM'),
('PREF_TYPE', '화면 설정 유형', 'Y', 'N', 35, 'Y', 'SYSTEM'),
('NOTI_TYPE', '알림 유형', 'Y', 'N', 36, 'Y', 'SYSTEM'),
('SEND_MTHD', '발송 방법', 'Y', 'N', 37, 'Y', 'SYSTEM'),
('CHG_TYPE', '변경 기록 유형', 'Y', 'N', 38, 'Y', 'SYSTEM'),
('ACT_TYPE', '접근 기록 작업', 'Y', 'N', 39, 'Y', 'SYSTEM'),
('FORM_TYPE', '업로드 양식', 'Y', 'N', 40, 'Y', 'SYSTEM'),
('UPLD_STAT', '업로드 상태', 'Y', 'N', 41, 'Y', 'SYSTEM'),
('RSLT_TYPE', '업로드 결과 유형', 'Y', 'N', 42, 'Y', 'SYSTEM'),
('ANS_STAT', 'AI 답변 상태', 'Y', 'N', 43, 'Y', 'SYSTEM'),
('DRCT_CLS', '직간접 구분', 'Y', 'N', 44, 'Y', 'SYSTEM'),
('SRC', '인원 계획 출처', 'Y', 'N', 45, 'Y', 'SYSTEM'),
('ROW_TYPE', '턴키 배분 줄 유형', 'Y', 'N', 46, 'Y', 'SYSTEM'),
('ASGN_STAT', '투입 상태', 'Y', 'N', 47, 'Y', 'SYSTEM'),
('ASGN_CHG_TYPE', '투입 변경 유형', 'Y', 'N', 48, 'Y', 'SYSTEM'),
('OPP_STAT', '영업기회 상태', 'Y', 'N', 49, 'Y', 'SYSTEM'),
('TENT_STAT', '가배정 상태', 'Y', 'N', 50, 'Y', 'SYSTEM'),
('APRV_ACT', '견적 승인 작업', 'Y', 'N', 51, 'Y', 'SYSTEM'),
('RCMD_TYPE', '추천 유형', 'Y', 'N', 52, 'Y', 'SYSTEM'),
('USER_ACT', '추천 후 사용자 작업', 'Y', 'N', 53, 'Y', 'SYSTEM'),
('COST_CLS', '원가 구분', 'Y', 'N', 54, 'Y', 'SYSTEM'),
('ADJ_ITEM', '전월 조정 항목', 'Y', 'N', 55, 'Y', 'SYSTEM'),
('SETL_STAT', '파트 정산 상태', 'Y', 'N', 56, 'Y', 'SYSTEM'),
('PAY_TYPE', '법인세 납부 유형', 'Y', 'N', 57, 'Y', 'SYSTEM'),
('IO', '대여금 입출 구분', 'Y', 'N', 58, 'Y', 'SYSTEM'),
('XFER_CLS', '계좌이체 구분', 'Y', 'N', 59, 'Y', 'SYSTEM'),
('ACT', '마감 작업', 'Y', 'N', 60, 'Y', 'SYSTEM'),
('CHK_ITEM', '마감 점검 항목', 'Y', 'N', 61, 'Y', 'SYSTEM'),
('CHK_STAT', '마감 점검 상태', 'Y', 'N', 62, 'Y', 'SYSTEM'),
('RSM_LABEL', '이력서 이름표', 'N', 'N', 63, 'Y', 'SYSTEM'),
('RSM_JOB_KW', '이력서 직무 단어', 'N', 'N', 64, 'Y', 'SYSTEM');

-- 2. 공통코드
INSERT INTO TB_SY_CODE (CODE_GRP_ID, CODE, CODE_NM, SORT_ORD, USE_YN, DEL_YN, CODE_VAL, UNIT_NM, ADD_VAL1, ADD_VAL2, REG_USER_ID) VALUES
('CTRT_TYPE', 'TK', '턴키', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CTRT_TYPE', 'MO', '월단위', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CTRT_TYPE', 'DP', '파견', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CTRT_TYPE', 'IN', '내부(자체 개발)', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('EMP_TYPE', 'FT', '정규직', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('EMP_TYPE', 'CT', '계약직', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('EMP_TYPE', 'FL', '프리랜서', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('EMP_TYPE', 'OS', '외주', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('JOB', 'PM', 'PM·사업관리', 1, 'Y', 'N', NULL, NULL, 'PL,PLN', '8500000', 'SYSTEM'),
('JOB', 'PL', 'PL·개발리더', 2, 'Y', 'N', NULL, NULL, 'BE,ARC,PM', '8000000', 'SYSTEM'),
('JOB', 'PLN', '기획·컨설팅', 3, 'Y', 'N', NULL, NULL, 'PM,UX', '6500000', 'SYSTEM'),
('JOB', 'ARC', '아키텍트', 4, 'Y', 'N', NULL, NULL, 'PL,BE', '8800000', 'SYSTEM'),
('JOB', 'BE', '백엔드 개발', 5, 'Y', 'N', NULL, NULL, 'PL,FE,DATA', '6000000', 'SYSTEM'),
('JOB', 'FE', '프론트엔드 개발', 6, 'Y', 'N', NULL, NULL, 'PUB,UX,BE', '5500000', 'SYSTEM'),
('JOB', 'UX', 'UI/UX 디자인', 7, 'Y', 'N', NULL, NULL, 'PUB,FE,PLN', '5000000', 'SYSTEM'),
('JOB', 'PUB', '퍼블리싱', 8, 'Y', 'N', NULL, NULL, 'FE,UX', '4000000', 'SYSTEM'),
('JOB', 'QA', 'QA·테스트', 9, 'Y', 'N', NULL, NULL, 'BE,FE', '4500000', 'SYSTEM'),
('JOB', 'DATA', '데이터(DA·DBA)', 10, 'Y', 'N', NULL, NULL, 'BE,AI', '6500000', 'SYSTEM'),
('JOB', 'OPS', '운영·유지보수', 11, 'Y', 'N', NULL, NULL, 'BE,DATA', '4500000', 'SYSTEM'),
('JOB', 'AI', 'AI·데이터 분석', 12, 'Y', 'N', NULL, NULL, 'DATA,BE', '7500000', 'SYSTEM'),
('GRADE', 'JR', '초급', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('GRADE', 'MD', '중급', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('GRADE', 'SR', '고급', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('GRADE', 'EX', '특급', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('JBPS', 'MEMBER', '팀원', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('JBPS', 'TEAM', '팀장', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('JBPS', 'GROUP', '그룹장', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INPUT_TYPE', 'NEW', '신규', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INPUT_TYPE', 'ADD', '추가투입', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INPUT_TYPE', 'SUB', '대체투입', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INPUT_TYPE', 'FREE', '무상지원', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DSTRB_STEP', 'EST', '견적', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DSTRB_STEP', 'CTRT', '최종 계약', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DSTRB_STEP', 'PLAN', '실제 투입계획', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('EXT_PSBL', 'H', '높음 (80%)', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('EXT_PSBL', 'M', '보통 (50%)', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('EXT_PSBL', 'L', '낮음 (0%)', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SGA_ITEM', 'RENT', '임대료', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SGA_ITEM', 'COMM', '통신비', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SGA_ITEM', 'WELF', '복리후생비', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SGA_ITEM', 'EDU', '교육훈련비', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SGA_ITEM', 'TRAV', '여비교통비', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DRCT_EXPS_ITEM', 'TRIP', '출장비', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DRCT_EXPS_ITEM', 'EQUIP', '장비 임대', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DRCT_EXPS_ITEM', 'DINE', '회식비', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DRCT_EXPS_ITEM', 'ETC', '기타', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'CTRT', '계약', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'AMT', '계약금액', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'PERIOD', '계약기간', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'PART', '영업파트', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'LOC', '근무지', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'BUDGET', '예산(목표원가)', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'TEAM', '팀비', 7, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'PEXP', '프로젝트경비', 8, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_ITEM', 'ETC', '기타', 9, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ALWNC', 'ALLOW_TEAM', '팀장 수당(월)', 1, 'Y', 'N', 200000, '원', NULL, NULL, 'SYSTEM'),
('ALWNC', 'ALLOW_GROUP', '그룹장 수당(월)', 2, 'Y', 'N', 400000, '원', NULL, NULL, 'SYSTEM'),
('ALWNC', 'INS_RATE', '4대보험(회사) 부담률', 3, 'Y', 'N', 9.4, '%', NULL, NULL, 'SYSTEM'),
('ALWNC', 'SEV_RATE', '퇴직금 적립률', 4, 'Y', 'N', 8.33, '%', NULL, NULL, 'SYSTEM'),
('ALWNC', 'TEAM_FEE', '팀비(1인당 월, 기본값)', 5, 'Y', 'N', 50000, '원', NULL, NULL, 'SYSTEM'),
('ALWNC', 'PEXP_FEE', '프로젝트경비(1인당 월, 기본값)', 6, 'Y', 'N', 50000, '원', NULL, NULL, 'SYSTEM'),
('PROJ_STAT', 'ING', '진행', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PROJ_STAT', 'PRE', '수주예정', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PROJ_STAT', 'END', '종료', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PROJ_STAT', 'LOSE', '실주', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('WORK_STAT', 'INPUT', '투입중', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('WORK_STAT', 'WTHDR', '철수예정', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('WORK_STAT', 'IDLE', '대기', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('WORK_STAT', 'LEAVE', '휴직', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('WORK_STAT', 'RETIRE', '퇴사', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('WORK_STAT', 'WORK', '재직', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FIN_CLS', 'SGA', '판관비', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FIN_CLS', 'NOPR_IN', '영업외수익', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FIN_CLS', 'NOPR_OUT', '영업외비용', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('BILL_STAT', 'UNPAID', '미수', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('BILL_STAT', 'PAID', '입금 완료', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('BILL_STAT', 'LATE', '연체', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('BILL_STAT', 'CNCL', '취소됨', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SALES_STEP', 'FIND', '발굴', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SALES_STEP', 'PROP', '제안', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SALES_STEP', 'NEGO', '협상', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SALES_STEP', 'PRE', '계약 직전', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('APRV_STAT', 'NONE', '승인 불필요', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('APRV_STAT', 'WAIT', '승인 대기', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('APRV_STAT', 'OK', '승인', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('APRV_STAT', 'REJ', '반려', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ISSUE_STAT', 'OPEN', '열림', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ISSUE_STAT', 'ACT', '조치 중', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ISSUE_STAT', 'CLOSE', '종료', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('IMPR', 'HIGH', '높음', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('IMPR', 'MID', '중간', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('IMPR', 'LOW', '낮음', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PROJ_STEP', 'TK_KICK', '착수', 1, 'Y', 'N', NULL, NULL, '5', 'TK,IN', 'SYSTEM'),
('PROJ_STEP', 'TK_ANLY', '분석', 2, 'Y', 'N', NULL, NULL, '15', 'TK,IN', 'SYSTEM'),
('PROJ_STEP', 'TK_DSGN', '설계', 3, 'Y', 'N', NULL, NULL, '20', 'TK,IN', 'SYSTEM'),
('PROJ_STEP', 'TK_DEV', '개발', 4, 'Y', 'N', NULL, NULL, '35', 'TK,IN', 'SYSTEM'),
('PROJ_STEP', 'TK_TEST', '테스트', 5, 'Y', 'N', NULL, NULL, '15', 'TK,IN', 'SYSTEM'),
('PROJ_STEP', 'TK_OPEN', '오픈', 6, 'Y', 'N', NULL, NULL, '5', 'TK,IN', 'SYSTEM'),
('PROJ_STEP', 'TK_END', '종료', 7, 'Y', 'N', NULL, NULL, '5', 'TK,IN', 'SYSTEM'),
('PROJ_STEP', 'OP_KICK', '착수', 8, 'Y', 'N', NULL, NULL, '5', 'MO,DP', 'SYSTEM'),
('PROJ_STEP', 'OP_HAND', '인수인계', 9, 'Y', 'N', NULL, NULL, '10', 'MO,DP', 'SYSTEM'),
('PROJ_STEP', 'OP_STAB', '안정화', 10, 'Y', 'N', NULL, NULL, '15', 'MO,DP', 'SYSTEM'),
('PROJ_STEP', 'OP_RUN', '정기 운영', 11, 'Y', 'N', NULL, NULL, '65', 'MO,DP', 'SYSTEM'),
('PROJ_STEP', 'OP_END', '종료', 12, 'Y', 'N', NULL, NULL, '5', 'MO,DP', 'SYSTEM'),
('CLOSE_STAT', 'WORK', '작업중', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CLOSE_STAT', 'CLOSE', '마감', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CNTC_STAT', 'BEFORE', '연락 전', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CNTC_STAT', 'DONE', '연락함', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CNTC_STAT', 'ITVW', '면접 예정', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CNTC_STAT', 'HOLD', '보류', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CNTC_STAT', 'CTRT', '계약 완료', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CAND_SRC', 'SITE', '구인 사이트', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CAND_SRC', 'VNDR', '협력사 추천', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CAND_SRC', 'ACQ', '지인 추천', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CAND_SRC', 'PAST', '과거 투입', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_RSN', 'CUST', '고객 요청', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_RSN', 'SCHD', '프로젝트 일정 변경', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_RSN', 'NEGO', '단가 재협상', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_RSN', 'HR', '인력 사정', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_RSN', 'FIX', '입력 오류 바로잡기', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_RSN', 'ETC', '기타', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('LOSE_RSN', 'PRICE', '가격', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('LOSE_RSN', 'TECH', '기술 평가', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('LOSE_RSN', 'SCHD', '일정', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('LOSE_RSN', 'CNCL', '고객 사업 취소', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('LOSE_RSN', 'ETC', '기타', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('RCMD_EXCL', 'SCHD', '일정이 맞지 않음', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('RCMD_EXCL', 'HOPE', '본인 희망과 다름', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('RCMD_EXCL', 'CUST', '고객 요청', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('RCMD_EXCL', 'ETC', '기타', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SKILL', 'SK001', '사업관리', 1, 'Y', 'N', NULL, NULL, 'PM', NULL, 'SYSTEM'),
('SKILL', 'SK002', 'PMO', 2, 'Y', 'N', NULL, NULL, 'PM', NULL, 'SYSTEM'),
('SKILL', 'SK003', '금융 업무', 3, 'Y', 'N', NULL, NULL, 'PM', NULL, 'SYSTEM'),
('SKILL', 'SK004', 'Java', 4, 'Y', 'N', NULL, NULL, 'PL,BE', NULL, 'SYSTEM'),
('SKILL', 'SK005', 'Spring', 5, 'Y', 'N', NULL, NULL, 'PL,BE', NULL, 'SYSTEM'),
('SKILL', 'SK006', '설계 리딩', 6, 'Y', 'N', NULL, NULL, 'PL', NULL, 'SYSTEM'),
('SKILL', 'SK007', '요구 분석', 7, 'Y', 'N', NULL, NULL, 'PLN', NULL, 'SYSTEM'),
('SKILL', 'SK008', 'UX 리서치', 8, 'Y', 'N', NULL, NULL, 'PLN,UX', NULL, 'SYSTEM'),
('SKILL', 'SK009', '업무 컨설팅', 9, 'Y', 'N', NULL, NULL, 'PLN', NULL, 'SYSTEM'),
('SKILL', 'SK010', 'MSA', 10, 'Y', 'N', NULL, NULL, 'ARC', NULL, 'SYSTEM'),
('SKILL', 'SK011', '클라우드', 11, 'Y', 'N', NULL, NULL, 'ARC', NULL, 'SYSTEM'),
('SKILL', 'SK012', 'Kubernetes', 12, 'Y', 'N', NULL, NULL, 'ARC', NULL, 'SYSTEM'),
('SKILL', 'SK013', 'Oracle', 13, 'Y', 'N', NULL, NULL, 'BE,DATA', NULL, 'SYSTEM'),
('SKILL', 'SK014', 'Python', 14, 'Y', 'N', NULL, NULL, 'BE,AI', NULL, 'SYSTEM'),
('SKILL', 'SK015', 'Node.js', 15, 'Y', 'N', NULL, NULL, 'BE', NULL, 'SYSTEM'),
('SKILL', 'SK016', 'React', 16, 'Y', 'N', NULL, NULL, 'FE', NULL, 'SYSTEM'),
('SKILL', 'SK017', 'Vue', 17, 'Y', 'N', NULL, NULL, 'FE', NULL, 'SYSTEM'),
('SKILL', 'SK018', 'TypeScript', 18, 'Y', 'N', NULL, NULL, 'FE', NULL, 'SYSTEM'),
('SKILL', 'SK019', 'Figma', 19, 'Y', 'N', NULL, NULL, 'UX', NULL, 'SYSTEM'),
('SKILL', 'SK020', 'HTML/CSS', 20, 'Y', 'N', NULL, NULL, 'PUB', NULL, 'SYSTEM'),
('SKILL', 'SK021', '웹 접근성', 21, 'Y', 'N', NULL, NULL, 'PUB', NULL, 'SYSTEM'),
('SKILL', 'SK022', '테스트 자동화', 22, 'Y', 'N', NULL, NULL, 'QA', NULL, 'SYSTEM'),
('SKILL', 'SK023', '성능 테스트', 23, 'Y', 'N', NULL, NULL, 'QA', NULL, 'SYSTEM'),
('SKILL', 'SK024', 'PostgreSQL', 24, 'Y', 'N', NULL, NULL, 'DATA', NULL, 'SYSTEM'),
('SKILL', 'SK025', '데이터 모델링', 25, 'Y', 'N', NULL, NULL, 'DATA', NULL, 'SYSTEM'),
('SKILL', 'SK026', 'Linux', 26, 'Y', 'N', NULL, NULL, 'OPS', NULL, 'SYSTEM'),
('SKILL', 'SK027', '모니터링', 27, 'Y', 'N', NULL, NULL, 'OPS', NULL, 'SYSTEM'),
('SKILL', 'SK028', '배치 운영', 28, 'Y', 'N', NULL, NULL, 'OPS', NULL, 'SYSTEM'),
('SKILL', 'SK029', 'LLM', 29, 'Y', 'N', NULL, NULL, 'AI', NULL, 'SYSTEM'),
('SKILL', 'SK030', '머신러닝', 30, 'Y', 'N', NULL, NULL, 'AI', NULL, 'SYSTEM'),
('BANK', 'KDB', 'KDB산업은행', 1, 'Y', 'N', NULL, NULL, '002', NULL, 'SYSTEM'),
('BANK', 'IBK', 'IBK기업은행', 2, 'Y', 'N', NULL, NULL, '003', NULL, 'SYSTEM'),
('BANK', 'KB', 'KB국민은행', 3, 'Y', 'N', NULL, NULL, '004', NULL, 'SYSTEM'),
('BANK', 'SUHYUP', 'Sh수협은행', 4, 'Y', 'N', NULL, NULL, '007', NULL, 'SYSTEM'),
('BANK', 'NH', 'NH농협은행', 5, 'Y', 'N', NULL, NULL, '011', NULL, 'SYSTEM'),
('BANK', 'NH_LOCAL', '지역농·축협', 6, 'Y', 'N', NULL, NULL, '012', NULL, 'SYSTEM'),
('BANK', 'WOORI', '우리은행', 7, 'Y', 'N', NULL, NULL, '020', NULL, 'SYSTEM'),
('BANK', 'SC', 'SC제일은행', 8, 'Y', 'N', NULL, NULL, '023', NULL, 'SYSTEM'),
('BANK', 'CITI', '한국씨티은행', 9, 'Y', 'N', NULL, NULL, '027', NULL, 'SYSTEM'),
('BANK', 'IM', 'iM뱅크', 10, 'Y', 'N', NULL, NULL, '031', NULL, 'SYSTEM'),
('BANK', 'BUSAN', '부산은행', 11, 'Y', 'N', NULL, NULL, '032', NULL, 'SYSTEM'),
('BANK', 'KJB', '광주은행', 12, 'Y', 'N', NULL, NULL, '034', NULL, 'SYSTEM'),
('BANK', 'JEJU', '제주은행', 13, 'Y', 'N', NULL, NULL, '035', NULL, 'SYSTEM'),
('BANK', 'JBB', '전북은행', 14, 'Y', 'N', NULL, NULL, '037', NULL, 'SYSTEM'),
('BANK', 'KNB', '경남은행', 15, 'Y', 'N', NULL, NULL, '039', NULL, 'SYSTEM'),
('BANK', 'MG', '새마을금고', 16, 'Y', 'N', NULL, NULL, '045', NULL, 'SYSTEM'),
('BANK', 'CU', '신협', 17, 'Y', 'N', NULL, NULL, '048', NULL, 'SYSTEM'),
('BANK', 'SAVINGS', '저축은행', 18, 'Y', 'N', NULL, NULL, '050', NULL, 'SYSTEM'),
('BANK', 'NFCF', '산림조합', 19, 'Y', 'N', NULL, NULL, '064', NULL, 'SYSTEM'),
('BANK', 'POST', '우체국', 20, 'Y', 'N', NULL, NULL, '071', NULL, 'SYSTEM'),
('BANK', 'HANA', '하나은행', 21, 'Y', 'N', NULL, NULL, '081', NULL, 'SYSTEM'),
('BANK', 'SHINHAN', '신한은행', 22, 'Y', 'N', NULL, NULL, '088', NULL, 'SYSTEM'),
('BANK', 'KBANK', '케이뱅크', 23, 'Y', 'N', NULL, NULL, '089', NULL, 'SYSTEM'),
('BANK', 'KAKAO', '카카오뱅크', 24, 'Y', 'N', NULL, NULL, '090', NULL, 'SYSTEM'),
('BANK', 'TOSS', '토스뱅크', 25, 'Y', 'N', NULL, NULL, '092', NULL, 'SYSTEM'),
('INDUTY', 'FIN_BANK', '금융(은행)', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'FIN_INS', '금융(보험)', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'FIN_CARD', '금융(카드)', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'PUB', '공공기관', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'LOCAL', '지자체', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'MFG_ELEC', '제조(전자)', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'MFG_PHAR', '제조(제약)', 7, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'LOGI', '물류', 8, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'SI', 'SI·IT 서비스', 9, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('INDUTY', 'SW', 'SW 개발', 10, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CLNT_TYPE', 'CUST', '고객사', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CLNT_TYPE', 'VNDR', '협력사', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CLNT_TYPE', 'BOTH', '고객·협력', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DATA_SCOPE', 'ALL', '전체', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DATA_SCOPE', 'PART', '담당 파트', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DATA_SCOPE', 'PROJ', '담당 프로젝트', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('USER_STAT', 'USE', '사용', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('USER_STAT', 'LOCK', '잠김', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('USER_STAT', 'STOP', '중지', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SCOPE_TYPE', 'PART', '담당 파트', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SCOPE_TYPE', 'PROJ', '담당 프로젝트', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PREF_TYPE', 'FAV', '즐겨찾기', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PREF_TYPE', 'RECENT', '최근', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PREF_TYPE', 'COLS', '열 설정', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PREF_TYPE', 'PAGE_SIZE', '한 쪽 줄 수', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PREF_TYPE', 'AMT_UNIT', '금액 단위', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PREF_TYPE', 'MENU_FOLD', '메뉴 접기', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PREF_TYPE', 'DENSE', '촘촘히 보기', 7, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PREF_TYPE', 'PERIOD', '조회 기간', 8, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('NOTI_TYPE', 'DUN', '미수', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('NOTI_TYPE', 'WTHDR', '철수', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('NOTI_TYPE', 'CASH_SHORT', '자금 부족', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('NOTI_TYPE', 'UPLD_DONE', '업로드 완료', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('NOTI_TYPE', 'AI_DONE', 'AI 분석 완료', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('NOTI_TYPE', 'QUOTE_REJ', '견적 반려', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SEND_MTHD', 'SYS', '시스템', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SEND_MTHD', 'MAIL', '메일', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SEND_MTHD', 'BOTH', '메일+시스템', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_TYPE', 'NEW', '신규 등록', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_TYPE', 'UPD', '수정', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_TYPE', 'STOP', '사용중지', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_TYPE', 'RESUME', '다시 사용', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHG_TYPE', 'DEL', '삭제', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'LOGIN', '로그인', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'LOGOUT', '로그아웃', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'LOGIN_FAIL', '로그인 실패', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'DNLD', '다운로드', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'CLOSE', '마감', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'UNCLOSE', '마감 해제', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'AUTH_CHG', '권한 변경', 7, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'USER_REG', '사용자 등록', 8, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'SNSTV_VIEW', '민감 항목 보기', 9, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT_TYPE', 'ACCT_VIEW', '계좌번호 보기', 10, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B1', '급여', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B2', '재무항목', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B5', '투입목록', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B6', '턴키배분표', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B7', '거래처', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B8', '개발자', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B9', '프로젝트', 7, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B10', '사업계획', 8, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('FORM_TYPE', 'B11', '전년손익', 9, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('UPLD_STAT', 'CHECKED', '검사완료', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('UPLD_STAT', 'APPLIED', '반영', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('UPLD_STAT', 'CANCEL', '취소', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('RSLT_TYPE', 'WARN', '경고', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('RSLT_TYPE', 'ERR', '오류', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ANS_STAT', 'OK', '답변', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ANS_STAT', 'NO_ANS', '답할 수 없음', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ANS_STAT', 'ERR', '오류', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DRCT_CLS', 'DRCT', '직접', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('DRCT_CLS', 'INDRCT', '간접(관리직)', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SRC', 'MANUAL', '직접 입력', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SRC', 'OPP', '수주 시 필요 인력에서 생성', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ROW_TYPE', 'DEV', '사람 줄', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ROW_TYPE', 'ITEM', '항목 줄(제경비·기술료)', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_STAT', 'ACT', '유효', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_STAT', 'CNCL', '배정취소', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'INPUT', '투입', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'EXT', '연장', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'EARLY', '조기철수', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'UPRC', '단가변경', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'EXTP', '연장가능성변경', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'KIND', '투입구분변경', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'CRCT', '정정', 7, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'CNCL', '취소', 8, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ASGN_CHG_TYPE', 'RATE', '월MM변경', 9, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('OPP_STAT', 'ING', '진행', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('OPP_STAT', 'WIN', '수주', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('OPP_STAT', 'LOSE', '실주', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('OPP_STAT', 'STOP', '사용 안 함', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('TENT_STAT', 'ACT', '가배정', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('TENT_STAT', 'REL', '해제', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('TENT_STAT', 'CNVT', '배정 후보로 넘김', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('TENT_STAT', 'DONE', '배정 확정', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('TENT_STAT', 'DROP', '후보에서 뺌', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('APRV_ACT', 'REQ', '요청', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('APRV_ACT', 'OK', '승인', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('APRV_ACT', 'REJ', '반려', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('APRV_ACT', 'AUTO_REL', '요구 해제', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('RCMD_TYPE', 'AI', 'AI 인력 추천', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('RCMD_TYPE', 'NEXT', '다음 자리', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('USER_ACT', 'ASGN', '배정', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('USER_ACT', 'TENT', '가배정', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('USER_ACT', 'EXCL', '제외', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('COST_CLS', 'ALLOC', '매출원가', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('COST_CLS', 'IDLE', '대기원가', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('COST_CLS', 'RND', '연구개발비', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('COST_CLS', 'INDRCT', '간접인건비', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('COST_CLS', 'PRE', '선투입', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ADJ_ITEM', 'SALES', '매출', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ADJ_ITEM', 'COST', '원가', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ADJ_ITEM', 'SGA', '판관비', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ADJ_ITEM', 'ETC', '기타', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SETL_STAT', 'WORK', '작업중', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SETL_STAT', 'CNFM', '확정', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('SETL_STAT', 'AUTO', '마감 시 자동 확정', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PAY_TYPE', 'CNFM', '확정 신고', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('PAY_TYPE', 'MID', '중간예납', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('IO', 'IN', '받음', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('IO', 'OUT', '갚음', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('XFER_CLS', 'EXP', '계좌이체(제경비)', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('XFER_CLS', 'SPCL', '특별비(미지급 인건비)', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT', 'CLOSE', '마감', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('ACT', 'OPEN', '해제', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHK_ITEM', 'PAY', '급여 업로드', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHK_ITEM', 'MM_OVER', 'MM 1.0 초과자', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHK_ITEM', 'BILL', '세금계산서 번호 미입력', 3, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHK_ITEM', 'FI', '재무 항목', 4, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHK_ITEM', 'SETL', '영업 파트 정산', 5, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHK_ITEM', 'BILL_MISS', '청구 누락 점검', 6, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHK_STAT', 'OK', '정상', 1, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
('CHK_STAT', 'WARN', '확인 필요', 2, 'Y', 'N', NULL, NULL, NULL, NULL, 'SYSTEM'),
-- 2026-11-03 추가 기능: 외부 인력 후보 [이력서 등록] 이름표 사전(ADD_VAL1=넣을 칸)·직무 단어표(ADD_VAL1=직무 코드)
('RSM_LABEL', 'L001', '이름', 1, 'Y', 'N', NULL, NULL, 'NAME', NULL, 'SYSTEM'),
('RSM_LABEL', 'L002', '성명', 2, 'Y', 'N', NULL, NULL, 'NAME', NULL, 'SYSTEM'),
('RSM_LABEL', 'L003', 'Name', 3, 'Y', 'N', NULL, NULL, 'NAME', NULL, 'SYSTEM'),
('RSM_LABEL', 'L004', '전화번호', 4, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L005', '전화', 5, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L006', '휴대폰', 6, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L007', '휴대전화', 7, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L008', '핸드폰', 8, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L009', '연락처', 9, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L010', 'HP', 10, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L011', 'H.P', 11, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L012', 'Mobile', 12, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L013', 'Tel', 13, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L014', 'Phone', 14, 'Y', 'N', NULL, NULL, 'TEL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L015', '이메일', 15, 'Y', 'N', NULL, NULL, 'MAIL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L016', 'E-mail', 16, 'Y', 'N', NULL, NULL, 'MAIL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L017', 'Email', 17, 'Y', 'N', NULL, NULL, 'MAIL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L018', '메일', 18, 'Y', 'N', NULL, NULL, 'MAIL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L019', '주소', 19, 'Y', 'N', NULL, NULL, 'ADDR', NULL, 'SYSTEM'),
('RSM_LABEL', 'L020', '현주소', 20, 'Y', 'N', NULL, NULL, 'ADDR', NULL, 'SYSTEM'),
('RSM_LABEL', 'L021', '거주지', 21, 'Y', 'N', NULL, NULL, 'ADDR', NULL, 'SYSTEM'),
('RSM_LABEL', 'L022', 'Address', 22, 'Y', 'N', NULL, NULL, 'ADDR', NULL, 'SYSTEM'),
('RSM_LABEL', 'L023', '기술', 23, 'Y', 'N', NULL, NULL, 'SKILL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L024', '보유기술', 24, 'Y', 'N', NULL, NULL, 'SKILL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L025', '스킬', 25, 'Y', 'N', NULL, NULL, 'SKILL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L026', 'Skill', 26, 'Y', 'N', NULL, NULL, 'SKILL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L027', 'Skills', 27, 'Y', 'N', NULL, NULL, 'SKILL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L028', '기술스택', 28, 'Y', 'N', NULL, NULL, 'SKILL', NULL, 'SYSTEM'),
('RSM_LABEL', 'L029', '희망단가', 29, 'Y', 'N', NULL, NULL, 'WANT', NULL, 'SYSTEM'),
('RSM_LABEL', 'L030', '희망금액', 30, 'Y', 'N', NULL, NULL, 'WANT', NULL, 'SYSTEM'),
('RSM_LABEL', 'L031', '희망급여', 31, 'Y', 'N', NULL, NULL, 'WANT', NULL, 'SYSTEM'),
('RSM_LABEL', 'L032', '희망연봉', 32, 'Y', 'N', NULL, NULL, 'WANT', NULL, 'SYSTEM'),
('RSM_LABEL', 'L033', '투입가능일', 33, 'Y', 'N', NULL, NULL, 'FROM', NULL, 'SYSTEM'),
('RSM_LABEL', 'L034', '가능일', 34, 'Y', 'N', NULL, NULL, 'FROM', NULL, 'SYSTEM'),
('RSM_LABEL', 'L035', '입사가능일', 35, 'Y', 'N', NULL, NULL, 'FROM', NULL, 'SYSTEM'),
('RSM_LABEL', 'L036', '투입가능시기', 36, 'Y', 'N', NULL, NULL, 'FROM', NULL, 'SYSTEM'),
('RSM_LABEL', 'L037', '소속', 37, 'Y', 'N', NULL, NULL, 'VNDR', NULL, 'SYSTEM'),
('RSM_LABEL', 'L038', '소속업체', 38, 'Y', 'N', NULL, NULL, 'VNDR', NULL, 'SYSTEM'),
('RSM_LABEL', 'L039', '소속회사', 39, 'Y', 'N', NULL, NULL, 'VNDR', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K001', 'PM', 1, 'Y', 'N', NULL, NULL, 'PM', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K002', '사업관리', 2, 'Y', 'N', NULL, NULL, 'PM', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K003', '프로젝트관리', 3, 'Y', 'N', NULL, NULL, 'PM', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K004', 'PL', 4, 'Y', 'N', NULL, NULL, 'PL', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K005', '개발리더', 5, 'Y', 'N', NULL, NULL, 'PL', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K006', '기획', 6, 'Y', 'N', NULL, NULL, 'PLN', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K007', '컨설팅', 7, 'Y', 'N', NULL, NULL, 'PLN', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K008', '아키텍트', 8, 'Y', 'N', NULL, NULL, 'ARC', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K009', '백엔드', 9, 'Y', 'N', NULL, NULL, 'BE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K010', 'Backend', 10, 'Y', 'N', NULL, NULL, 'BE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K011', '서버개발', 11, 'Y', 'N', NULL, NULL, 'BE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K012', 'API', 12, 'Y', 'N', NULL, NULL, 'BE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K013', 'Spring', 13, 'Y', 'N', NULL, NULL, 'BE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K014', 'Java', 14, 'Y', 'N', NULL, NULL, 'BE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K015', '프론트엔드', 15, 'Y', 'N', NULL, NULL, 'FE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K016', '프론트', 16, 'Y', 'N', NULL, NULL, 'FE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K017', 'Frontend', 17, 'Y', 'N', NULL, NULL, 'FE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K018', 'React', 18, 'Y', 'N', NULL, NULL, 'FE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K019', 'Vue', 19, 'Y', 'N', NULL, NULL, 'FE', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K020', 'UI/UX', 20, 'Y', 'N', NULL, NULL, 'UX', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K021', 'UX디자인', 21, 'Y', 'N', NULL, NULL, 'UX', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K022', '퍼블리싱', 22, 'Y', 'N', NULL, NULL, 'PUB', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K023', 'QA', 23, 'Y', 'N', NULL, NULL, 'QA', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K024', '테스트', 24, 'Y', 'N', NULL, NULL, 'QA', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K025', 'DBA', 25, 'Y', 'N', NULL, NULL, 'DATA', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K026', 'DA', 26, 'Y', 'N', NULL, NULL, 'DATA', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K027', '데이터모델링', 27, 'Y', 'N', NULL, NULL, 'DATA', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K028', '운영', 28, 'Y', 'N', NULL, NULL, 'OPS', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K029', '유지보수', 29, 'Y', 'N', NULL, NULL, 'OPS', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K030', 'SM', 30, 'Y', 'N', NULL, NULL, 'OPS', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K031', '머신러닝', 31, 'Y', 'N', NULL, NULL, 'AI', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K032', '딥러닝', 32, 'Y', 'N', NULL, NULL, 'AI', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K033', 'AI', 33, 'Y', 'N', NULL, NULL, 'AI', NULL, 'SYSTEM'),
('RSM_JOB_KW', 'K034', '데이터분석', 34, 'Y', 'N', NULL, NULL, 'AI', NULL, 'SYSTEM');

-- 3. 운영 기준값 (43개)
INSERT INTO TB_SY_OPER_STD (STD_CD, APLY_BGN_YM, STD_NM, STD_VAL, UNIT_NM, STD_DESC, REG_USER_ID) VALUES
('PART_DSTRB_RT', '202601', '파트 이익 배분율(신규 파트 기본)', 40, '%', '식-43', 'SYSTEM'),
('PART_SALES_EXPS', '202601', '공통관리비(1인·월, 신규 파트 기본)', 1500000, '원', '식-42', 'SYSTEM'),
('VAT_RT', '202601', '부가세율', 10, '%', '식-05, 식-49', 'SYSTEM'),
('WTHLD_RT', '202601', '원천세율', 3.3, '%', '식-17, 식-49', 'SYSTEM'),
('TGT_UTIL_RT', '202601', '목표 가동률', 90, '%', '식-37, 식-65', 'SYSTEM'),
('TGT_MRGN_RT', '202601', '목표 마진율', 20, '%', '식-19, 식-21, 식-56, 식-85, 식-90', 'SYSTEM'),
('QUOTE_APRV_RT', '202601', '견적 승인 기준 이익률', 15, '%', '식-86·87', 'SYSTEM'),
('QUOTE_REAPRV_GAP', '202601', '재승인 이익률 차이', 0.5, '%p', '식-86', 'SYSTEM'),
('LOW_MRGN_GAP', '202601', '저마진 경고폭', 5, '%p', '식-94', 'SYSTEM'),
('BDGT_WARN_RT', '202601', '예산 소진 주의', 80, '%', '식-19', 'SYSTEM'),
('DUN_D1', '202601', '미수 알림 1단계', 7, '일', '식-89 (묶음 DUN_D)', 'SYSTEM'),
('DUN_D2', '202601', '미수 알림 2단계', 30, '일', '식-89 (묶음 DUN_D)', 'SYSTEM'),
('DUN_D3', '202601', '미수 알림 3단계', 60, '일', '식-89 (묶음 DUN_D)', 'SYSTEM'),
('WTHDR_NOTI_D', '202601', '철수 알림 일수', 30, '일', '공통-15 「철수 n일 전」 알림(02 SFR-015-04, 2026-10-31 C23). 철수 리스트 빨강·주황 기준은 따르지 않음', 'SYSTEM'),
('EXT_PRBL_H', '202601', '연장 확률 높음', 80, '%', '식-60 (묶음 EXT_PRBL)', 'SYSTEM'),
('EXT_PRBL_M', '202601', '연장 확률 보통', 50, '%', '식-60 (묶음 EXT_PRBL)', 'SYSTEM'),
('EXT_PRBL_L', '202601', '연장 확률 낮음', 0, '%', '식-60 (묶음 EXT_PRBL)', 'SYSTEM'),
('EXT_PRBL_NULL', '202601', '연장 확률 미정', 50, '%', '식-60 (묶음 EXT_PRBL)', 'SYSTEM'),
('STEP_PRBL_1', '202601', '영업 단계 확률 발굴', 10, '%', '식-61 (묶음 STEP_PRBL)', 'SYSTEM'),
('STEP_PRBL_2', '202601', '영업 단계 확률 제안', 30, '%', '식-61 (묶음 STEP_PRBL)', 'SYSTEM'),
('STEP_PRBL_3', '202601', '영업 단계 확률 협상', 60, '%', '식-61 (묶음 STEP_PRBL)', 'SYSTEM'),
('STEP_PRBL_4', '202601', '영업 단계 확률 계약 직전', 90, '%', '식-61 (묶음 STEP_PRBL)', 'SYSTEM'),
('SHORT_PERSON', '202601', '인력 부족 기준', 0.4, '명', '식-62, 식-93', 'SYSTEM'),
('SURPLUS_PERSON', '202601', '인력 남음 기준', 1.0, '명', '식-62', 'SYSTEM'),
('HIRE_LEAD_MON', '202601', '채용 준비 기간', 2, '개월', '식-93', 'SYSTEM'),
('MIN_CASH', '202601', '최소 운영자금', 1000000000, '원', '식-51 (Q-21-2 결정)', 'SYSTEM'),
('CASH_BASE_YM', '202601', '자금수지 시작 월', 202601, '연월', '식-46 (Q-21-1 결정, 한 번 넣는 값: 아래 규칙 ③). 시스템을 쓰기 시작한 달로도 씀: 이 달 전은 옮겨 온 달(마감으로 봄, 02 DAR-001, 2026-10-31 C16)', 'SYSTEM'),
('CASH_BASE_AMT', '202601', '자금수지 기초 잔액', 1150000000, '원', '식-46 (Q-21-1 결정, 시안 값, 한 번 넣는 값: 아래 규칙 ③)', 'SYSTEM'),
('BILL_MISS_RT', '202601', '청구 누락 허용 오차 비율', 0.5, '%', '식-88 (묶음 BILL_MISS)', 'SYSTEM'),
('BILL_MISS_MIN', '202601', '청구 누락 허용 오차 최소 금액', 10000, '원', '식-88 (묶음 BILL_MISS)', 'SYSTEM'),
('COST_OVER_UPRC_RT', '202601', '단가 대비 원가 기준', 80, '%', '식-91', 'SYSTEM'),
('MM_OVER', '202601', 'MM 초과 경고 기준', 1.0, 'MM', '식-95', 'SYSTEM'),
('LOCK_FAIL_CNT', '202601', '로그인 잠금 실패 횟수', 5, '회', '공통-07', 'SYSTEM'),
('SESSION_MIN', '202601', '자동 로그아웃', 30, '분', '공통-07', 'SYSTEM'),
('PWD_CHG_DAY', '202601', '비밀번호 변경 안내 주기', 90, '일', '공통-07', 'SYSTEM'),
('AI_SCORE_JOB', '202601', 'AI 추천 배점 직무', 25, '점', '식-68 (묶음 AI_SCORE, 28번 0번 결정)', 'SYSTEM'),
('AI_SCORE_SKILL', '202601', 'AI 추천 배점 스킬', 20, '점', '식-68 (묶음 AI_SCORE)', 'SYSTEM'),
('AI_SCORE_CUST', '202601', 'AI 추천 배점 고객사·업종', 15, '점', '식-68 (묶음 AI_SCORE)', 'SYSTEM'),
('AI_SCORE_GRADE', '202601', 'AI 추천 배점 등급', 10, '점', '식-68 (묶음 AI_SCORE)', 'SYSTEM'),
('AI_SCORE_PERF', '202601', 'AI 추천 배점 과거 성과', 5, '점', '식-68 (묶음 AI_SCORE)', 'SYSTEM'),
('AI_SCORE_COST', '202601', 'AI 추천 배점 비용 효과', 5, '점', '식-68 (묶음 AI_SCORE)', 'SYSTEM'),
('AI_SCORE_AVAIL', '202601', 'AI 추천 배점 투입 가능일', 10, '점', '식-68 (묶음 AI_SCORE)', 'SYSTEM'),
('AI_SCORE_LOC', '202601', 'AI 추천 배점 근무지', 10, '점', '식-68 (묶음 AI_SCORE)', 'SYSTEM');

-- 4. 역할 (기본 5개, 삭제 불가)
INSERT INTO TB_SY_ROLE (ROLE_ID, ROLE_NM, DATA_SCOPE_CD, SNSTV_VIEW_YN, SYS_ROLE_YN, SORT_ORD, USE_YN, REG_USER_ID) VALUES
('ADMIN', '시스템 관리자', 'ALL', 'Y', 'Y', 1, 'Y', 'SYSTEM'),
('EXEC', '경영진', 'ALL', 'Y', 'Y', 2, 'Y', 'SYSTEM'),
('FIN', '재무 담당', 'ALL', 'Y', 'Y', 3, 'Y', 'SYSTEM'),
('SALES_LDR', '영업 파트장', 'PART', 'N', 'Y', 4, 'Y', 'SYSTEM'),
('PM', '일반 사용자(PM)', 'PROJ', 'N', 'Y', 5, 'Y', 'SYSTEM');

-- 5. 메뉴 (대메뉴 G001~, 서브메뉴 M001~)
INSERT INTO TB_SY_MENU (MENU_ID, UPPER_MENU_ID, MENU_NM, SCRN_ID, MENU_LVL, SORT_ORD, USE_YN, PC_ONLY_YN, ICON_NM, LOCK_YN, REG_USER_ID) VALUES
('G001', NULL, '대시보드', NULL, 1, 1, 'Y', 'N', 'grid', 'N', 'SYSTEM'),
('M001', 'G001', '대시보드', 'UI-16', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('G002', NULL, '기준정보', NULL, 1, 2, 'Y', 'N', 'db', 'N', 'SYSTEM'),
('M002', 'G002', '거래처·영업 파트', 'UI-15', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M003', 'G002', '개발자 풀', 'UI-02', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('G003', NULL, '프로젝트·투입', NULL, 1, 3, 'Y', 'N', 'folder', 'N', 'SYSTEM'),
('M004', 'G003', '프로젝트', 'UI-01', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M005', 'G003', '프로젝트 현황판', 'UI-31', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M006', 'G003', '투입 배정', 'UI-12', 2, 3, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M007', 'G003', '철수 리스트', 'UI-05', 2, 4, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M008', 'G003', '인원 계획 대비 투입', 'UI-11', 2, 5, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('G004', NULL, '인력 계획', NULL, 1, 4, 'Y', 'N', 'calendar', 'N', 'SYSTEM'),
('M009', 'G004', '인력 운영 보드', 'UI-28', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M010', 'G004', '인력 수요 예측', 'UI-17', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M011', 'G004', '영업 파이프라인', 'UI-29', 2, 3, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M012', 'G004', '외부 인력 후보', 'UI-33', 2, 4, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('G005', NULL, '매출', NULL, 1, 5, 'Y', 'N', 'receipt', 'N', 'SYSTEM'),
('M013', 'G005', '청구·입금', 'UI-13', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('G006', NULL, '원가·수익', NULL, 1, 6, 'Y', 'N', 'chart', 'N', 'SYSTEM'),
('M014', 'G006', '개인 원가·수익표', 'UI-03', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M015', 'G006', '프로젝트 원가 분석', 'UI-04', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M016', 'G006', '영업 파트 이익금', 'UI-19', 2, 3, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M017', 'G006', '단가 재협상 대상', 'UI-32', 2, 4, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('G007', NULL, '재무·자금', NULL, 1, 7, 'Y', 'N', 'wallet', 'N', 'SYSTEM'),
('M018', 'G007', '재무 항목', 'UI-10', 2, 1, 'Y', 'Y', NULL, 'N', 'SYSTEM'),
('M019', 'G007', '전체 수익 구조', 'UI-07', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M020', 'G007', '영업이익표', 'UI-08', 2, 3, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M021', 'G007', '순이익표', 'UI-09', 2, 4, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M022', 'G007', '사업계획 대비 실적', 'UI-18', 2, 5, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M023', 'G007', '자금수지표', 'UI-21', 2, 6, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('G008', NULL, '마감', NULL, 1, 8, 'Y', 'N', 'lock', 'N', 'SYSTEM'),
('M024', 'G008', '월 마감', 'UI-14', 2, 1, 'Y', 'Y', NULL, 'N', 'SYSTEM'),
('G009', NULL, 'AI', NULL, 1, 9, 'Y', 'N', 'sparkle', 'N', 'SYSTEM'),
('M025', 'G009', 'AI 분석 도우미', 'UI-26', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('M026', 'G009', 'AI 인력 추천', 'UI-30', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
('G010', NULL, '시스템', NULL, 1, 10, 'Y', 'N', 'gear', 'Y', 'SYSTEM'),
('M027', 'G010', '사용자', 'UI-23', 2, 1, 'Y', 'Y', NULL, 'Y', 'SYSTEM'),
('M028', 'G010', '권한', 'UI-24', 2, 2, 'Y', 'Y', NULL, 'Y', 'SYSTEM'),
('M029', 'G010', '메뉴', 'UI-25', 2, 3, 'Y', 'Y', NULL, 'Y', 'SYSTEM'),
('M030', 'G010', '공통코드', 'UI-27', 2, 4, 'Y', 'Y', NULL, 'Y', 'SYSTEM');

-- 6. 역할별 권한 처음 값 (화면 메뉴만, 조회·등록·수정·삭제·업로드·다운로드·확정)
INSERT INTO TB_SY_ROLE_MENU_AUTH (ROLE_ID, MENU_ID, INQ_YN, REG_YN, UPD_YN, DEL_YN, UPLD_YN, DNLD_YN, CNFM_YN, REG_USER_ID) VALUES
('ADMIN', 'M001', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M002', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M003', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M004', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M005', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M006', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M007', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M008', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M009', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M010', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M011', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M012', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M013', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M014', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M015', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M016', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M017', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M018', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M019', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M020', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M021', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M022', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M023', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M024', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M025', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M026', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M027', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M028', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M029', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('ADMIN', 'M030', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('EXEC', 'M001', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M002', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M003', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M004', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M005', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M006', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M007', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M008', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M009', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M010', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M011', 'Y', 'N', 'N', 'N', 'N', 'Y', 'Y', 'SYSTEM'),
('EXEC', 'M012', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M013', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M014', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M015', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M016', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M017', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M018', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M019', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M020', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M021', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M022', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M023', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M024', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M025', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M026', 'Y', 'N', 'N', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('EXEC', 'M027', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('EXEC', 'M028', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('EXEC', 'M029', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('EXEC', 'M030', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('FIN', 'M001', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M002', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M003', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M004', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M005', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M006', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M007', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M008', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M009', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M010', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M011', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M012', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M013', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M014', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M015', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M016', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M017', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M018', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M019', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M020', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M021', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M022', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M023', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M024', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M025', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M026', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M027', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M028', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M029', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('FIN', 'M030', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'Y', 'SYSTEM'),
('SALES_LDR', 'M001', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M002', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M003', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M004', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M005', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M006', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M007', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M008', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M009', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M010', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M011', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M012', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M013', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M014', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M015', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M016', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M017', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M018', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M019', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M020', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M021', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M022', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M023', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M024', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M025', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M026', 'Y', 'Y', 'Y', 'N', 'N', 'Y', 'N', 'SYSTEM'),
('SALES_LDR', 'M027', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M028', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M029', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('SALES_LDR', 'M030', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M001', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M002', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M003', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M004', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M005', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M006', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M007', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M008', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M009', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M010', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M011', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M012', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M013', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M014', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M015', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M016', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M017', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M018', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M019', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M020', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M021', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M022', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M023', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M024', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M025', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M026', 'Y', 'Y', 'Y', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M027', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M028', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M029', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM'),
('PM', 'M030', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'SYSTEM');

-- 7. 첫 시스템 관리자 계정 (2026-11-03 사용자 결정)
--    아이디 admin, 임시 비밀번호 a1234!!!! (PWD_HASH = BCrypt 강도 10 해시, 06 D-8)
--    TEMP_PWD_YN='Y'라 첫 로그인 때 새 비밀번호(SFR-007-06 규칙: 8자 이상, 영문·숫자·특수문자)로 바꾸게 한다(SFR-007-04)
--    시스템 관리자라 2단계 인증(MFA_USE_YN='Y', 이메일 인증번호, SFR-007-07)
--    이메일 admin@example.com은 예시 주소다. 실제 관리자 이메일로 바꿔서 넣는다
INSERT INTO TB_SY_USER (USER_ID, USER_NM, EMAIL_ADDR, PWD_HASH, TEMP_PWD_YN, PWD_ERR_CNT, USER_STAT_CD, MFA_USE_YN, MFA_ERR_CNT, REG_USER_ID) VALUES
('admin', '시스템 관리자', 'admin@example.com', '$2a$10$u/nzt6Qi.JlrzTbVt7RIB.Ye.erc30wfwKBTqbxIkbzurq2i4QuIe', 'Y', 0, 'USE', 'Y', 0, 'SYSTEM');

INSERT INTO TB_SY_USER_ROLE (USER_ID, ROLE_ID, REG_USER_ID) VALUES
('admin', 'ADMIN', 'SYSTEM');


-- 계산규칙 처음 값: 식 93개 + 계산 공통 원칙(식-00)
-- 실행 순서: 07_DDL_계산규칙.sql 다음
BEGIN;

INSERT INTO TB_SY_CALC_RULE (CALC_ID, SFR_ID, CALC_NM, FRML_CN, DTL_CN, USE_SCRN_CN, SORT_ORD, REG_USER_ID) VALUES
('식-00', NULL, '계산 공통 원칙', '(수식 없음, 모든 식에 적용하는 원칙)', '- 요구사항 ID: 계산식 장마다 SFR-9nn(1장 → SFR-901). 식 번호(식-nn)는 원본 그대로 둔다
- 금액은 부가세 제외(자금수지만 포함), 원 단위 정수 저장(테이블 설계 원칙 D-1). 기준값(확률·목표선·배점)은 TB_SY_OPER_STD에서 적용 시작월 이력으로 관리
- **직원구분은 그 달 값**(2026-10-31 C17): 직원구분(정규직·계약직·프리랜서·외주)에 따라 달라지는 월 계산(식-09·10·13·17·42·48·49·64)은 그 달 급여 줄의 TB_HR_MON_PAY.EMP_TYPE_CD(급여 업로드 때 개발자 풀 값을 복사)를 쓴다. 급여를 아직 안 올린 달(예정)은 TB_HR_DEV.EMP_TYPE_CD(지금 값). 그래서 직원구분을 바꿔도 지난 달 숫자는 그대로다. 예: 10월까지 정규직, 11월부터 프리랜서 → 9·10월은 정규직 식, 11월부터 프리랜서 식
- 예시 숫자는 원본의 가상값이다. 시안과 v2가 다른 부분은 원본 열린 질문(Q-식-n)을 그대로 남겼다', NULL, 0, 'SYSTEM'),
('식-01', 'SFR-901', '월 투입 MM(달력일 비례)', '월 MM = 계약 MM × (그 달 투입일수 ÷ 그 달 달력일수), 소수 둘째 자리 반올림', '- 쓰는 화면: UI-12, UI-03, UI-13, UI-19, UI-28, UI-11
- 정의: 한 사람의 한 투입 건이 그 달에 차지하는 MM이다. 투입을 시작하거나 끝낸 달만 날짜만큼 줄이고, 가운데 달은 계약한 MM을 그대로 쓴다.
- 수식: `월 MM = 계약 MM × (그 달 투입일수 ÷ 그 달 달력일수), 소수 둘째 자리 반올림`
- 예시: 10월 16일 투입 시작, 계약 1.0MM, 10월은 31일. 투입일수 16 → 16 ÷ 31 = 0.516 → 0.52MM. 계약 0.5MM이면 0.5 × 16 ÷ 31 = 0.26MM. 30일 중 23일이면 0.77MM.
- 예외·반올림: 업로드(투입 목록)에서 M/M 칸을 비우면 이 식으로 자동 계산한다. 반올림은 식-58을 따른다. 주말·공휴일은 빼지 않고 달력일만 센다.
- 관련 테이블: TB_PJ_ASGN(INPUT_BGN_DT·INPUT_END_DT·CTRT_MM) → TB_PJ_ASGN_MON(INPUT_DAY_CNT·MON_MM)
- 결정 Q-식-1(사용자 2026-10-20): 투입 시작일과 철수일을 모두 일수에 넣는다(16일 시작 → 16일 치, 20일 철수 → 20일 치).
- 계약 MM은 그 달에 적용되는 값이다(월 MM 변경 이력에서 식-02 청구 단가와 같은 방식으로 찾음). TB_PJ_ASGN.CTRT_MM은 현재 값이라 지난 달·다음 달 계산에 쓰지 않는다(C6).
- 결정 Q-식-2(사용자 2026-10-20): 계약 MM이 1.0 미만인 건도 시작·종료 달에 같은 비율로 곱해서 줄인다(시안대로).', 'UI-12, UI-03, UI-13, UI-19, UI-28, UI-11', 1, 'SYSTEM'),
('식-02', 'SFR-901', '월단위·파견(월 고정) 매출', '매출 = Σ (월 MM × 청구 단가). 무상지원·내부는 0. 금액은 건별로 원 단위 버림 후 합산', '- 쓰는 화면: UI-13, UI-07, UI-08, UI-09, UI-04, UI-03, UI-16
- 정의: 귀속월(일한 달)에 잡는 매출이다. 투입 건마다 월 MM과 청구 단가를 곱해 더한다. 무상지원 건과 내부(자체 개발) 프로젝트는 0원이다.
- 수식: `매출 = Σ (월 MM × 청구 단가). 무상지원·내부는 0. 금액은 건별로 원 단위 버림 후 합산`
- 예시: 700만 원 × 1.0MM + 650만 원 × 0.5MM = 10,250,000원. 부가세와 합계는 식-05. 시작 달 예: 700만 원 × 0.52MM = 3,640,000원.
- 예외·반올림: 대체 투입은 청구서에 계약상 인력 이름을 적고 집계는 실제 투입 인력에 붙인다(식-07). 입금 여부는 매출에 영향을 주지 않는다.
- 그 달 청구 단가: 투입 [변경 기록] 「단가 변경」 줄(TB_PJ_ASGN_HIST.ASGN_CHG_TYPE_CD=UPRC, 정정·취소 제외) 중 적용 시작월 ≤ 그 달인 가장 최근 줄의 NEW_UPRC. 없으면 배정 때 단가(「투입」 줄의 NEW_UPRC). 같은 적용 시작월이 여러 줄이면 나중 줄. TB_PJ_ASGN.BILL_UPRC는 화면 표시용 현재 값일 뿐이다(오늘이 속한 달에 적용되는 값(시작 전 투입은 시작 달 값). 변경 기록을 저장할 때와 매달 1일 배치로 다시 맞춘다(2026-10-31 C6)). 월 MM(예전 투입률)도 같은 방식(RATE 줄의 NEW_CTRT_MM, 2026-10-26 사용자 결정)
- 관련 테이블: TB_PJ_ASGN_MON(MON_MM·APLY_UPRC·SALES_AMT), TB_PJ_ASGN_HIST(NEW_UPRC·NEW_CTRT_MM)
- 결정 Q-식-3(사용자 2026-10-21): 명세서대로 건별로 원 단위 버림한 뒤 더한다. 시안의 건별 반올림은 예시 값이라 몇 원 달라도 그대로 둔다.', 'UI-13, UI-07, UI-08, UI-09, UI-04, UI-03, UI-16', 2, 'SYSTEM'),
('식-04', 'SFR-901', '턴키 매출', '그 달 매출 = 그 달에 청구일이 있는 청구 건의 공급가액 합계', '- 쓰는 화면: UI-13, UI-01, UI-07, UI-08 (UI-04 프로젝트 원가 분석의 누적 매출은 이 식이 아니라 식-18 진행률 기준, 2026-10-29 D6)
- 정의: 턴키는 청구일이 속한 달에 단계별 청구 공급가액을 매출로 잡는다. 일한 달과 무관하다. 그래서 청구 사이 달은 적자처럼 보인다.
- 수식: `그 달 매출 = 그 달에 청구일이 있는 청구 건의 공급가액 합계`
- 예시: 5억 원 계약의 3차 청구 1억 5천만 원을 10월 8일에 청구하면 10월 매출 150,000,000원. 입금이 12월이어도 10월 매출은 그대로다.
- 예외·반올림: 일한 만큼 나눈 참고 금액은 식-25(배분 기준 매출)로 따로 보여 준다. 취소된 청구(CNCL_YN=Y, 잘못 등록한 건)는 매출에 넣지 않는다(사용자 결정 2026-10-26).
- 청구 금액이 틀렸을 때(2026-10-30 D20·D21 사용자 결정): 수정세금계산서(마이너스 청구)는 v2 기능이라 v1에는 두지 않는다(청구 공급가는 음수를 받지 않음). ① 청구월이 마감 전이고 정상 입금이 없으면 청구를 취소하고 다시 등록한다. ② 청구월이 마감됐으면 매출 차액은 재무·자금 › 재무 항목 (UI-10)의 전월 조정에 항목 「매출」로 넣는다(식-59, 예: 1,000만 → 800만이면 −200만). 이미 받은 돈을 고객에게 돌려주면 그 금액은 재무 항목의 계좌이체 지출에 넣어 자금수지에 잡는다. ③ 마감된 달의 청구가 아직 입금 전이면 그 청구의 미수금(연체·미수 알림)은 v1에서 줄일 방법이 없다(v2 수정세금계산서 범위).
- 관련 테이블: TB_BL_BILL(BILL_DT·SPLY_AMT·CNCL_YN), TB_PJ_PROJ, TB_CO_PREV_ADJ
- 결정 Q-식-7·Q-13-7(사용자 2026-10-21): 명세서대로 청구일 기준으로 잡는다. 엑셀과 월별 숫자가 달라지는 것은 감수하고, 배분 기준 금액(식-25)은 참고 열로 함께 보여 준다.', 'UI-13, UI-01, UI-07, UI-08 (UI-04 프로젝트 원가 분석의 누적 매출은 이 식이 아니라 식-18 진행률 기준, 2026-10-29 D6)', 4, 'SYSTEM'),
('식-05', 'SFR-901', '청구 부가세와 합계', '부가세 = 공급가액 × 부가세율(기본 10%, 원 단위 버림). 청구 합계 = 공급가액 + 부가세', '- 쓰는 화면: UI-13, UI-21
- 정의: 청구 한 건의 부가세는 공급가액에 그 청구의 부가세율을 곱하고 원 단위는 버린다. 부가세율은 청구 창에서 %로 넣고, 기본값은 운영 기준값 VAT_RT(10%)다.
- 수식: `부가세 = 공급가액 × 부가세율(기본 10%, 원 단위 버림). 청구 합계 = 공급가액 + 부가세`
- 예시: 공급가액 12,345,678원 → 부가세 1,234,567원, 합계 13,580,245원. 공급가액 10,250,000원 → 부가세 1,025,000원, 합계 11,275,000원.
- 예외·반올림: 부가세 금액은 직접 넣지 않고 세율로만 정한다. 면세·영세율 건은 세율 0. 외주 용역비의 부가세도 같은 식이다(식-49).
- 관련 테이블: TB_BL_BILL(SPLY_AMT·VAT_RT·VAT_AMT·TOT_AMT), TB_SY_OPER_STD(VAT_RT)
- 결정 Q-13-4·Q-식-8(사용자 2026-10-21): 부가세는 금액이 아니라 세율(%)로만 입력받는다. 기본 10%, 면세·영세율은 0. 명세서의 “재무 담당이 부가세 금액을 직접 고친다”는 세율 입력으로 대신한다. 청구 창에 합계는 보여 주지 않는다.', 'UI-13, UI-21', 5, 'SYSTEM'),
('식-06', 'SFR-901', '미수금 잔액과 청구 상태', '미수금 = 청구 합계 − 입금 합계. 미수금 = 0이면 “입금 완료”', '- 쓰는 화면: UI-13, UI-16, UI-21
- 정의: 청구 한 건에 여러 번 나눠 받은 입금(취소된 입금 제외)을 더해 남은 돈을 구한다. 0이 되면 상태가 입금 완료가 되고 미수 알림이 멈춘다.
- 수식: `미수금 = 청구 합계 − 입금 합계. 미수금 = 0이면 “입금 완료”`
- 예시: 합계 11,275,000원, 1차 입금 5,000,000원, 2차 입금 4,000,000원 → 미수금 2,275,000원. 나머지를 받으면 0원, 입금 완료.
- 예외·반올림: 입금액이 미수금보다 크면 저장을 막는다(시안 동작). 입금은 매출에 영향을 주지 않고 미수금과 자금수지에만 쓴다. 취소된 청구는 미수금 0, 상태 「취소됨」(입금이 있는 청구는 취소할 수 없음, 사용자 결정 2026-10-26).
- 입금 취소(Q-13-5, 2026-10-25 사용자 결정): 취소된 입금은 입금 합계에서 빠진다. **입금일이 속한 달**이 마감되면 취소할 수 없다(청구월은 보지 않는다). 입금은 그 달 자금수지 실적이라 마감 기준도 입금일로 맞춘다.
- 관련 테이블: TB_BL_BILL(RCPT_SUM_AMT·UNPAID_AMT), TB_BL_RCPT
- 결정 Q-식-9(사용자 2026-10-21): 시안대로 입금 예정일 다음 날부터 연체로 표시한다. 알림은 7일째부터(식-89). 상태와 알림은 따로 간다.
- 결정 Q-식-10(사용자 2026-10-21): 청구 창에 입금 예정일 칸(필수)을 둔다. 기본값은 청구월 다음 달 25일이고 고칠 수 있다. 고객사별 결제 조건 칸은 두지 않는다.
- 결정 Q-식-11·Q-13-1(사용자 2026-10-21): 저장을 막기만 한다. 남는 돈은 재무 담당이 다른 청구를 골라 직접 입금 등록한다. 선수금은 따로 관리하지 않는다.', 'UI-13, UI-16, UI-21', 6, 'SYSTEM'),
('식-07', 'SFR-901', '대체 투입의 청구·집계 기준', '청구서 줄 = 계약상 인력 이름. 개인 원가·수익표의 매출·원가 = 실제 투입 인력', '- 쓰는 화면: UI-12, UI-13, UI-03
- 정의: 대체 투입은 계약상 인력을 실제 다른 사람이 대신 일하는 경우다. 청구서에는 계약상 이름을 쓰고, 회사 안 매출·원가 집계는 실제 투입 인력에게 붙인다.
- 수식: `청구서 줄 = 계약상 인력 이름. 개인 원가·수익표의 매출·원가 = 실제 투입 인력`
- 예시: 계약상 A 개발자 대신 B 개발자가 0.5MM 투입(단가 700만 원). 고객에게 보내는 청구 내역에는 A 개발자 3,500,000원, 개인 원가·수익표에는 B 개발자의 단가 3,500,000원.
- 저장(2026-10-29): 시스템의 청구는 프로젝트·월 단위 공급가 한 줄(TB_BL_BILL)만 저장하고 사람별 청구 줄은 두지 않는다(TB_BL_BILL_DTL 삭제). 청구서는 시스템이 만들지 않으므로, 청구 내역에 적을 계약상 인력은 투입 배정의 계약상 인력(TB_PJ_ASGN.CTRT_DEV_ID)을 보고 적는다.
- 예외·반올림: 단가는 투입 목록에 적은 단가를 쓴다. 무상지원은 계약 유형과 관계없이 매출 0이다.
- 관련 테이블: TB_PJ_ASGN(CTRT_DEV_ID 계약상 인력)
- 결정 Q-식-12(사용자 2026-10-20): 대체 투입의 청구 단가 기본값은 계약상 인력 단가(고객과 계약한 금액)로 채우고 고칠 수 있다. 턴키는 배분표 금액을 그대로 쓴다.
- 결정 B17(사용자 2026-10-30): 계약상 인력은 그 프로젝트에 배정된 적 있는 사람(철수 포함) 중에서 고르고 개발자 ID로 저장한다(계산에는 안 씀). 대체 투입을 저장할 때 계약상 인력(A)의 배정이 투입일에 걸쳐 있으면 확인 창에서 [같이 처리]로 A를 투입일 전날 조기 철수한다(사유는 투입 변경 사유 코드에서 고르고 메모 기본 「대체투입(B)」, 이력 남음, C7). A를 철수하지 않으면 월단위·파견은 그 기간 매출이 2명분(월 단가 × 월 MM 합)으로 잡히고 A가 대기로 빠지지 않는다.
- 같이 처리 후 결과(시안 점검 2026-10-30): A는 철수 다음 날부터 MM 0·대기(대기 원가로 이동), B는 투입일부터 투입·가동. 월단위·파견 프로젝트 매출은 그대로이고 B 매출은 A 단가 × B 월 MM. 투입 달은 날짜 비율로 나뉨(예: 10/16 시작이면 A 0.48MM, B 0.52MM).
- 턴키: 프로젝트 매출은 배분표 합계라 그대로지만 개인 매출은 배분표 사람 줄로 정해지므로, 배분표에서 A 줄 금액을 B 줄로 옮겨 다시 올려야 한다(달마다 합계는 그대로, 투입 달은 회사가 정한 비율로 나눔. 예: 10월 534만 원 → A 2,583,870원 · B 2,756,130원, 11월부터 A 금액을 B로). 시스템은 배분표를 자동으로 바꾸지 않고, 확인 창과 저장 안내에 「턴키 배분표는 다시 옮겨 올려 주셔야 합니다. 그 전까지는 배분 불일치 경고가 뜹니다.」를 보인다(계산에 쓰는 배분표에 A 줄이 있을 때만). 다시 올리기 전까지는 식-55 배정 불일치 점검에 A 「배정 없음」, B 「배분표 없음」이 뜨고 B 매출은 「그 달 원가 × 계약금액 ÷ 예산」으로 계산된다.', 'UI-12, UI-13, UI-03', 7, 'SYSTEM'),
('식-08', 'SFR-902', '직위 수당', '수당 = 직위 기준값(팀장·그룹장 정액). 그 밖의 직위는 0', '- 쓰는 화면: UI-02, UI-03
- 정의: 정규직·계약직 중 팀장과 그룹장에게 붙는 월 정액 수당이다. 직위별 금액은 기준값에 두고 바꿀 수 있다. 팀원은 0원이다.
- 수식: `수당 = 직위 기준값(팀장·그룹장 정액). 그 밖의 직위는 0`
- 예시: 시안 값: 팀장 200,000원, 그룹장 400,000원, 팀원 0원. 월 급여 400만 원인 팀장의 급여+수당 = 4,200,000원.
- 예외·반올림: 프리랜서·외주에는 수당이 없다. 수당은 그 달 개발자 풀의 직위(TB_HR_DEV)로 정한다. 급여 업로드 양식(B.1)에는 이 칸이 없고, 급여를 올릴 때 시스템이 공통코드로 계산해 저장한다. 공통코드를 고치면 마감 안 된 달은 다시 계산하고, 마감한 달은 저장된 값 그대로다(2026-10-31 C4).
- 관련 테이블: TB_SY_CODE(수당코드), TB_HR_MON_PAY.ALWNC_AMT, TB_HR_DEV
- 열린 질문 Q-식-13 (해결, 2026-10): 직위별 수당 금액은 얼마인가? → 공통코드(UI-27)에 「수당코드」 그룹을 만들어 ALLOW_TEAM(팀장 수당, 월, 기본 200,000원)과 ALLOW_GROUP(그룹장 수당, 월, 기본 400,000원) 코드로 관리한다. 값은 화면에서 바로 고칠 수 있고, 고치면 전체 원가 계산에 바로 적용된다.', 'UI-02, UI-03', 8, 'SYSTEM'),
('식-09', 'SFR-902', '퇴직금 적립', '퇴직금 적립 = (월 급여 + 수당) × 퇴직금 적립률(공통코드 SEV_RATE, 기본 8.33% ≒ 1÷12), 정규직만', '- 쓰는 화면: UI-03, UI-04, UI-08
- 정의: 정규직에게만 매달 원가로 쌓는 퇴직금이다. 실제 현금은 지급·납입한 달에만 자금수지에 잡는다(재무 항목 계좌이체 지출 표에 입력, 2026-10-28).
- 수식: `퇴직금 적립 = (월 급여 + 수당) × 퇴직금 적립률(공통코드 SEV_RATE, 기본 8.33% ≒ 1÷12), 정규직만`
- 예시: 월 급여 400만 원 + 수당 20만 원 = 420만 원 → 4,200,000 × 8.33% = 349,860원. 계약직은 0원.
- 예외·반올림: 원 단위 버림. 적립률은 시스템 관리 › 공통코드(UI-27)의 수당코드 그룹에서 고친다. 급여 업로드 양식(B.1)에는 이 칸이 없고, 급여를 올릴 때 시스템이 공통코드로 계산해 저장한다. 공통코드를 고치면 마감 안 된 달은 다시 계산하고, 마감한 달은 저장된 값 그대로다(2026-10-31 C4). 영업 파트 이익금 계산(식-42)은 수당을 빼고 급여 ÷ 12를 쓴다.
- 관련 테이블: TB_HR_MON_PAY.RETIRE_RSV_AMT, TB_SY_CODE(퇴직금 적립률)
- 결정 Q-식-14·Q-식-38(사용자 2026-10-21): 둘 다 명세서대로 둔다. 원가 화면은 (급여+수당) × 퇴직금 적립률(회사가 쓰는 돈), 영업 파트 이익금(식-42)은 급여 ÷ 12(파트 정산 규칙). 목적이 다른 숫자라 달라도 되고, 각 화면 “계산 방법”에 어느 식인지 적는다.
- 결정 B3(사용자 2026-10-30): 원가 화면 퇴직금은 ÷ 12가 아니라 공통코드 퇴직금 적립률(기본 8.33%)을 곱한다(명세서 식-09·시안과 같게). 파트 이익금(식-42)은 급여 ÷ 12 그대로.
- 결정 Q-식-15(사용자 2026-10-21): 명세서대로 원 단위 버림(식-57). 시안의 10원 반올림은 예시 값.', 'UI-03, UI-04, UI-08', 9, 'SYSTEM'),
('식-10', 'SFR-902', '개인 월 인건비(개인별 원가)', '정규직 = 급여 + 수당 + 4대보험(회사) + 퇴직금 적립. 계약직 = 급여 + 수당 + 4대보험(회사). 프리랜서 = 그 달 실제 지급액(원천세 차감 전). 외주 = 협력사 공급가액', '- 쓰는 화면: UI-03, UI-04, UI-02
- 정의: 한 사람이 한 달에 회사에 드는 사람 값이다. 직원 구분마다 구성이 다르다. 엑셀의 Q열 “개인별 원가”에 해당한다.
- 수식: `정규직 = 급여 + 수당 + 4대보험(회사) + 퇴직금 적립. 계약직 = 급여 + 수당 + 4대보험(회사). 프리랜서 = 그 달 실제 지급액(원천세 차감 전). 외주 = 협력사 공급가액`
- 예시: 정규직 급여 400만 원, 수당 20만 원, 4대보험(회사) 42만 원, 퇴직금 35만 원 → 4,970,000원. 프리랜서 지급액 500만 원 → 500만 원. 외주 공급가액 800만 원 → 800만 원(부가세 별도).
- 예외·반올림: 4대보험(회사)은 급여 업로드 값이다(국민연금·건강·장기요양 회사분 + 고용 1.15% + 산재 1%). 프리랜서·외주는 4대보험이 없다. 휴직자는 식-14를 본다.
- 관련 테이블: TB_HR_MON_PAY(PAY_AMT·ALWNC_AMT·INSR_CMPNY_AMT·RETIRE_RSV_AMT·VNDR_SPLY_AMT·EMP_TYPE_CD 그 달 직원구분)
- 결정 Q-식-16(사용자 2026-10-21): 명세서대로 외주 원가는 공급가액(부가세 제외). 부가세는 돌려받는 돈이라 원가가 아니고 자금수지 용역비에서 지급만 잡는다(2026-10-28 지급 관리 삭제 전에는 지급 관리).
- 열린 질문 Q-식-17 (해결, 2026-10): 시안은 4대보험(회사)을 급여의 8~10%로 임의 계산했다. → 사람마다 급여의 8~10% 사이에서 임의로 계산하던 것을 없애고, 공통코드(UI-27, INS_RATE 「4대보험(회사) 부담률」) 하나의 고정 비율(기본값 9.4%)로 모든 인력에 똑같이 적용하도록 고쳤다. 실제 운영에서 급여 업로드 값으로 바뀌는 방안은 그대로 열려 있다(별도 과제).', 'UI-03, UI-04, UI-02', 10, 'SYSTEM'),
('식-11', 'SFR-902', '직접 경비(팀비·프로젝트경비 등)', '팀비·경비 몫 = (그 프로젝트 팀비(월) + 프로젝트경비(월)) × 그 사람의 그 프로젝트 MM. 프로젝트 직접 경비 = 투입 자사 인력 팀비·경비 몫의 합 + 그 달 추가 직접 경비 합계', '- 쓰는 화면: UI-03, UI-04, UI-08
- 정의: 인건비와 따로 한 번만 더하는 경비다. 팀비(회식비)와 프로젝트경비(물품 구매비)는 **자사 인력 1인당 월 정액**이다(2026-10-28 사용자 결정, 원본 명세서 본문과 같음). 그 프로젝트에 투입된 자사 인력(외주 제외) 한 사람마다 그 프로젝트 MM만큼 붙는다(1.0MM이면 전액, 0.5MM이면 절반).
- 수식: `팀비·경비 몫 = (그 프로젝트 팀비(월) + 프로젝트경비(월)) × 그 사람의 그 프로젝트 MM. 프로젝트 직접 경비 = 투입 자사 인력 팀비·경비 몫의 합 + 그 달 추가 직접 경비 합계`
- 추가 직접 경비(Q-식-19): 그 달 프로젝트의 직접 경비 = (팀비(월) + 프로젝트경비(월)) × 자사 인력 MM 합 + 그 달 추가 직접 경비 합계. 추가 직접 경비는 사람에게 나누지 않는다(2026-10-31 사용자 결정). 프로젝트 원가 분석(UI-04)·영업이익표(UI-08)에만 들어가고 개인 원가·수익표(UI-03)에는 들어가지 않는다. (이전 2026-10-28: 투입 자사 인력 MM 비율로 나눔)
- ~~공용 경비를 나눌 사람이 없는 달(2026-10-25): 그 달 그 프로젝트에 투입된 자사 인력이 없으면(외주만) 공용 경비는 저장할 수 없다.~~ 2026-10-31 삭제: 사람에게 나누지 않으므로 외주만 투입된 달에도 넣을 수 있다.
- 추가 직접 경비의 돈(2026-10-28): 사람·받는 곳·부가세 칸을 없앴다. 모두 회사가 쓴 돈이고 어느 프로젝트에서 썼는지만 남긴다. 원가는 그 달 프로젝트 원가에만 더하고(2026-10-31), 지급 관리 줄은 만들지 않는다(지급 관리는 2026-10-28 삭제). 실제로 나간 돈은 법인카드로 냈으면 재무 항목 법인카드 표의 카드 대금, 계좌이체로 냈으면 재무 항목 계좌이체 표로 자금수지 제경비에 들어간다(2026-10-28 사용자 결정). 예: 늘봄카드 10월 회식비 30만 → 늘봄카드 10월 원가에 300,000원(사람에게 나누지 않음, 2026-10-31). (이전 2026-10-25: 공용은 받는 곳에 「제경비(기타)」 지급 줄, 부가세는 지급액에만)
- ~~사람을 고른 추가 직접 경비(2026-10-25): 그 사람 원가에만 더한다.~~ 2026-10-28에 사람 칸을 없앴다. 모든 추가 직접 경비는 공용(회사 지급)이다(2026-10-31부터 사람에게 나누지 않음).
- (2026-10-28 사용자 결정: 아래 경비 정산 칸은 뺐다. 개인 영수증 실비는 급여와 따로 시스템 밖에서 일괄 처리하고, 그 달 합계만 재무 항목 판관비로 넣는다.) ~~주의(2026-10-25): 이런 경비는 영수증으로 정산해 다음 달 급여에 같이 지급되는 경우가 많다. 급여 업로드(B.1)의 급여·수당에 정산액이 섞이면 개인 원가에 인건비와 직접 경비로 두 번 잡히므로, 정산액은 급여 업로드의 「경비 정산」 칸에 따로 넣는다. 이 칸은 원가에 안 들어가고 실지급액(현금)에만 더한다(식-17). 재무가 사람별로 모으기 쉽게 [급여 업로드] 창에 전달 추가 직접 경비를 사람별로 보여 주고(SFR-102-11), 재무가 영수증과 맞춰 본 금액을 칸에 적어 올린다. 지급은 올린 값 기준이다.~~
- 예시: 시안 값: 팀비 50,000원·프로젝트경비 50,000원(1인당 월), 자사 인력 6명이 1.0MM씩 투입 → 1인당 팀비 50,000원 + 경비 50,000원 = 월 100,000원, 프로젝트 직접 경비 월 600,000원. 0.5MM만 들어간 사람은 50,000원. 1년이면 1.0MM인 사람 1,200,000원. 금액을 바꾸려면 프로젝트의 「팀비(월)」·「프로젝트경비(월)」을 고친다. 시안 v1.6 10월: 회사 전체 직접 경비 899,982원 → 9,450,000원, 영업이익 81,555,701원 → 73,005,683원.
- 예외·반올림: 팀비·경비는 투입한 MM만큼만 붙는다. MM 합계가 1.0 미만이어도 남는 몫을 대기 원가로 보내지 않는다(투입이 0이면 팀비·경비도 0). 추가 직접 경비는 사람에게 나누지 않으므로 반올림이 없다(2026-10-31).
- 관련 테이블: TB_PJ_PROJ(TEAM_EXPS_AMT·PROJ_EXPS_AMT), TB_PJ_DRCT_EXPS, TB_CO_MON_COST(TEAM_EXPS_AMT·PROJ_EXPS_AMT·DRCT_EXPS_AMT)
- 마감 저장(2026-10-28 사용자 결정): 월 마감 때 TB_CO_MON_COST 한 줄(사람×프로젝트)에 두 몫을 따로 저장한다. ① TEAM_EXPS_AMT = 그때 프로젝트 팀비(월) × 그 줄 MM ② PROJ_EXPS_AMT = 그때 프로젝트경비(월) × 그 줄 MM. 각각 원 단위 버림. DRCT_EXPS_AMT = ① + ②. 외주·대기(IDLE)·간접 줄은 모두 0. 추가 직접 경비는 사람 줄에 저장하지 않고(~~③ ADD_EXPS_AMT~~ 2026-10-31 삭제), 프로젝트 원가 분석·영업이익표가 그 달 TB_PJ_DRCT_EXPS 합계를 바로 더한다(마감한 달은 입력·삭제가 막혀 값이 바뀌지 않으므로 따로 저장할 필요 없음). 이유: 프로젝트에는 팀비·경비의 지금 값만 있어, 합계 한 칸이면 금액을 바꾼 뒤 지난 달을 나눌 수 없다. 마감 안 한 달은 지금처럼 그때그때 계산하고, 마감한 달은 이 칸을 읽는다(UI-03 「팀비」「프로젝트경비」 열).
- 예시(마감): 9월 홍길동이 늘봄카드 1.0MM, 팀비 50,000·프로젝트경비 50,000 → ① 50,000 ② 50,000, DRCT_EXPS_AMT 100,000. 그 달 늘봄카드 추가 직접 경비 300,000원은 홍길동 줄에 없고 늘봄카드 9월 프로젝트 원가에만 더해진다. 10월에 팀비를 100,000으로 고쳐도 9월 줄은 그대로라 UI-03 9월 누계의 팀비는 50,000.
- 열린 질문 Q-식-18 (해결, 2026-10): 팀비·프로젝트경비는 이제 프로젝트별 실제 입력값이다(UI-01 등록·수정 화면에 「팀비(월)」·「프로젝트경비(월)」 칸이 생겼다). 기본값은 공통코드(UI-27, TEAM_FEE·PEXP_FEE)의 50,000원이고, 프로젝트마다 실제 금액으로 고쳐 입력할 수 있다. 1인당 배분은 그 프로젝트에 투입된 자사 인력(외주 제외)의 투입 MM 비중으로 나눈다(식 변경: 1인당 배분 = 프로젝트 팀비(또는 경비) × 그 사람의 그 프로젝트 투입 MM ÷ 그 프로젝트 전체 자사 인력 투입 MM 합).
- 결정(2026-10-28 사용자): 팀비·프로젝트경비는 자사 인력 **1인당 월 정액**이다(팀비 = 회식비, 프로젝트경비 = 물품 구매비). 위 Q-식-18의 「프로젝트 금액을 MM 비중으로 나눔」을 바꾼다. 원본 명세서 식-11 본문 「자사 인력 1인당 정액」과 같다.
- 결정 Q-식-19(사용자 2026-10-21): 출장비처럼 그 달에만 드는 비용은 프로젝트 상세 [기본 정보] 탭의 “추가 직접 경비”(월·항목·금액)로 넣는다. 그 달 직접 경비에만 더해진다(~~팀비·프로젝트경비와 같이 투입 자사 인력의 MM 비율로 나뉜다~~ 2026-10-31: 사람에게 나누지 않음). 프로젝트경비(월)는 매달 같은 금액이라 한 번 생기는 비용을 넣으면 다음 달에도 더해지므로 쓰지 않는다.', 'UI-03, UI-04, UI-08', 11, 'SYSTEM'),
('식-12', 'SFR-902', '개인 원가합계', '원가합계 = 개인 월 인건비 + 그 사람의 직접 경비', '- 쓰는 화면: UI-03
- 정의: 개인 원가·수익표에 보여 주는 한 사람의 월 원가다. 엑셀의 T열과 같다. 영업이익표는 이 값이 아니라 식-13·식-14·식-15로 계산해서 이중 계산을 막는다.
- 수식: `원가합계 = 개인 월 인건비 + 그 사람의 직접 경비`
- 예시: 정규직 인건비 4,970,000원 + 직접 경비 100,000원(1.0MM 투입, 팀비 50,000 + 경비 50,000) = 5,070,000원.
- 예외·반올림: 표시용 값이다. 회사 전체 손익은 배분 원가·대기 원가·직접 경비로 따로 합산한다.
- 관련 테이블: TB_CO_MON_COST', 'UI-03', 12, 'SYSTEM'),
('식-13', 'SFR-902', '프로젝트 배분 원가', '배분 원가 = 개인 월 인건비 × (그 프로젝트 MM ÷ 분모). 분모 = max(MM 합계, 1.0). 프리랜서·외주는 분모 = MM 합계', '- 쓰는 화면: UI-03, UI-04, UI-08, UI-07
- 정의: 한 사람의 월 인건비를 투입 프로젝트에 MM 비율로 나눈 몫이다. 분모는 그 달 MM 합계와 1.0 중 큰 값이다. 무상지원 MM도 나눈다.
- 수식: `배분 원가 = 개인 월 인건비 × (그 프로젝트 MM ÷ 분모). 분모 = max(MM 합계, 1.0). 프리랜서·외주는 분모 = MM 합계`
- 예시: 월 인건비 4,970,000원, A 1.0MM, B 0.5MM(합계 1.5) → A 3,313,333원, B 1,656,666원. 합이 1원 모자라 마지막 줄 B에 1원을 더해 1,656,667원. 외주 800만 원, 0.5MM+0.25MM → 5,333,333원, 2,666,667원.
- 예외·반올림: 나눠 생긴 끝자리는 마지막 줄에 모아 합계를 맞춘다(식-57). 프리랜서·외주는 지급액 전부를 투입 프로젝트에 나눈다.
- 관련 테이블: TB_CO_MON_COST.LABOR_ALLOC_AMT(COST_CLS_CD=ALLOC)
- 줄 단위(2026-10-28 사용자 결정): 「그 프로젝트 MM」은 투입 1건의 MM으로 본다. 같은 사람이 같은 프로젝트에 투입이 2건이면 2줄로 나눠 계산·저장한다(TB_CO_MON_COST PK에 ASGN_ID). 줄 순서(끝자리 보정의 마지막 줄)는 프로젝트ID·투입ID 순. 예: 월 인건비 4,970,000원, 가온은행 유상 0.5(A-0101) + 무상지원 0.5(A-0102) → 2,485,000원 / 2,485,000원 두 줄.
- 결정 Q-식-20(사용자 2026-10-21): 명세서대로 버림 + 마지막 줄 보정(식-57).', 'UI-03, UI-04, UI-08, UI-07', 13, 'SYSTEM'),
('식-14', 'SFR-902', '대기 원가', '대기 원가 = 개인 월 인건비 × (1.0 − MM 합계), MM 합계가 1.0 미만일 때. 투입이 전혀 없으면 개인 월 인건비 전체(직접 경비는 0, 식-11)', '- 쓰는 화면: UI-03, UI-08, UI-09, UI-07
- 정의: 정규직·계약직이 투입되지 않은 몫의 인건비다. 매출원가에 넣는다. 프리랜서·외주는 대기 원가가 없다(지급액 전부를 투입 프로젝트에 나눔).
- 수식: `대기 원가 = 개인 월 인건비 × (1.0 − MM 합계), MM 합계가 1.0 미만일 때. 투입이 전혀 없으면 개인 월 인건비 전체(직접 경비는 0, 식-11)`
- 예시: 정규직 월 인건비 4,970,000원, MM 합계 0.6 → 프로젝트 2,982,000원, 대기 원가 1,988,000원. 투입 0이면 직접 경비도 0이라 4,970,000원 전부 대기 원가.
- 예외·반올림: MM 합계가 1.0 이상이면 대기 원가는 0이다. 프리랜서는 지급액 500만 원, MM 0.5여도 500만 원 전부 투입 프로젝트로 가고 대기 원가는 0원이다.
- 관련 테이블: TB_CO_MON_COST(PROJ_ID=IDLE, COST_CLS_CD=IDLE)
- 결정(퇴사, 2026-10-27 사용자 결정): 퇴사한 다음 달부터는 개인 월 인건비·대기 원가를 잡지 않는다. 퇴사한 달까지는 그 달 급여 업로드 값 그대로다. 개인 원가·수익표의 연 누계도 퇴사한 달까지만 더하고, 그 해 이전에 퇴사한 사람은 목록에 나오지 않는다.
- 결정 Q-식-21(사용자 2026-10-21): 개인 원가·수익표에서는 휴직자를 빼고 총 인원에만 넣는다(명세서). 급여 업로드에 휴직자 금액이 있으면 대기 원가로 넣어 회사 손익에서 빠지지 않게 한다. 무급 휴직은 0원.', 'UI-03, UI-08, UI-09, UI-07', 14, 'SYSTEM'),
('식-15', 'SFR-902', '간접 인력·내부 프로젝트 원가의 분류', '판관비로 가는 돈 = 간접 인력 인건비(간접 인건비) + 내부 프로젝트에 배분된 원가(연구개발비). 나머지는 매출원가', '- 쓰는 화면: UI-08, UI-09, UI-07, UI-03
- 정의: 간접 인력의 인건비와 내부(자체 개발) 프로젝트에 배분된 원가는 매출원가가 아니라 판관비로 보낸다. 내부 프로젝트는 “연구개발비”라는 줄이다.
- 수식: `판관비로 가는 돈 = 간접 인력 인건비(간접 인건비) + 내부 프로젝트에 배분된 원가(연구개발비). 나머지는 매출원가`
- 예시: 정규직 한 명이 내부 0.5MM + 월단위 0.5MM. 내부 몫 = 2,485,000원 + 경비 50,000원 = 2,535,000원 → 연구개발비(판관비). 나머지 같은 금액은 배분 원가.
- 예외·반올림: 개인 원가·수익표(UI-03)의 개인 영업이익은 이 분류와 달리 “단가 − 원가합계”로 보여 준다. 그래서 내부 프로젝트만 하는 사람과 대기 인력은 −원가합계가 된다.
- 관련 테이블: TB_CO_MON_COST.COST_CLS_CD(INDRCT·RND), TB_CO_MON_PL
- 결정 Q-식-22(사용자 2026-10-20): 직접/간접은 엑셀이 아니라 개발자 풀(직접/간접 칸, Q-02-5)에서 정하고, 급여 업로드 때 그 값을 TB_HR_MON_PAY.DRCT_CLS_CD로 복사한다.', 'UI-08, UI-09, UI-07, UI-03', 15, 'SYSTEM'),
('식-16', 'SFR-902', '수주예정 프로젝트 선투입 원가', '수주예정 프로젝트 원가 = 선투입 인력의 배분 원가. 실주 시 → 대기 원가로 이동', '- 쓰는 화면: UI-01, UI-12, UI-04, UI-16
- 정의: 계약 전에 먼저 투입된 인력의 원가는 그 수주예정 프로젝트에 쌓는다. 실주(수주 실패)하면 같은 금액을 대기 원가로 옮긴다. 실주는 프로젝트 상세 [⋯] → 「실주 처리」(SFR-101-13)로 정하고, 그때 상태가 「실주」(PROJ_STAT_CD=LOSE)가 된다. 상태가 실주인 프로젝트에 쌓인 선투입 원가는 대기 원가(COST_CLS_CD=IDLE)로 잡는다. 화면에는 “미계약 선투입” 경고가 뜬다.
- 수식: `수주예정 프로젝트 원가 = 선투입 인력의 배분 원가. 실주 시 → 대기 원가로 이동`
- 예시: 월 인건비 4,970,000원인 사람이 0.5MM 선투입 → 수주예정 프로젝트에 2,485,000원이 쌓인다. 실주하면 그 2,485,000원이 대기 원가가 된다.
- 예외·반올림: 수주예정 프로젝트는 계약 전이라 선투입 원가가 있어도 누적 매출을 0으로 본다(식-18 진행률을 쓰지 않음). 예상 최종 마진율도 계산하지 않고(식-20·21은 진행 중 턴키만), 수주돼 상태가 「진행」이 되면 그때부터 계산한다(옛 문장 「청구가 없어」·「배분표가 확정된 뒤」 정리, 2026-10-29).
- 관련 테이블: TB_CO_MON_COST(COST_CLS_CD=PRE·IDLE), TB_PJ_PROJ.PROJ_STAT_CD
- 결정 Q-식-23(사용자 2026-10-20): 실주로 이미 마감된 달의 원가가 바뀌면 그 달은 그대로 두고 “전월 조정”(식-59)으로 처리한다.
- 결정 B1(사용자 2026-10-30): 명세서대로 실주하면 선투입 원가를 대기 원가로 옮긴다. 실주 처리 때 투입 중인 사람은 오늘 날짜로 조기 철수하고, 시작 전 배정은 배정 취소한다(SFR-101-13). 그래서 실주한 날 다음부터는 이 프로젝트에 새 원가가 쌓이지 않는다.', 'UI-01, UI-12, UI-04, UI-16', 16, 'SYSTEM'),
('식-17', 'SFR-902', '본인 공제와 실지급액(참고 열)', '원천세 = (급여 + 수당) × 3.3%, 계약직·프리랜서만. 실지급액 = 급여 + 수당 − 4대보험(본인) − 원천세 − 근로소득세', '- 쓰는 화면: UI-03
- 정의: 급여에서 떼는 돈을 보여 주는 참고 열이다. 회사 원가를 바꾸지 않는다. 4대보험(본인)과 근로소득세는 급여 업로드 값이고, 비어 있으면 0으로 본다(프리랜서·외주는 4대보험이 원래 없음, 2026-10-30 E4). 이 값은 자금수지표 제경비(다음 달, 식-48)에도 쓴다.
- 결정(2026-10-28 사용자): 개인 영수증 실비(택시비·식대 등)는 급여와 따로 처리한다. 급여 업로드 급여에 섞지 않고, 시스템 밖에서 일괄 정산한 뒤 그 달 합계만 재무 항목 판관비로 넣는다. 그래서 수식에서 「경비 정산」을 뺐다(시안·문서 반영 2026-10-28).
- 수식: `원천세 = (급여 + 수당) × 3.3%, 계약직·프리랜서만. 실지급액 = 급여 + 수당 − 4대보험(본인) − 원천세 − 근로소득세`
- 급여 업로드 양식(B.1)에는 원천세·실지급액 칸이 없다. 급여를 올릴 때 이 식으로 계산해 저장한다(수당·퇴직금 적립과 같은 방식, 2026-10-31 사용자 결정).
- 예시: 계약직 급여 400만 원 + 수당 20만 원 = 420만 원. 원천세 138,600원, 4대보험(본인) 350,000원(가상) → 실지급액 3,711,400원.
- ~~경비 정산(2026-10-25): 영수증으로 정산해 급여와 같이 주는 출장비·숙박비·교통비 등(세금 없음). 급여 업로드 「경비 정산」 칸 값이다(시스템이 자동으로 채우지 않음. 업로드 창의 사람별 목록을 보고 재무가 확인한 금액, SFR-102-11). 원가에는 넣지 않는다(쓴 달에 프로젝트 추가 직접 경비로 이미 잡힘, 식-11). 예: 월 실지급 300만 원, 10월 교통비 3만 원 × 2 → 11월 경비 정산 6만 원 → 11월 실지급액 306만 원. 원가는 10월 직접 경비 6만 원, 현금은 11월 인건비 지출 306만 원(자금수지표).~~ (2026-10-28 삭제)
- 예외·반올림: 원 단위 버림. 기본으로는 숨겨 두고 필요할 때 켠다(시안 동작).
- 관련 테이블: TB_HR_MON_PAY(INSR_SELF_AMT·WTHLD_TAX_AMT·INCM_TAX_AMT·REAL_PAY_AMT)
- 결정 Q-식-24(사용자 2026-10-21): 한 번만 뺀다. 엑셀의 6명 이중 공제는 수식 오류이며, 이관 검증 때 “6명 실지급액은 엑셀과 다른 것이 정상”으로 적는다.
- 결정 Q-식-25(사용자 2026-10-21): 4대보험(본인)·근로소득세는 급여 업로드 값을 쓴다. 시안의 9.4%는 예시.', 'UI-03', 17, 'SYSTEM'),
('식-18', 'SFR-903', '턴키 누적 마진율', '누적 매출(턴키) = 계약금액 × 진행률. 진행률 = min(1, 원가 누계 ÷ 예산), 상태가 “종료”인 건은 진행률 100%. 누적 마진율 = (누적 매출 − 원가 누계) ÷ 누적 매출 × 100', '- 쓰는 화면: UI-04, UI-01, UI-16
- 정의: 지금까지 인식한 매출(계약금액 × 진행률)에서 지금까지 쓴 원가를 뺀 이익이 그 매출의 몇 %인지 본다. 진행률은 원가 누계를 예산으로 나눈 값이고, 1을 넘으면 1로 본다. 상태가 “종료”인 건은 진행률을 100%로 본다. 이렇게 하면 원가를 쓴 만큼(발생주의) 매출도 같이 인식되어 회계적으로 맞는다. 원가 누계는 배분 원가 누계와 직접 경비 누계의 합이다.
- 수식: `누적 매출(턴키) = 계약금액 × 진행률. 진행률 = min(1, 원가 누계 ÷ 예산), 상태가 “종료”인 건은 진행률 100%. 누적 마진율 = (누적 매출 − 원가 누계) ÷ 누적 매출 × 100`
- 예시: 계약금액 5억 원, 예산 4억 원, 원가 누계 1.3억 원 → 진행률 32.5%, 누적 매출 162,500,000원 → (1.625 − 1.3) ÷ 1.625 = 20.0%.
- 예외·반올림: 원가 누계가 0이면(수주예정 등) 누적 매출도 0이라 칸을 비운다(–). 내부(자체 개발)는 매출이 없어 마진 칸을 비운다.
- 관련 테이블: TB_PJ_PROJ, TB_CO_MON_COST
- 결정 Q-식-26(사용자 2026-10-21): 명세서 결정(2026-10)대로 청구 누계가 아니라 진행률(원가 누계 ÷ 예산)로 누적 매출을 잡는다. 명세서 v1.9 UI-04 “예외·주의”의 “턴키의 누적 매출은 청구한 금액의 누계” 문장은 옛 내용이라 다음 판에서 지운다.', 'UI-04, UI-01, UI-16', 18, 'SYSTEM'),
('식-19', 'SFR-903', '목표 원가(예산)와 예산 소진율', '예산(목표 원가) = 프로젝트별 입력값(기본값 현재 계약금액 × (1 − 목표 마진율), 목표 마진율 20%면 80%, 수정 가능). 예산 소진율 = 원가 누계 ÷ 예산 × 100', '- 쓰는 화면: UI-04, UI-01, UI-31, UI-16
- 정의: 예산(목표 원가)은 프로젝트마다 직접 입력받는 값이다. 계약금액을 입력하면 기본값으로 계약금액 × (1 − 목표 마진율)(목표 마진율 20%면 80%)이 자동으로 채워지고, 필요하면 프로젝트별로 고칠 수 있다. 지금까지 쓴 원가가 이 예산의 몇 %인지가 예산 소진율이다. 80% 이상이면 주황, 100% 이상이면 “예산 초과” 경보다.
- 수식: `예산(목표 원가) = 프로젝트별 입력값(기본값 현재 계약금액 × (1 − 목표 마진율), 목표 마진율 20%면 80%, 수정 가능). 예산 소진율 = 원가 누계 ÷ 예산 × 100`
- 예시: 계약금액 5억 원 → 예산 기본값 400,000,000원(수정 가능). 원가 누계 1.3억 원 → 소진율 32.5%.
- 예외·반올림: 기본 비율은 따로 두지 않고 운영 기준값 목표 마진율(TGT_MRGN_RT, 기본 20%)에서 1 − 목표 마진율로 계산한다(식-56과 같은 식). 목표 마진율을 고치면 그 뒤 새로 채우는 예산부터 바뀌고 이미 저장된 예산은 그대로다. 계약금액이 바뀌면 예산은 다시 계약금액 × (1 − 목표 마진율)로 자동 채워지지만, 사람이 직접 고친 뒤에는 자동으로 덮어쓰지 않는다. 내부 프로젝트는 예산이 없어 칸을 비운다.
- 관련 테이블: TB_PJ_PROJ.BDGT_AMT, TB_CO_MON_COST, TB_SY_OPER_STD(목표 마진율)
- 결정 C2(사용자 2026-10-31): 예산 기본값은 계약금액 × (1 − 목표 마진율) 하나로 통일. 따로 있던 운영 기준값 「예산 기본 비율(BDGT_DEFAULT_RT) 80%」는 지운다(명세서에 없던 값, 목표 마진율과 따로 바뀌면 식-19·식-56이 어긋남).
- 결정 Q-식-27(사용자 2026-10-21): 명세서 결정(2026-10, Q-01-3)대로 사람이 고친 예산을 소진율·신호등에 쓴다.', 'UI-04, UI-01, UI-31, UI-16', 19, 'SYSTEM'),
('식-20', 'SFR-903', '남은 기간 예상 원가', '예상 원가 = Σ (사람별 남은 달 MM 합 × 그 사람 월 인건비)', '- 쓰는 화면: UI-04, UI-01
- 정의: 턴키가 끝날 때까지 앞으로 더 쓸 원가의 예상이다. 투입 배정(UI-12)에 잡힌 사람마다 남은 달의 MM을 더하고 그 사람의 월 인건비를 곱해 더한다. 배분표가 있든 없든 같은 식이다(2026-10-30 B28 사용자 결정).
- 수식: `예상 원가 = Σ (사람별 남은 달 MM 합 × 그 사람 월 인건비)`
  - 남은 달: 기준 달(원가 분석은 고른 달, 그 밖은 이번 달) 다음 달부터 그 배정의 철수일까지. 철수일이 프로젝트 계약 종료일보다 늦으면 계약 종료일까지만 센다.
  - 달마다 MM: 식-01(월 MM × 그 달 투입일수 ÷ 달력일수, 소수 둘째 자리 반올림). [변경 기록]의 「월 MM 변경」은 적용 시작월부터 반영. 배정 취소된 줄은 뺀다. 같은 사람의 배정이 여러 줄이면 MM을 합쳐 한 사람으로 센다.
  - 월 인건비(있는 것부터 순서대로): ① 최근 월 인건비(그 사람의 가장 최근 달 월 인건비 = 급여·수당·4대보험·퇴직금, 정규직·계약직이거나 기준 달에 투입이 있으면 있음) ② 없으면 개발자 풀(UI-02)의 월 급여 지급액(TB_HR_DEV_PRC.MON_PAY_AMT) ③ 그것도 없으면 배분표(가장 나중 단계)의 그 사람 남은 달 금액 합 ÷ 남은 달 MM 합 × 예산 ÷ 현재 계약금액(배분표 금액은 받는 돈이라 이익을 빼려고 예산 비율을 곱함, 식-41을 거꾸로 쓴 것. 예산이 비면 계약금액 × 80%) ④ 다 없으면 0으로 세고 화면에 「단가 없음」을 표시한다.
  - 금액: 사람별로 MM 합 × 월 인건비를 원 단위 버림(식-57) 후 더한다.
- 예시: 지금 5월, 12월 종료, 계약 10억, 예산 8억. 김개발(04-01~12-31, 1.0MM, 최근 인건비 400만) 6~12월 7.0MM × 400만 = 2,800만. 이영희(07-01~12-31, 1.0MM, 아직 시작 전, 개발자 풀 급여 350만) 7~12월 6.0MM × 350만 = 2,100만(6월은 0). 박신규(07-15~12-31, 1.0MM, 급여 없음, 배분표 7월 275만·8~12월 500만씩) 7월 0.55 + 8~12월 5.0 = 5.55MM, 월 인건비 = 2,775만 ÷ 5.55 × 8억 ÷ 10억 = 400만 → 2,220만. 합계 7,120만.
- 예외·반올림: 진행 중인 턴키만 계산하고, 수주예정·종료는 계산하지 않는다. 아직 배정하지 않은 사람·달은 들어가지 않는다(화면 계산 방법에 「투입 배정 기준」으로 적음). 배정을 바꾸면 바로 다시 계산된다. 화면의 예상 최종 마진율 칸에 마우스를 올리면 근거(몇 명, 단가 없음 몇 명)가 보인다.
- 관련 테이블: TB_PJ_ASGN(기간·월 MM), TB_PJ_ASGN_HIST(월 MM 변경, RATE), TB_CO_MON_COST(최근 월 인건비), TB_HR_DEV_PRC(월 급여 지급액), TB_PJ_TK_DSTRB(배분표 금액), TB_PJ_PROJ(계약금액·예산·종료일)
- 결정 B28(사용자 2026-10-30): 남은 MM은 투입 배정에서 가져온다(배분표 MM 칸 DSTRB_MM은 두지 않음, 입력이 늘지 않음). 배분표 유무로 나누지 않는다. 월 인건비가 없는 사람은 위 ②→③→④ 순서로 보완한다(②~④와 「배분표 유무로 나누지 않음」은 요구사항에 없는 보완). 아래 Q-식-28 결정 중 「배분표 MM」「배분표 없으면 이번 달 원가 × 남은 개월」은 이 결정으로 바뀜.
- 결정 Q-식-28·Q-04-1(사용자 2026-10-21): 배분표가 있으면 사람별 남은 달 배분 MM × 그 사람 최근 월 인건비를 더하고, 없으면 이번 달 배분 원가 × 남은 개월 수. 배분표는 가장 나중 단계(실제 투입계획 → 최종 계약 → 견적)를 쓴다. 배분표가 없을 때 따로 “MM당 원가”를 만들지 않는다.', 'UI-04, UI-01', 20, 'SYSTEM'),
('식-21', 'SFR-903', '턴키 예상 최종 마진율과 목표 대비', '예상 최종 마진율 = (현재 계약금액 − 원가 누계 − 남은 기간 예상 원가) ÷ 현재 계약금액 × 100. 목표 대비 = 예상 최종 마진율 − 목표 마진율', '- 쓰는 화면: UI-04, UI-16
- 정의: 끝났을 때의 마진율을 미리 본다. 계약금액에서 지금까지 쓴 원가와 앞으로 쓸 원가를 빼서 계약금액으로 나눈다. 목표 마진율과의 차이는 %p로 적는다.
- 수식: `예상 최종 마진율 = (현재 계약금액 − 원가 누계 − 남은 기간 예상 원가) ÷ 현재 계약금액 × 100. 목표 대비 = 예상 최종 마진율 − 목표 마진율`
- 예시: 계약금액 5억 원, 원가 누계 1.3억 원, 남은 예상 원가 2.6억 원 → (500−130−260) ÷ 500 = 22.0%, 목표 대비 +2.0%p.
- 예외·반올림: 진행 중인 턴키만 계산하고 수주예정·종료는 계산하지 않는다(식-20). 월단위·파견은 이 식 대신 월간 마진율(식-22)을 쓴다.
- 관련 테이블: TB_PJ_PROJ, TB_CO_MON_COST, TB_SY_OPER_STD(목표 마진율)', 'UI-04, UI-16', 21, 'SYSTEM'),
('식-22', 'SFR-903', '월단위·파견 월간 마진율', '월간 마진율 = (당월 매출 − 배분 원가 − 직접 경비) ÷ 당월 매출 × 100. 목표 대비 = 월간 마진율 − 목표 마진율', '- 쓰는 화면: UI-04, UI-16, UI-07
- 정의: 이번 달 매출에서 이번 달 인건비 배분 원가와 직접 경비를 뺀 이익이 매출의 몇 %인지 본다.
- 수식: `월간 마진율 = (당월 매출 − 배분 원가 − 직접 경비) ÷ 당월 매출 × 100. 목표 대비 = 월간 마진율 − 목표 마진율`
- 예시: 당월 매출 3,000만 원, 배분 원가 2,200만 원, 직접 경비 100만 원 → (3000 − 2200 − 100) ÷ 3000 = 23.3%, 목표 대비 +3.3%p.
- 예외·반올림: 직접 경비를 한 번만 빼는 것이 핵심이다. 시안의 “배분 원가”는 직접 경비가 이미 들어 있어 (매출 − 배분 원가) ÷ 매출로 계산한다.
- 관련 테이블: TB_CO_MON_COST
- 결정 Q-식-29(사용자 2026-10-21): UI-04 “누적 원가”는 배분 원가 + 직접 경비(팀비·프로젝트경비·추가 직접 경비) 누계다. 열 이름은 그대로 두고 ⓘ 설명에 적는다.', 'UI-04, UI-16, UI-07', 22, 'SYSTEM'),
('식-23', 'SFR-903', '개인 영업이익과 이익률', '개인 영업이익 = 단가 − 원가합계. 이익률 = 개인 영업이익 ÷ 단가 × 100(단가 0이면 “–”)', '- 쓰는 화면: UI-03, UI-02
- 정의: 한 사람이 한 달에 낸 이익이다. 단가(청구 단가 × 월 MM, 턴키는 배분 금액, 무상지원·내부는 0)에서 원가합계를 뺀다. 엑셀의 U열과 같다.
- 수식: `개인 영업이익 = 단가 − 원가합계. 이익률 = 개인 영업이익 ÷ 단가 × 100(단가 0이면 “–”)`
- 예시: 단가 700만 원 × 1.0MM − 원가합계 5,070,000원 = 1,930,000원, 이익률 27.6%. 내부만 투입된 사람은 0 − 5,070,000 = −5,070,000원.
- 예외·반올림: 대기 인력도 단가 0이라 −원가합계가 된다. 휴직자는 목록에서 뺀다. 프로젝트 줄별로 펼치면 줄마다 (단가 − 배분 원가)를 보여 주고 대기 몫은 따로 한 줄이다.
- 관련 테이블: TB_CO_MON_COST
- 결정 Q-식-30(사용자 2026-10-21): 계산은 그대로(−원가합계) 두고, 개인 원가·수익표 “계산 방법”에 설명 한 줄을 넣는다(SFR-103-07).', 'UI-03, UI-02', 23, 'SYSTEM'),
('식-24', 'SFR-903', 'MM당 평균 단가·원가·이익', 'MM당 단가 = 누적 매출 ÷ 실제 MM. MM당 원가 = 누적 원가 ÷ 실제 MM. MM당 이익 = MM당 단가 − MM당 원가', '- 쓰는 화면: UI-04, UI-01
- 정의: 프로젝트의 MM 하나당 평균 얼마를 받고 얼마를 썼는지 본다. 엑셀의 “평균단가 − 평균급여”를 대신한다. 실제 MM은 칸 개수가 아니라 월별 투입 MM의 합이다.
- 수식: `MM당 단가 = 누적 매출 ÷ 실제 MM. MM당 원가 = 누적 원가 ÷ 실제 MM. MM당 이익 = MM당 단가 − MM당 원가`
- 예시: 누적 매출 4.2억 원, 누적 원가 3.6억 원, 실제 MM 60 → 단가 7,000,000원, 원가 6,000,000원, 이익 1,000,000원.
- 예외·반올림: 실제 MM이 0이면 칸을 비운다. MM당 원가는 월 급여만 평균 내는 엑셀보다 크고 이익은 작게 나온다(원가 전체 기준).
- 관련 테이블: TB_CO_MON_COST, TB_PJ_PROJ. 누적 매출은 턴키 식-18(계약금액 × 진행률), 월단위·파견 월 매출(SALES_AMT) 누계. 청구(TB_BL_BILL)는 쓰지 않는다(2026-10-29 D6)', 'UI-04, UI-01', 24, 'SYSTEM'),
('식-25', 'SFR-903', '배분 기준 매출(턴키 참고선)', '턴키 배분 기준 매출(배분표 있음, 그 달) = 그 달 배분표 합계. 배분표 없음 = 그 달 배분 원가 × (현재 계약금액 ÷ 예산). 이전에는 이 달별 값이 그 달 원가 ÷ (현재 원가 × 전체 개월수)라는 다른 기준을 썼는데, 이번에 식-18 누적 매출과 같은 비율(계약금액 ÷ 예산)로 통일했다. 그래서 달별 값을 모두 더하면 누적 매출(식-18)과 정확히 같아진다 — 같은 방식을 달로 나눠 보여줄 뿐, 더는 다른 계산이 아니다.', '- 쓰는 화면: UI-04, UI-07, UI-01
- 정의: 턴키는 청구한 달에만 매출이 잡혀 출렁인다. 일한 만큼 나눠 본 참고 금액을 함께 보여 준다. 월단위·파견 매출에 턴키 배분표 금액을 더해 UI-07의 점선으로 쓴다.
- 수식: `턴키 배분 기준 매출(배분표 있음, 그 달) = 그 달 배분표 합계. 배분표 없음 = 그 달 배분 원가 × (현재 계약금액 ÷ 예산). 이전에는 이 달별 값이 그 달 원가 ÷ (현재 원가 × 전체 개월수)라는 다른 기준을 썼는데, 이번에 식-18 누적 매출과 같은 비율(계약금액 ÷ 예산)로 통일했다. 그래서 달별 값을 모두 더하면 누적 매출(식-18)과 정확히 같아진다 — 같은 방식을 달로 나눠 보여줄 뿐, 더는 다른 계산이 아니다.`
- 예시: 계약금액 5억 원, 예산 4억 원, 이번 달 배분 원가 2,000만 원 → 5억 × (2,000만 ÷ 4억) = 25,000,000원. 이렇게 매달 더한 값은 원가 누계 1.3억 원 시점에 162,500,000원이 되어 식-18의 누적 매출 예시와 정확히 같다.
- 시안 반영(2026-10-24 사용자 결정): 시안의 UI-07 참고선은 계약금액 ÷ 개월수 균등 분할, UI-04 상세 월별 금액은 원가 기준만 쓰고 있었다 → 명세서대로 배분표를 먼저 쓰고 없을 때만 원가 × 계약금액 ÷ 예산으로 고쳤다. 이제 턴키 단가·참고선은 UI-03·UI-04·UI-07·UI-19 모두 같은 규칙이다.
- 예외·반올림: 참고용이다. 실제 매출과 영업이익표 숫자는 바꾸지 않는다. 월별 상세 금액과 누적 매출(식-18)이 같은 기준(계약금액 ÷ 예산)을 쓰므로, 상세 창에서 달별 금액을 다 더한 값이 누적 매출과 어긋나지 않는다.
- 관련 테이블: TB_PJ_TK_DSTRB, TB_CO_MON_COST', 'UI-04, UI-07, UI-01', 25, 'SYSTEM'),
('식-26', 'SFR-903', '마진 시뮬레이션(가상)', '월단위·파견: 새 마진율 = (매출 + n × 1인 단가 − 원가 − n × 1인 월 원가) ÷ (매출 + n × 1인 단가). 턴키: 새 마진율 = (계약금액 − 원가 누계 − 남은 예상 원가 − n × 1인 월 원가 × 개월) ÷ 계약금액', '- 쓰는 화면: UI-04
- 정의: 인원을 늘리거나 줄이면 마진율이 얼마나 바뀌는지 가상으로 본다. 결과에 “가상” 표시를 하고 저장하지 않는다.
- 수식: `월단위·파견: 새 마진율 = (매출 + n × 1인 단가 − 원가 − n × 1인 월 원가) ÷ (매출 + n × 1인 단가). 턴키: 새 마진율 = (계약금액 − 원가 누계 − 남은 예상 원가 − n × 1인 월 원가 × 개월) ÷ 계약금액`
- 예시: 월단위: 매출 3,000만, 원가 2,300만, 1명 추가(단가 800만, 원가 600만) → (3800−2900) ÷ 3800 = 23.68%. 턴키: 5억, 원가 누계 1.3억, 남은 2.6억, 1명 × 월 600만 × 4개월 = 2,400만 → (500−130−260−24) ÷ 500 = 17.2%.
- 예외·반올림: 목표 마진율 20%와의 차이도 함께 적는다. 턴키는 계약금액이 그대로라 매출은 변하지 않는다.
- 관련 테이블: (저장 없음)', 'UI-04', 26, 'SYSTEM'),
('식-27', 'SFR-903', '무상지원 비용', '무상지원 원가 = 무상지원 투입의 배분 원가. 무상지원 MM = 무상지원 투입의 MM 합', '- 쓰는 화면: UI-04, UI-03
- 정의: 무상지원 MM은 매출이 0이지만 원가는 그 프로젝트 원가로 잡는다. 프로젝트 원가 분석에 “무상지원 MM · 원가”로 따로 보여 준다.
- 수식: `무상지원 원가 = 무상지원 투입의 배분 원가. 무상지원 MM = 무상지원 투입의 MM 합`
- 예시: 월 인건비 4,970,000원인 사람이 무상지원 0.5MM + 유상 0.5MM → 무상지원 원가 2,485,000원, 매출 0원.
- 예외·반올림: 내부 프로젝트가 아니므로 판관비로 가지 않는다(v2 D-8).
- 마감한 달(2026-10-28): TB_CO_MON_COST에서 FREE_MM > 0인 줄(투입구분 FREE)의 LABOR_ALLOC_AMT 합이 무상지원 원가, FREE_MM 합이 무상지원 MM이다. 투입 1건이 한 줄이라 비례 계산이 필요 없다.
- 관련 테이블: TB_PJ_ASGN.INPUT_TYPE_CD(무상지원), TB_CO_MON_COST.FREE_MM
- 결정 Q-식-31(사용자 2026-10-21): 명세서대로 무상지원 원가는 그 프로젝트 원가로 잡는다(판관비 아님).', 'UI-04, UI-03', 27, 'SYSTEM'),
('식-28', 'SFR-904', '매출총이익', '매출원가 = 배분 원가 + 대기 원가 + 직접 경비. 매출총이익 = 매출액 − 매출원가. 매출총이익률 = 매출총이익 ÷ 매출액 × 100', '- 쓰는 화면: UI-08, UI-09, UI-07, UI-16
- 정의: 매출에서 매출원가를 뺀 이익이다. 매출원가는 배분 원가(내부 제외), 대기 원가, 직접 경비를 더한 값이다.
- 수식: `매출원가 = 배분 원가 + 대기 원가 + 직접 경비. 매출총이익 = 매출액 − 매출원가. 매출총이익률 = 매출총이익 ÷ 매출액 × 100`
- 예시: 매출 10억 원, 배분 원가 6.5억, 대기 원가 0.8억, 직접 경비 0.2억 → 매출원가 750,000,000원, 매출총이익 250,000,000원, 매출총이익률 25.0%.
- 예외·반올림: 직접 경비는 한 번만 더한다(배분 원가에는 인건비 몫만 넣는다).
- 관련 테이블: TB_CO_MON_PL', 'UI-08, UI-09, UI-07, UI-16', 28, 'SYSTEM'),
('식-29', 'SFR-904', '판관비', '판관비 = 재무 항목 입력 합계 + 간접 인건비 + 연구개발비(내부 프로젝트)', '- 쓰는 화면: UI-08, UI-09, UI-07, UI-10
- 정의: 판매비와 관리비다. 재무 항목(UI-10)에 입력한 값에 간접 인건비와 연구개발비를 더한다.
- 수식: `판관비 = 재무 항목 입력 합계 + 간접 인건비 + 연구개발비(내부 프로젝트)`
- 예시: 입력 5,000만 원 + 간접 인건비 2,000만 원 + 연구개발비 1,500만 원 = 85,000,000원.
- 예외·반올림: 재무 항목은 매달 반복 항목이 자동으로 채워진다. 마감한 달은 바꾸지 않는다. 마감 전 달은 재무 항목에 저장한 판관비 합계가 바로 손익(영업이익·순이익)에 반영되고, 영업외수익·영업외비용도 같은 식으로 순이익에 반영된다(2026-10-28). 법인카드 대금·계좌이체 지출은 판관비가 아니라 손익에 넣지 않는다(식-48).
- 관련 테이블: TB_FI_ITEM, TB_CO_MON_PL', 'UI-08, UI-09, UI-07, UI-10', 29, 'SYSTEM'),
('식-30', 'SFR-904', '영업이익과 영업이익률', '영업이익 = 매출총이익 − 판관비 + 전월 조정. 영업이익률 = 영업이익 ÷ 매출액 × 100', '- 쓰는 화면: UI-08, UI-07, UI-16, UI-18
- 정의: 매출총이익에서 판관비를 빼고 전월 조정을 더한 값이다. 전월 조정은 이전에 마감한 달의 정정분이다.
- 수식: `영업이익 = 매출총이익 − 판관비 + 전월 조정. 영업이익률 = 영업이익 ÷ 매출액 × 100`
- 예시: 매출총이익 250,000,000원 − 판관비 85,000,000원 + 전월 조정 (−3,000,000원) = 162,000,000원. 매출 10억 원이면 영업이익률 16.2%.
- 예외·반올림: 조정 부호는 식-59 결정(Q-식-55, ADJ_AMT = 영업이익에 더하는 값)을 따른다.
- 관련 테이블: TB_CO_MON_PL, TB_CO_PREV_ADJ', 'UI-08, UI-07, UI-16, UI-18', 30, 'SYSTEM'),
('식-31', 'SFR-904', '법인세(월할)', '월 법인세 = (연간 예상 법인세 − 마감된 달에 이미 반영한 금액) ÷ 남은 개월 수. 원 단위 버림, 끝자리는 12월에 모은다. 연초에는 연간 예상액 ÷ 12', '- 쓰는 화면: UI-08, UI-09, UI-10, UI-07
- 정의: 법인세는 한꺼번에 내지만 손익에는 매달 나눠 반영한다. 이미 마감한 달의 금액은 그대로 두고, 연간 예상액이 바뀌면 차이를 남은 달에 나눈다.
- 수식: `월 법인세 = (연간 예상 법인세 − 마감된 달에 이미 반영한 금액) ÷ 남은 개월 수. 원 단위 버림, 끝자리는 12월에 모은다. 연초에는 연간 예상액 ÷ 12`
- 예시: 연간 예상 18,400,000원 → 매달 1,533,333원, 12월은 끝자리를 모아 1,533,337원(합계 18,400,000원). 9월까지 마감(13,799,997원 반영) 후 예상액을 20,000,000원으로 바꾸면 남은 3개월은 2,066,667원, 2,066,667원, 2,066,669원.
- 예외·반올림: 실제 납부(3월 확정 신고, 8월 중간예납)는 손익이 아니라 자금수지에만 잡는다(식-50).
- 관련 테이블: TB_FI_CORP_TAX, TB_CO_MON_PL.CORP_TAX_AMT
- 결정 Q-14-4(사용자 2026-10-22): 마감을 풀면 그 달은 "마감 안 된 달"로 돌아가 위 식으로 다시 나눈다. 예: 1~9월 마감 후 9월 해제 → 9~12월 4개월이 남은 금액을 나눈다. 다시 마감할 때 그 달 금액을 새로 고정한다.', 'UI-08, UI-09, UI-10, UI-07', 31, 'SYSTEM'),
('식-32', 'SFR-904', '당기순이익과 순이익률', '당기순이익 = 영업이익 + 영업외수익 − 영업외비용 − 법인세(월할). 순이익률 = 당기순이익 ÷ 매출액 × 100', '- 쓰는 화면: UI-09, UI-07, UI-16
- 정의: 영업이익에 영업외수익을 더하고 영업외비용과 법인세(월할)를 뺀 값이다.
- 수식: `당기순이익 = 영업이익 + 영업외수익 − 영업외비용 − 법인세(월할). 순이익률 = 당기순이익 ÷ 매출액 × 100`
- 예시: 영업이익 162,000,000원 + 영업외수익 2,200,000원 − 영업외비용 3,100,000원 − 법인세 1,533,333원 = 159,566,667원. 매출 10억 원이면 순이익률 16.0%.
- 예외·반올림: 매출이 0이면 순이익률은 “–”로 보인다.
- 관련 테이블: TB_CO_MON_PL', 'UI-09, UI-07, UI-16', 32, 'SYSTEM'),
('식-33', 'SFR-904', '대기 원가 비중', '대기 원가 비중 = 대기 원가 ÷ 매출원가 × 100', '- 쓰는 화면: UI-07
- 정의: 매출원가 가운데 투입되지 않은 정규·계약직 인건비가 차지하는 몫이다.
- 수식: `대기 원가 비중 = 대기 원가 ÷ 매출원가 × 100`
- 예시: 대기 원가 8,000만 원, 매출원가 7.5억 원 → 10.7%.
- 예외·반올림: 매출원가가 0이면 “–”.
- 관련 테이블: TB_CO_MON_PL', 'UI-07', 33, 'SYSTEM'),
('식-34', 'SFR-904', '계약 유형별 기여도', '유형별 매출총이익 = 그 유형 매출 − 그 유형 배분 원가. 기여도 = 유형별 매출총이익 ÷ 흑자 유형(매출총이익 > 0) 합계 × 100', '- 쓰는 화면: UI-09, UI-07
- 정의: 매출총이익을 턴키·월단위·파견으로 나눠 각 유형이 차지하는 비율을 본다. 내부(자체 개발)는 판관비라 넣지 않는다.
- 수식: `유형별 매출총이익 = 그 유형 매출 − 그 유형 배분 원가. 기여도 = 유형별 매출총이익 ÷ 흑자 유형(매출총이익 > 0) 합계 × 100`
- 예시: 턴키 1.2억, 월단위 2.0억, 파견 0.8억 → 합계 4.0억. 기여도 30.0%, 50.0%, 20.0%.
- 예외·반올림: 적자 유형은 비율을 내지 않고 막대 없이 “적자 −금액”만 빨갛게 보인다. 예: 턴키 −0.4억, 월단위 2.0억, 파견 0.8억 → 월단위 71.4%, 파견 28.6%, 턴키 “적자 −0.4억”(Q-09-1 결정 2026-10-21). 2025년(시작한 달 전)처럼 월별 손익만 옮겨 온 해는 유형별 기여도를 볼 수 없다.
- 관련 테이블: TB_CO_MON_COST.CTRT_TYPE_CD
- 결정 Q-식-32(사용자 2026-10-21): 기여도 기준은 매출총이익이다. 어느 유형이 이익을 많이 냈는지 보는 막대라서 매출 기준으로 바꾸지 않는다. 매출 비중은 UI-07 매출 구성의 「계약 유형별」에서 본다.', 'UI-09, UI-07', 34, 'SYSTEM'),
('식-35', 'SFR-904', '기간 합산·월 평균·전년 대비', '누계 = 1월부터 기준월까지 합. 월 평균 = 누계 ÷ 개월 수. 전년 대비 = (올해 누계 − 작년 같은 기간 누계) ÷ |작년 누계| × 100. 전월 대비도 같은 방식', '- 쓰는 화면: UI-07, UI-08, UI-09, UI-03, UI-18
- 정의: 월 숫자를 분기·연 누계로 더하고, 월 평균과 전년 같은 기간 대비 증감을 구한다. 진행 중인 분기와 해는 기준월까지만 더한다.
- 수식: `누계 = 1월부터 기준월까지 합. 월 평균 = 누계 ÷ 개월 수. 전년 대비 = (올해 누계 − 작년 같은 기간 누계) ÷ |작년 누계| × 100. 전월 대비도 같은 방식`
- 예시: 1~3월 누계 매출 12억 원, 작년 1~3월 10억 원 → +20.0%(증가). 3개월 누계 12억 원의 월 평균은 400,000,000원.
- 예외·반올림: 개인 원가·수익표의 월 평균은 일한 달만 개월 수에 넣는다(프리랜서·외주는 투입한 달만).
- 관련 테이블: TB_CO_MON_PL, TB_CO_MON_COST', 'UI-07, UI-08, UI-09, UI-03, UI-18', 35, 'SYSTEM'),
('식-36', 'SFR-904', '사업계획 달성률', '매출 달성률 = 실적 매출 ÷ 목표 매출 × 100. 이익률 차이 = 실적 영업이익률 − 목표 영업이익률(목표 영업이익 ÷ 목표 매출)', '- 쓰는 화면: UI-18, UI-16
- 정의: 실적을 올해 사업계획 목표와 견줘 달성률을 낸다. 월별 표의 영업이익은 금액이 아니라 이익률의 차이를 %p로 본다. 요약 칸의 「누계 영업이익 달성률」은 금액 비율(영업이익 달성률)로 본다(2026-10-29 사용자 결정, 시안 그대로).
- 수식: `매출 달성률 = 실적 매출 ÷ 목표 매출 × 100. 이익률 차이 = 실적 영업이익률 − 목표 영업이익률(목표 영업이익 ÷ 목표 매출)`
- 수식(영업이익 달성률, 2026-10-29): `누계 영업이익 달성률 = Σ(1월~기준월 실적 영업이익) ÷ Σ(1월~기준월 목표 영업이익) × 100`
  - 실적 영업이익 = 영업이익표(UI-08)와 같은 값(TB_CO_MON_PL.OPRT_PRFT_AMT, 마감 안 한 달은 그때그때 계산값). 목표 = TB_FI_BIZ_PLAN.OPRT_PRFT_TGT_AMT
  - 표시: 소수 첫째 자리 반올림 + %. 칸 아래에 「실적 누계 / 목표 누계」 금액
  - 누계 목표 합이 0 이하(목표 미등록·빈칸, 또는 목표 자체가 적자)면 계산하지 않고 「–」(목표가 음수면 비율 방향이 거꾸로 되어 뜻이 없어서)
  - 실적 누계가 적자면 음수 %로 보인다(예: 실적 −1억, 목표 8억 → −12.5%)
  - 쓰는 곳: 재무·자금 › 사업계획 대비 실적 요약 「누계 영업이익 달성률」(SFR-118-01), 대시보드 요약 「누계 영업이익」 칸의 「목표 달성 n%」(SFR-116-02). 두 곳은 같은 값
- 예시: 실적 매출 9.5억, 목표 10억 → 95.0%. 실적 영업이익 1.5억(이익률 15.8%), 목표 영업이익 1억(목표 이익률 10.0%) → +5.8%p, 영업이익 달성률 150.0%. 10월 기준 1~10월 실적 영업이익 합 7억, 목표 합 8억 → 누계 영업이익 달성률 87.5%.
- 예외·반올림: 월별과 누계 둘 다 같은 식이다. 목표는 사업계획 업로드 값이다. 목표 매출이 0(업로드 빈칸 포함)이면 달성률과 이익률 차이를 계산하지 않고 “–”로 보인다(Q-18-3 결정 2026-10-21). 누계 목표는 0인 달도 그대로 더한다.
- 관련 테이블: TB_FI_BIZ_PLAN, TB_CO_MON_PL', 'UI-18, UI-16', 36, 'SYSTEM'),
('식-37', 'SFR-905', '가동률', '가동률 = 그 달 MM > 0인 인원 ÷ 그 달 재직 직접 인력 × 100(퇴사자는 퇴사한 달까지). 목표 대비 = 가동률 − 90%', '- 쓰는 화면: UI-16, UI-03
- 정의: 그 달 투입 중인 인원이 전체 직접 인력의 몇 %인지 본다. 전체는 대기·휴직까지 포함한다. 투입 중은 MM이 0보다 큰 사람이고 무상지원·내부 투입도 센다. 목표선은 90%다.
- 수식: `가동률 = 그 달 MM > 0인 인원 ÷ 그 달 재직 직접 인력 × 100. 목표 대비 = 가동률 − 90%` (그 달 재직 = 퇴사자는 퇴사한 달까지)
- 예시: 전체 50명, 투입 중 40명 → 80.0%, 목표 대비 −10.0%p.
- 예외·반올림: 목표 가동률 90%는 운영 기준값이다. 대시보드는 대기 인원 수도 함께 보여 준다.
- 관련 테이블: TB_PJ_ASGN_MON, TB_HR_DEV, TB_SY_OPER_STD(목표 가동률)
- 결정 Q-식-33(사용자 2026-10-22): 전체 = 직간접구분(DRCT_CLS_CD)이 직접인 사람 전체. 직원구분(정규·계약·프리랜서·외주)은 상관없고 대기·휴직 포함, 간접(관리직)과 퇴사자는 뺀다. 시안 v1.4 반영.
- 결정(퇴사, 2026-10-27 사용자 결정): 퇴사자를 지금 상태로 빼면 일했던 지난달 분모에서도 빠져 비율이 틀어진다. 그래서 퇴사자는 퇴사일이 있는 달까지 넣고 다음 달부터 뺀다(달마다 따짐, 2026-10-27 사용자 결정). 예: 12월 15일 퇴사 → 1~12월 분모에 들어가고 내년 1월부터 빠짐. 식-38·39도 같다.', 'UI-16, UI-03', 37, 'SYSTEM'),
('식-38', 'SFR-905', '가득률', '가득률 = 월단위·파견 투입 인원 ÷ 그 달 재직 직접 인력 × 100', '- 쓰는 화면: UI-03, UI-16
- 정의: 매달 단가만큼 청구되는 인력의 비율이다. 월단위·파견에 투입된 인원을 센다. 턴키·무상지원·내부만 하는 사람은 빠진다.
- 수식: `가득률 = 월단위·파견 투입 인원 ÷ 그 달 재직 직접 인력 × 100` (분모는 식-37과 같음)
- 예시: 전체 50명, 월단위·파견 투입 30명 → 60.0%.
- 예외·반올림: 월단위와 턴키를 함께 하는 사람은 가득에 센다(시안 동작).
- 관련 테이블: TB_PJ_ASGN_MON, TB_PJ_PROJ.CTRT_TYPE_CD
- 결정 Q-식-34(사용자 2026-10-21): 명세서 정의대로. 가동률 = 어느 프로젝트든 일하는 사람 비율, 가득률 = 그중 매달 단가만큼 청구되는(월단위·파견) 사람 비율. 요약 칸에 이 설명을 도움말로 보여 준다.', 'UI-03, UI-16', 38, 'SYSTEM'),
('식-39', 'SFR-905', '연 누계 월평균 가동·가득 인원', '월평균 가동 인원 = Σ (월 가동 인원) ÷ 개월 수. 월평균 재직 인원 = Σ (그 달 재직 인원) ÷ 개월 수. 가동률 = 월평균 가동 인원 ÷ 월평균 재직 인원 × 100. 가득도 같은 방식', '- 쓰는 화면: UI-03
- 정의: 연 누계로 볼 때 가동률은 달마다 가동 인원을 구해 평균을 낸 뒤 총 인원으로 나눈다.
- 수식: `월평균 가동 인원 = Σ (월 가동 인원) ÷ 개월 수. 월평균 재직 인원 = Σ (그 달 재직 인원) ÷ 개월 수. 가동률 = 월평균 가동 인원 ÷ 월평균 재직 인원 × 100. 가득도 같은 방식`
- 재직 인원: 퇴사자는 퇴사일이 있는 달까지 넣고 다음 달부터 뺀다(달마다 따짐, 2026-10-27 사용자 결정). 퇴사가 없으면 총 인원과 같다.
- 예시: 1~3월 가동 인원 38, 40, 42명 → 월평균 40명. 총 50명이면 80.0%.
- 예외·반올림: 월평균 인원은 소수 첫째 자리로 보여 주고 그 값으로 계산한다(Q-식-35).
- 관련 테이블: TB_PJ_ASGN_MON
- 결정 Q-식-35(사용자 2026-10-21): 월평균 인원은 소수 첫째 자리(예: 39.7명)로 보여 주고 가동률·가득률도 이 값으로 계산한다.', 'UI-03', 39, 'SYSTEM'),
('식-40', 'SFR-905', '개인 원가·수익표 평균과 합계', '평균 단가 = Σ 단가 ÷ 단가 칸이 있는 인원. 평균 원가 = Σ 원가합계 ÷ 표 인원. 평균 영업이익 = Σ 영업이익 ÷ 표 인원', '- 쓰는 화면: UI-03
- 정의: 표 위쪽 요약의 평균이다. 평균 단가는 단가 칸이 있는 인원의 평균(단가 0인 대기·내부 포함)이다. 평균 원가와 평균 영업이익은 합계를 표의 인원(휴직 제외)으로 나눈다. 엑셀 「평균단가」「평균급여(원가포함)」와 같다.
- 수식: `평균 단가 = Σ 단가 ÷ 단가 칸이 있는 인원. 평균 원가 = Σ 원가합계 ÷ 표 인원. 평균 영업이익 = Σ 영업이익 ÷ 표 인원`
- 예시: 5명(휴직 제외), 단가 700·650·0·800·0만 원, 원가합계 507·480·500·600·450만 원 → 평균 단가 4,300,000원, 평균 원가 5,074,000원, 평균 영업이익 −774,000원, 적자 2명.
- 예외·반올림: “턴키 단가 미포함” 보기에서는 턴키만 하는 사람(단가 800만 원)의 단가 칸을 비우고 평균 단가에서 뺀다 → 평균 단가 3,375,000원. 그 사람의 영업이익은 −원가합계(−6,000,000원)로 평균에 들어가 평균 영업이익은 −2,374,000원이다.
- 관련 테이블: TB_CO_MON_COST', 'UI-03', 40, 'SYSTEM'),
('식-41', 'SFR-906', '파트 월 단가', '월 단가 = Σ (청구 단가 × 월 MM). 턴키는 배분표의 그 달 금액(배분표 없으면 그 달 배분 원가 × 현재 계약금액 ÷ 예산). 무상지원 0. 내부 제외', '- 쓰는 화면: UI-19
- 정의: 영업 파트 이익금 계산에서 한 사람의 그 달 단가다. 프로젝트별 (청구 단가 × 월 MM)을 모두 더한다. 턴키는 배분 금액을 쓰고 무상지원은 0이다. 내부 프로젝트는 뺀다.
- 수식: `월 단가 = Σ (청구 단가 × 월 MM). 턴키는 배분표의 그 달 금액(배분표 없으면 그 달 배분 원가 × 현재 계약금액 ÷ 예산). 무상지원 0. 내부 제외`
- 예시: 700만 원 × 1.0MM → 7,000,000원. 투입 시작 달 0.52MM이면 700만 원 × 0.52 = 3,640,000원.
- 예외·반올림: 월 MM은 식-01을 쓴다. 금액은 투입 건별로 원 단위 버림한 뒤 더한다(식-02·식-57과 같음. 예: 1,100,000원 × 0.47MM = 517,000원, 6,666,666원 × 0.77MM = 5,133,332원, 2026-10-30).
- 관련 테이블: TB_CO_PART_SETL_DTL.UPRC_AMT
- 결정 Q-식-36·Q-19-2(사용자 2026-10-21): 턴키 단가는 배분표의 그 사람 그 달 금액(가장 나중 단계 표). 배분표가 없으면 그 사람 그 달 배분 원가 × (현재 계약금액 ÷ 예산)(식-25와 같은 비율). 턴키 투입에는 청구 단가 칸이 없어 “월 단가 × MM”은 쓰지 않는다.', 'UI-19', 41, 'SYSTEM'),
('식-42', 'SFR-906', '파트 월 비용(급여·공통관리비·퇴직금)', '급여 = 그 달 실제 지급액. 시작 달은 월 급여 × (그 달 일수 − 시작일 + 1) ÷ 그 달 일수. 공통관리비 = 파트 기준값(외주 0). 퇴직금 = 급여 ÷ 12(정규직만). 비용 = 급여 + 공통관리비 + 퇴직금', '- 쓰는 화면: UI-19
- 정의: 급여는 MM과 관계없이 그 달 실제 지급액 전부다. 투입을 시작한 달만 날짜만큼 줄인다. 공통관리비는 외주 인력만 빼고 붙고, 퇴직금은 정규직만이다.
- 수식: `급여 = 그 달 실제 지급액. 시작 달은 월 급여 × (그 달 일수 − 시작일 + 1) ÷ 그 달 일수. 공통관리비 = 파트 기준값(외주 0). 퇴직금 = 급여 ÷ 12(정규직만). 비용 = 급여 + 공통관리비 + 퇴직금`
- 예시: 10월 16일 투입 시작(31일), 월 급여 400만 원 → 급여 2,064,516원. 정규직 급여 400만 원, 공통관리비 150만 원 → 퇴직금 333,333원, 비용 5,833,333원. 외주 인력 공통관리비는 0원.
- 예외·반올림: 시작 달 급여와 퇴직금은 원 단위 버림이다(식-57 공통 규칙. 예: 급여 3,500,000원 → 퇴직금 291,666원, 2026-10-30 D16). 공통관리비 기준값 150만 원은 파트 기준 이력에서 가져온다. 프리랜서도 공통관리비가 붙는다. 시작 달인데 개발자 풀 월 급여·지급액이 비어 있으면 그 달 급여는 0으로 잡히고, 비고에 “⚠ 월 급여 없음(시작 달 급여 0)”을 표시한다(막지 않음, N-02-4).
- 관련 테이블: TB_CO_PART_SETL_DTL(PAY_AMT·SALES_EXPS_AMT·RETIRE_AMT·COST_AMT)
- 결정 Q-식-37(사용자 2026-10-21): 명세서대로 투입을 시작한 달만 날짜만큼 줄이고 철수한 달은 줄이지 않는다(v2 D-32, 엑셀 「원가표」 방식). 퇴사로 끝난 달은 급여 업로드 값이 이미 줄어 있다.
- 결정 Q-식-38(사용자 2026-10-21): Q-식-14와 같은 답. 파트 이익금은 급여 ÷ 12(수당 제외) 그대로 둔다(식-09 참고).', 'UI-19', 42, 'SYSTEM'),
('식-43', 'SFR-906', '인력별 이익금', '차액 = 월 단가 − 월 비용. 이익금 = 차액 × 파트 이익 배분율(40%), 원 단위 버림', '- 쓰는 화면: UI-19
- 정의: 월 단가에서 월 비용을 뺀 차액에 파트 이익 배분율을 곱한 값이다. 적자 인력은 음수가 된다.
- 수식: `차액 = 월 단가 − 월 비용. 이익금 = 차액 × 파트 이익 배분율(40%), 원 단위 버림`
- 화면 표시: 영업 파트 이익금(UI-19) 표에서 차액 열 이름은 「이익」, 요약 칸은 「이익 합계」다. 「비용 계」와 「이익」 열 이름 옆 ⓘ에 계산식을 보인다(2026-10-29 사용자 결정). DB 칸은 TB_CO_PART_SETL_DTL.DIFF_AMT 그대로.
- 예시: 단가 700만 원 × 1.0MM, 비용 5,833,333원 → 차액 1,166,667원, 이익금 = 1,166,667원 × 40% = 466,666원(버림).
- 예외·반올림: 음수 예: 단가 350만 원(0.5MM), 비용 5,833,333원 → 차액 −2,333,333원 × 40% = −933,333.2원. 0 쪽으로 버리면 −933,333원, 아래로 버리면 −933,334원.
- 관련 테이블: TB_CO_PART_SETL_DTL(DIFF_AMT·PRFT_AMT)
- 결정 Q-식-39(사용자 2026-10-21): 음수는 0 쪽으로 자른다(−933,333.2 → −933,333). 엑셀 ROUNDDOWN과 같고, 식-57의 버림 전체에 같은 규칙.', 'UI-19', 43, 'SYSTEM'),
('식-44', 'SFR-906', '파트 이익금', '파트 이익금 = Σ 인력별 이익금 − 법인카드 사용액. 기간 추이 합계는 투입한 달(MM > 0)의 값만 더한다', '- 쓰는 화면: UI-19, UI-14, UI-16
- 정의: 그 파트의 그 달 이익금이다. 인력별 이익금을 모두 더하고(적자도 그대로 상계) 법인카드 사용액을 뺀다. 정산을 확정하면 그 파트·월은 잠긴다.
- 수식: `파트 이익금 = Σ 인력별 이익금 − 법인카드 사용액. 기간 추이 합계는 투입한 달(MM > 0)의 값만 더한다`
- 예시: A 개발자 466,666원, B 개발자 −120,000원, 법인카드 50,000원 → 296,666원(적자도 상계).
- 예외·반올림: 확정 취소는 사유를 남겨야 하고, 마감한 달은 취소할 수 없다. 인력은 그 사람의 소속 영업 파트로 모은다.
- 관련 테이블: TB_CO_PART_SETL(CORP_CARD_AMT·PART_PRFT_AMT)
- 결정 Q-식-40(사용자 2026-10-21): 명세서대로 적자 인력의 음수 이익금도 파트 합계에서 그대로 상계한다.
- 결정 Q-식-41(사용자 2026-10-21): 명세서대로 그 사람의 소속 영업 파트로 모은다. 프로젝트를 어느 파트(본사 직접 영업 포함, v2 D-34)가 따왔는지와 관계없다.
- 결정(사용자 2026-10-25): 마감할 때 확정하지 않은 영업 파트 정산은 **함께 자동 확정**한다(확정자 = 마감한 사람, 표시 「마감 시 자동 확정」). 이의는 마감 전에 정리하고, 마감 뒤 틀린 금액(늦게 온 법인카드 등)은 다음 달에 반영. 마감 해제하면 기존 규칙대로 재무가 사유를 적고 확정 취소 가능(2026-10-25 사용자 결정, 명세서에 규칙 없음).', 'UI-19, UI-14, UI-16', 44, 'SYSTEM'),
('식-45', 'SFR-906', '파트 기준 이력(배분율·공통관리비)', '그 달의 적용 값 = 기준 이력 중 적용 시작월 ≤ 그 달 ≤ 적용 종료월인 줄의 값', '- 쓰는 화면: UI-19, UI-15
- 정의: 이익 배분율과 공통관리비는 그 달에 적용되는 “영업 파트 기준 이력” 값을 쓴다. 기준을 바꿔도 이미 마감한 달의 정산은 바뀌지 않는다.
- 수식: `그 달의 적용 값 = 기준 이력 중 적용 시작월 ≤ 그 달 ≤ 적용 종료월인 줄의 값`
- 예시: 2025년 배분율 35%, 2026년부터 40%. 차액 1,000,000원이면 2025-12는 350,000원, 2026-01은 400,000원.
- 예외·반올림: 파트장 교체도 같은 이력 줄로 남긴다.
- 관련 테이블: TB_BS_PART_STD(APLY_BGN_YM·APLY_END_YM)
- 결정 Q-식-42(사용자 2026-10-21): 명세서대로 파트마다 이익 배분율·공통관리비를 따로 넣을 수 있다(UI-15 기준 이력). 처음 값은 모두 40%·150만 원이고, 실제 파트별 값은 이관 때 확인해서 넣는다.', 'UI-19, UI-15', 45, 'SYSTEM'),
('식-46', 'SFR-907', '월 이월금', '이월금 = 전월이월금(A) + 수입(B) − 지출(C). 다음 달 전월이월금 = 이번 달 이월금', '- 쓰는 화면: UI-21, UI-16
- 정의: 그 달 끝에 남는 돈이다. 전월이월금에 수입을 더하고 지출을 뺀다. 첫 달의 전월이월금은 기초 잔액이다.
- 수식: `이월금 = 전월이월금(A) + 수입(B) − 지출(C). 다음 달 전월이월금 = 이번 달 이월금`
- 예시: 전월이월금 1,000,000,000원 + 수입 850,000,000원 − 지출 900,000,000원 = 950,000,000원.
- 예외·반올림: 부가세를 포함해 계산한다(손익은 부가세 제외). 예정 달은 예정일, 실적 달은 실제 입금·지급일 기준이다.
- 관련 테이블: TB_BL_RCPT, TB_HR_MON_PAY, TB_FI_ITEM, TB_FI_LOAN, TB_FI_CARD_MON, TB_FI_XFER, TB_FI_TAX_PAY, TB_SY_OPER_STD(CASH_BASE_YM, CASH_BASE_AMT)
- 저장(2026-10-29 사용자 결정): 자금수지 전용 테이블을 두지 않는다. 입금·급여·외주 원가·재무 항목·대여금·법인카드·계좌이체·법인세 원천 테이블에서 달마다 바로 합산해 계산한다(TB_FI_CASH 삭제, 쓰던 지급 관리가 2026-10-28 없어져 조회만 남았기 때문).
- 결정 Q-식-43(사용자 2026-10-21): 기초 잔액과 시작 월은 운영 기준값(UI-27)에 관리자가 한 번 넣는다. 시작 월을 마감하면 고칠 수 없다. 회사의 최신 자금수지 양식은 받으면 줄 구성만 맞춘다.', 'UI-21, UI-16', 46, 'SYSTEM'),
('식-47', 'SFR-907', '자금수지 수입', '수입(B) = 입금 합계(부가세 포함) + 잡수익 + 대여금', '- 쓰는 화면: UI-21, UI-13
- 정의: 수입은 입금 합계(부가세 포함)에 잡수익과 대여금을 더한 값이다. 입금 예정은 예정일, 실적은 실제 입금일에 잡는다.
- 수식: `수입(B) = 입금 합계(부가세 포함) + 잡수익 + 대여금`
- 예시: 입금 11,275,000원 + 잡수익 2,000,000원 + 대여금 0원 = 13,275,000원.
- 예외·반올림: 입금은 매출에 영향을 주지 않고 미수금(식-06)과 자금수지에만 쓴다.
- 관련 테이블: TB_BL_RCPT, TB_FI_ITEM, TB_FI_LOAN
- 결정 Q-식-44(사용자 2026-10-22): 대여금은 재무 항목(UI-10)의 「대여금 입출금」 표에 넣는다. 받으면 +, 갚으면 −로 자금수지 대여금 줄에 잡히고 손익에는 넣지 않는다. 잡수익은 재무 항목 영업외수익이다.', 'UI-21, UI-13', 47, 'SYSTEM'),
('식-48', 'SFR-907', '자금수지 지출', '지출(C) = 인건비(실지급액) + 계약직·프리랜서 + 용역비(부가세 포함) + 미지급 인건비(계좌이체 지출 구분 특별비) + 제경비(세금·4대보험·영업비·계좌이체 지출 구분 계좌이체(퇴직금 지급·퇴직연금 납입 포함) 등) + 법인세 납부', '- 쓰는 화면: UI-21, UI-10
- 정의: 지출은 인건비, 계약직·프리랜서, 용역비(외주), 미지급 인건비, 제경비, 법인세 납부를 더한 값이다. 퇴직금 적립은 실제 지급·납입한 달에만 제경비로 잡는다(재무 항목 계좌이체 지출 표에 입력).
- 수식: `지출(C) = 인건비(실지급액) + 계약직·프리랜서 + 용역비(부가세 포함) + 미지급 인건비(계좌이체 지출 구분 특별비) + 제경비(세금·4대보험·영업비·계좌이체 지출 구분 계좌이체(퇴직금 지급·퇴직연금 납입 포함) 등) + 법인세 납부`
- 예시: 정규직 인건비 30,000,000원 + 계약직·프리랜서 8,000,000원 + 용역비 8,800,000원 + 미지급 0원 + 제경비 4,500,000원 + 법인세 0원 = 51,300,000원.
- 예외·반올림: 기존 비용 시트는 정규직→인건비, 프리랜서→계약직·프리랜서, 업체→용역비, 특별비→미지급 인건비, 제경비→제경비로 옮긴다. 「미지급 인건비」는 기존 시트의 특별비를 넣는 줄로, 재무 항목 계좌이체 지출에서 구분 「특별비」로 넣은 금액(TB_FI_XFER.XFER_CLS_CD = SPCL, 이체일자 달)을 더한다. 없으면 0(2026-10-30 E3 사용자 결정). 「인건비(정규직)」과 계약직 급여는 급여 업로드 실지급액(TB_HR_MON_PAY.REAL_PAY_AMT, 식-17)에서 가져온다. 마감한 달은 실적, 마감 전 달은 예정이고, 업로드가 없는 앞으로의 달은 가장 최근 업로드 금액을 예정으로 쓴다(2026-10-25). 예: 10월 업로드 정규직 실지급액 합 4억 → 10월 4억(마감하면 실적), 11·12월 예정 4억
- 결정(2026-10-28 사용자): 지급 관리(UI-20)를 없앤다. 지급은 모두 냈다고 보고 원천 자료에서 바로 계산하며, 실제와 안 맞는 금액은 재무 항목에 행을 추가해 맞춘다. 마감한 달 = 실적, 마감 전 달 = 예정. 줄별 출처: 인건비(정규직) = 정규직 실지급액(식-17). 계약직·프리랜서 = 계약직 실지급액 + 프리랜서 (급여 + 수당 − 3.3%)(식-49). 용역비 = 외주 원가 + 부가세 10%(식-49). 제경비 = 전달 급여의 4대보험(회사 + 본인)·원천세·근로소득세(다음 달 10일 납부라 다음 달) + 재무 항목 판관비·영업외비용(입출금일 달, 부가세 포함) + 퇴직금 지급 + 법인카드 대금 + 계좌이체 지출 중 구분 「계좌이체」(아래 결정). 미지급 인건비 = 계좌이체 지출 중 구분 「특별비」(이체일 달, 2026-10-30 E3). 지난달 「미지급」 이월은 없다. 예: 9월 4대보험·원천세 4,186만 → 10월 제경비, 10월 프리랜서 500만 − 16.5만 = 483.5만 → 10월 계약직·프리랜서.
- 결정(2026-10-28 사용자): 팀비·프로젝트경비·추가 직접 경비는 원가에는 들어가지만 실제로 나간 돈이 자금수지에 없었다. 그래서 재무 항목에 두 표를 둔다. ① 법인카드 대금: 카드 목록(20장 안팎, 늘어날 수 있음)에 결제월마다 카드별 금액을 넣고, 합계가 그 달 제경비에 들어간다. 자금수지 반영 합계는 직접 고칠 수 있다(고치면 카드 합계 대신 그 값). 카드는 지우지 않고 「삭제됨」으로 두며, 금액이 있는 달에는 계속 보인다. 재발급은 옛 카드 삭제 + 새 카드 추가. ② 계좌이체 지출: 날짜·내용·받는 곳·금액. 이체일 달 제경비에 들어간다. 두 표 모두 손익에는 넣지 않는다(원가에 이미 있음). 관리자 개인 법인카드 사용분은 판관비로 재무 항목에 넣는다. 영업 파트 이익금의 파트 법카(식-44)와는 별개다. 예: 10월 카드 합계 2,000,000 + 계좌이체 3,300,000 → 10월 제경비 +5,300,000, 영업이익 변화 없음.
- 관련 테이블: TB_HR_MON_PAY, TB_FI_ITEM, TB_FI_CARD, TB_FI_CARD_MON, TB_FI_CARD_PAY, TB_FI_XFER, TB_FI_TAX_PAY
- 추가 직접 경비(2026-10-28): 받는 곳 칸을 없앴고 지급 관리도 삭제했다. 실제로 나간 돈은 법인카드 대금 또는 계좌이체 지출로 제경비에 들어간다(위 결정). (이전 2026-10-25: 공용은 「제경비(기타)」 줄, 사람 경비는 급여 경비 정산)
- ~~결정 Q-식-45(사용자 2026-10-22): 제경비 = 지급 관리(UI-20)에서 제경비로 분류된 줄의 합이다. 재무 항목의 판관비·영업외비용은 저장할 때 지급 관리에 제경비 줄을 자동으로 만들고, 4대보험·원천세는 급여에서 만든다. 자금수지는 지급 관리만 보고 더하므로 임대료처럼 두 곳에 보이는 돈도 한 번만 들어간다. 시안의 “판관비 × 90% + 고정 금액”은 쓰지 않는다.~~ (2026-10-28 지급 관리 삭제로 바뀜, 위 결정 참고)', 'UI-21, UI-10', 48, 'SYSTEM'),
('식-49', 'SFR-907', '외주·프리랜서 지급액', '외주 지급액 = 공급가액 + 부가세(10%). 프리랜서 공급가액 = 그 달 급여(급여 업로드 지급액) + 수당(프리랜서는 0, 식-08). 프리랜서 지급액 = 공급가액 − 원천세(공급가액 × 3.3%). 모두 원 단위 버림', '- 쓰는 화면: UI-21
- 정의: 한 건에 실제로 보내는 돈이다(2026-10-28부터 지급 관리 없이 자금수지 지출 계산에 바로 씀). 외주 업체는 부가세를 더해서 주고, 프리랜서는 원천세 3.3%를 떼고 준다.
- 수식: `외주 지급액 = 공급가액 + 부가세(10%). 프리랜서 공급가액 = 그 달 급여(급여 업로드 지급액) + 수당(프리랜서는 0, 식-08). 프리랜서 지급액 = 공급가액 − 원천세(공급가액 × 3.3%). 모두 원 단위 버림`
- 예시: 외주 공급가액 8,000,000원 → 부가세 800,000원, 지급액 8,800,000원. 프리랜서 5,000,000원 → 원천세 165,000원, 지급액 4,835,000원.
- 예외·반올림: ~~지급 차수는 1차 10일, 2차 15일, 3차 20일, 4차 말일, 5차 예비이고 공통코드 「지급 차수」로 정해 모든 달에 같이 쓴다(Q-20-2). 자동으로 만든 줄의 차수는 지급 구분 공통코드의 기본 차수를 따른다: 프리랜서 2차, 외주 4차, 4대보험·원천세 1차, 임대료 3차.~~ (2026-10-28 지급 관리·지급 차수 삭제)
- 관련 테이블: TB_HR_MON_PAY (TB_FI_PAY는 2026-10-28, TB_FI_CASH는 2026-10-29 삭제)
- ~~경비 정산(2026-10-25): 프리랜서는 급여 이체가 아니라 지급 관리 줄로 받으므로, 지난달 그 사람 이름으로 쓴 추가 직접 경비를 급여 업로드 「경비 정산」 칸 값으로 이 줄에 더한다. 원천세 대상이 아니고(공급가액에 안 넣음), 원가에도 안 넣는다(쓴 달 직접 경비로 이미 잡힘, 식-11). 예: 박지훈 급여 500만, 10월 택시 6만 → 11월 지급액 = 500만 − 16.5만 + 6만 = 489.5만, 자금수지 「인건비(계약직·프리랜서)」 489.5만.~~ (2026-10-28 사용자 결정으로 삭제. 영수증 실비는 시스템 밖에서 따로 일괄 처리)
- 결정 Q-식-46(사용자 2026-10-21): 프리랜서 원천세는 (급여 + 수당) × 3.3%다. 프리랜서 공급가액을 급여 + 수당으로 정해 식-17과 같은 숫자가 되게 한다.
- ~~결정 Q-식-47(사용자 2026-10-21): 지급 구분마다 기본 차수를 공통코드에 둔다. 초기값은 시안(프리랜서 2차, 외주 4차, 4대보험·원천세 1차, 임대료 3차)이고 관리자가 바꿀 수 있다.~~ (2026-10-28 지급 관리 삭제)', 'UI-21', 49, 'SYSTEM'),
('식-50', 'SFR-907', '법인세 납부 예상', '확정 신고 예상액 = 연간 예상 법인세 − 중간예납', '- 쓰는 화면: UI-21, UI-10
- 정의: 법인세는 실제 납부하는 달에만 자금수지의 “법인세 납부” 줄로 잡는다. 3월에는 전년도 확정 신고, 8월에는 올해 중간예납이다. 확정 신고 예상액은 연간 예상액에서 중간예납을 뺀 값이다.
- 수식: `확정 신고 예상액 = 연간 예상 법인세 − 중간예납`
- 예시: 연간 예상 20,000,000원, 중간예납 8,000,000원 → 다음 해 3월 확정 신고 예상 12,000,000원.
- 예외·반올림: 손익(순이익표)의 월할 법인세와는 별개다. 손익은 식-31을 따른다.
- 관련 테이블: TB_FI_CORP_TAX(EST_TAX_AMT), TB_FI_TAX_PAY(PAY_AMT·PAY_DT·PAY_YN)
- 금액 저장과 납부 확인(2026-10-29 사용자 결정, 대조 13번): 금액은 TB_FI_TAX_PAY.PAY_AMT 한 곳에만 저장한다. 중간예납(MID) 금액은 재무가 입력한다(비면 0). 확정 신고(CNFM) PAY_AMT가 NULL이면 위 식으로 자동 계산해 보여 주고, 사람이 고친 값이 있으면 그 값을 쓴다. 납부일이 지나도 저절로 납부가 되지 않는다. [납부 확인]으로 실제 납부일(PAY_DT)·실제 납부액(PAY_AMT)을 넣으면 PAY_YN=Y. 납부 확인하면 낸 법인세 금액이 그대로 저장되고(확정 신고도 숫자로 저장, 빈칸으로 두지 않음), 그 뒤로는 연간 예상액을 고쳐도 바뀌지 않는다. 납부 확인한 줄은 고칠 수 없다(2026-10-30 B2). 자금수지 「법인세 납부」 = PAY_YN=Y면 PAY_DT 달에 PAY_AMT, N이면 PAY_DUE_DT 달에 예상 금액. 납부일이 지났는데 N이면 화면에 「확인 필요」와 대시보드 경고.
- 예시(납부 확인): 연간 82,000,000원, 중간예납 10,000,000원 입력 → 확정 신고 자동 72,000,000원(2027-03 자금수지 예정). 3월 31일 실제 72,500,000원 납부 → [납부 확인]에 72,500,000원 입력 → 3월 자금수지 법인세 납부 72,500,000원. 확인을 잊으면 4월 1일부터 「확인 필요」.
- 새 해 줄 자동 생성(2026-10-29 사용자 결정): 그 해 재무 항목을 처음 열 때 그 과세연도 줄이 없으면 만든다. TB_FI_CORP_TAX(그 해, EST_TAX_AMT = 작년 값, 작년 값도 없으면 0), TB_FI_TAX_PAY 두 줄 = MID(납부예정일 그 해 8월 31일, PAY_AMT NULL, PAY_YN N)·CNFM(다음 해 3월 31일, PAY_AMT NULL=자동, PAY_YN N). 납부예정일은 고칠 수 있다. 화면에 보이는 줄 = 작년 CNFM 1줄 + 올해 MID·CNFM 2줄 + 그보다 전인데 아직 납부 확인(PAY_YN=Y) 안 한 줄(확인할 때까지 해가 바뀌어도 남음).
- 결정 Q-10-2(사용자 2026-10-21): 확정 신고 예상액은 위 식으로 자동 채우되 사람이 고칠 수 있다(세무사 실제 계산액 반영). 고친 값은 자금수지 납부 예정에만 쓰고 월할 법인세(식-31)는 바꾸지 않는다.', 'UI-21, UI-10', 50, 'SYSTEM'),
('식-51', 'SFR-907', '자금 부족 경고', '경고 = 예정 달 이월금 < 최소 운영자금(운영 기준값 MIN_CASH, 처음 10억 원)', '- 쓰는 화면: UI-21, UI-16
- 정의: 예정 달의 월말 잔액이 기준보다 낮아질 것으로 보이면 경고한다. 시안은 “최소 운영자금 10억 원 미만”이면 경고하고 대시보드에도 올린다.
- 수식: `경고 = 예정 달 이월금 < 최소 운영자금(운영 기준값 MIN_CASH, 처음 10억 원)`
- 예시: 최소 운영자금 10억 원, 11월 예상 이월금 950,000,000원 → 경고.
- 예외·반올림: 실적(마감한 달)에는 경고하지 않고 예정 달만 본다.
- 관련 테이블: TB_BL_RCPT, TB_HR_MON_PAY, TB_FI_ITEM, TB_FI_LOAN, TB_FI_CARD_MON, TB_FI_XFER, TB_FI_TAX_PAY, TB_SY_OPER_STD(최소 운영자금)
- 결정 Q-식-48(사용자 2026-10-21): 시안대로 “최소 운영자금 미만”이면 경고한다. v2의 “0 미만”은 쓰지 않는다. 금액은 관리자가 운영 기준값에서 바꾼다.', 'UI-21, UI-16', 51, 'SYSTEM'),
('식-52', 'SFR-907', '반기·연간 합계', '항목 합계 = 기간 안 월 값의 합. 기간 전월이월금 = 첫 달 전월이월금. 기간 이월금 = 마지막 달 이월금', '- 쓰는 화면: UI-21
- 정의: 자금수지표 오른쪽의 상반기·하반기·연간 칸이다. 수입·지출 항목은 합하고, 전월이월금은 기간 첫 달 값, 이월금은 기간 마지막 달 값을 쓴다.
- 수식: `항목 합계 = 기간 안 월 값의 합. 기간 전월이월금 = 첫 달 전월이월금. 기간 이월금 = 마지막 달 이월금`
- 예시: 1~3월 수입 3억·4억·5억 원 → 합계 12억 원. 1월 전월이월금 10억, 3월 이월금 11억이면 기간 칸은 10억 → 11억.
- 예외·반올림: 이월금을 합하면 안 된다.
- 관련 테이블: TB_BL_RCPT, TB_HR_MON_PAY, TB_FI_ITEM, TB_FI_LOAN, TB_FI_CARD_MON, TB_FI_XFER, TB_FI_TAX_PAY', 'UI-21', 52, 'SYSTEM'),
('식-53', 'SFR-908', '턴키 배분표 단계 선택', '쓰는 값 = 실제 투입계획 > 최종 계약 > 견적 순으로 있는 값', '- 쓰는 화면: UI-01, UI-04
- 정의: 턴키 개인 단가는 사람 × 월 배분표로 정한다. 견적, 최종 계약, 실제 투입계획 세 단계 중 가장 나중 단계 값을 쓴다. 항목 줄(제경비·기술료 등)은 프로젝트 매출에만 더하고 개인 단가에는 넣지 않는다.
- 수식: `쓰는 값 = 실제 투입계획 > 최종 계약 > 견적 순으로 있는 값`
- 예시: A 개발자 11월: 견적 6,000,000원, 최종 계약 6,500,000원, 실제 투입계획 7,000,000원 → 7,000,000원. 실제 투입계획이 없으면 6,500,000원.
- 예외·반올림: 단계는 업로드한 표 단위로 정한다(Q-식-50).
- 관련 테이블: TB_PJ_TK_DSTRB.DSTRB_STEP_CD
- 결정 Q-식-49(사용자 결정 2026-10-21): 배분표의 기술료·컨설팅비 등 항목 줄은 지금 규칙대로 프로젝트 매출에 넣고 개인 단가에서 뺀다. 회사 밖으로 나가는 비용인지는 데이터를 넣는 사람이 판단한다.
- 결정 Q-식-50(사용자 결정 2026-10-21): 배분표는 엑셀 업로드로만 넣으므로 칸마다 단계를 고르는 화면은 없다. 단계는 업로드한 표 단위로 정해지고, 계산에는 가장 나중 단계의 표를 쓴다.', 'UI-01, UI-04', 53, 'SYSTEM'),
('식-54', 'SFR-908', '배분표 합계 점검', '차이 = 현재 계약금액 − (사람 줄 금액 합 + 항목 줄 금액 합). 차이 ≠ 0이면 경고', '- 쓰는 화면: UI-01
- 정의: 배분표(사람 줄 + 항목 줄)의 합계가 현재 계약금액과 다르면 경고한다.
- 수식: `차이 = 현재 계약금액 − (사람 줄 금액 합 + 항목 줄 금액 합). 차이 ≠ 0이면 경고`
- 예시: 계약금액 5억 원, 사람 줄 462,000,000원 + 항목 줄 28,000,000원 = 490,000,000원 → 차이 10,000,000원, 경고.
- 예외·반올림: 저장은 막지 않고 경고만 한다(v2 표현 기준).
- 관련 테이블: TB_PJ_TK_DSTRB, TB_PJ_PROJ
- 결정 Q-식-51(사용자 결정 2026-10-21): 허용 오차 없이 1원이라도 다르면 경고하고, 저장은 막지 않는다.
- 추가(2026-10-24 사용자 결정): 배정 불일치 점검. 지금 계산에 쓰는 배분표의 사람 줄과 투입 배정을 비교한다. ① 배정 없음 = 배분표 그 달 금액 > 0인데 그 사람의 그 달 월 MM이 0. ② 배분표 없음 = 그 달 월 MM > 0인 사람이 배분표에 줄이 없음(이 사람 단가는 식-55의 대체식). 저장·계산은 막지 않고 실제 투입계획을 다시 올리라고 알린다. 예: B를 3월에 철수시켰는데 배분표에는 10월까지 있으면 「B · 배정 없음 · 4~10월」.', 'UI-01', 54, 'SYSTEM'),
('식-55', 'SFR-908', '턴키 실제 MM과 개인 단가', '실제 MM = Σ 월별 투입 MM. 개인 월 단가 = 그 달 배분표 금액(식-53 결과). 배분표가 없거나 그 사람 줄이 없으면 그 달 배분 원가 × 현재 계약금액 ÷ 예산', '- 쓰는 화면: UI-01, UI-03, UI-04
- 정의: 턴키의 실제 MM은 금액이 있는 칸의 개수가 아니라 월별 투입 MM의 합이다. 개인 월 단가는 그 사람의 월 배분 금액이다. 엑셀은 칸 개수를 세어 보름만 일한 달도 1MM이 된다.
- 수식: `실제 MM = Σ 월별 투입 MM. 개인 월 단가 = 그 달 배분표 금액(식-53 결과). 배분표가 없거나 그 사람 줄이 없으면 그 달 배분 원가 × 현재 계약금액 ÷ 예산`
- 결정(2026-10-24 사용자): 개인 원가·수익표(UI-03) 턴키 단가도 이 식을 쓴다. 이전 시안은 개발자 풀 월 단가 × MM을 써서 턴키 이익이 실제와 다르게 보였다. 개발자 풀 월 단가는 턴키 매출에 쓰지 않는다.
- 예시: A 개발자 9월 0.5MM(15일), 10월 1.0MM, 11월 1.0MM → 실제 MM = 2.50(칸 3개가 아님). 이후 MM당 평균은 식-24.
- 예외·반올림: 무상지원 MM은 실제 MM에 들어가지만 매출이 0이다.
- 관련 테이블: TB_PJ_ASGN_MON, TB_PJ_TK_DSTRB', 'UI-01, UI-03, UI-04', 55, 'SYSTEM'),
('식-56', 'SFR-908', '추가 계약 반영', '현재 계약금액 = 원 계약금액 + 추가 계약금액. 목표 원가 = 현재 계약금액 × (1 − 목표 마진율) (BDGT_MANUAL_YN=N일 때만)', '- 쓰는 화면: UI-01, UI-04, UI-17
- 정의: 추가 계약은 청구(취소 안 된 TB_BL_BILL)가 생긴 뒤 프로젝트 관리(UI-01) [수정]에서 계약금액을 바꾼 것이다(B11). 따로 「확정」 단계나 예정 상태는 없고 저장하면 바로 반영된다. 추가 계약이 생기면 현재 계약금액과 MM에 더하고 배분표를 다시 맞추게 한다. 목표 원가(예산)는 수기로 고치지 않은 경우(BDGT_MANUAL_YN=N)만 새 계약금액으로 다시 계산하고, 수기로 고친 예산(Y)은 그대로 둔다(담당자가 직접 다시 고침, 2026-10-30 B16).
- 수식: `현재 계약금액 = 원 계약금액 + 추가 계약금액. 목표 원가 = 현재 계약금액 × (1 − 목표 마진율) (BDGT_MANUAL_YN=N일 때만)`
- 예시: 원 계약 5억 원 + 추가 계약 0.8억 원 → 현재 계약금액 580,000,000원, 목표 원가 464,000,000원. 예산을 수기로 4.5억 원으로 고쳐 둔 프로젝트는 450,000,000원 그대로.
- 예외·반올림: 운영 전환은 새 프로젝트로 등록한다(앞 프로젝트와 잇는 칸은 두지 않음, 2026-10-30 B21).
- 원 계약금액(2026-10-29 B11): 등록·수주 처리 때 계약금액을 그대로 저장. 청구(취소 안 된 TB_BL_BILL, CNCL_YN=''N'')가 하나도 없을 때 계약금액을 고치면 정정으로 보고 원 계약금액도 같이 바꾸고, 청구가 하나라도 생긴 뒤에 고치면 현재 계약금액만 바뀐다(차이 = 추가 계약금액). 청구 확정은 v2라 보지 않는다(2026-10-29 D9 때 정정. 청구 확정 칸은 두지 않음, 2026-10-30 D19).
- 관련 테이블: TB_PJ_PROJ_CHG, TB_PJ_PROJ(ORGNL_CTRT_AMT·CTRT_AMT·BDGT_AMT·BDGT_MANUAL_YN), TB_BL_BILL
- 결정 C3(사용자 2026-10-31): 명세서의 「예정 상태의 추가 계약은 인력 예측에만 쓴다」는 뺀다. 예정/확정을 고르는 칸·화면이 없고 인력 예측 식(식-61)도 쓰지 않는다. 추가될 인력을 미리 잡으려면 프로젝트 상세 [인원 계획] 탭에서 계획 MM을 늘린다(미충원으로 식-61 수요에 잡힘).
- 결정 Q-식-52(사용자 결정 2026-10-21): 30% 이상 차이를 “할인/컨소시엄 지분”으로 구분하는 표시는 v2에만 있고 시안에 없어 만들지 않는다.', 'UI-01, UI-04, UI-17', 56, 'SYSTEM'),
('식-57', 'SFR-909', '금액 버림과 끝자리 보정', '각 줄 = 버림(원래 금액 × 비율). 마지막 줄 = 원래 금액 − 앞 줄들의 합', '- 쓰는 화면: 전 화면(UI-03, UI-04, UI-13, UI-19 등)
- 정의: 금액은 원 단위 버림이다. 하나를 여러 줄로 나눌 때 생기는 끝자리는 마지막 줄에 모아 줄 합계가 원래 금액과 같게 맞춘다.
- 수식: `각 줄 = 버림(원래 금액 × 비율). 마지막 줄 = 원래 금액 − 앞 줄들의 합`
- 예시: 100원을 3등분 → 33원, 33원, 마지막 줄 34원. 월 인건비 4,970,000원을 1.0:0.5로 나누면 3,313,333원과 1,656,667원(식-13).
- 예외·반올림: 음수 금액은 0 쪽으로 자른다(−933,333.2 → −933,333, Q-식-39). 부가세는 식-05.
- 관련 테이블: 전 금액 컬럼(NUMERIC(15))
- 결정 Q-식-53(사용자 2026-10-21): 명세서대로 원 단위 버림 + 나눈 끝자리는 마지막 줄에 모은다. 청구 쪽 Q-식-3(건별 버림 후 합산)과 같은 규칙.', '전 화면(UI-03, UI-04, UI-13, UI-19 등)', 57, 'SYSTEM'),
('식-58', 'SFR-909', 'MM 반올림', 'MM = round(계산값, 소수 2자리). 금액 = 반올림한 MM × 단가', '- 쓰는 화면: UI-12, UI-13, UI-19
- 정의: MM은 소수 둘째 자리까지 반올림한다. 계산한 뒤 곱하는 금액은 반올림한 MM으로 계산한다.
- 수식: `MM = round(계산값, 소수 2자리). 금액 = 반올림한 MM × 단가`
- 예시: 30일 중 23일 투입 → 23 ÷ 30 = 0.7666… → 0.77MM. 단가 700만 원이면 5,390,000원.
- 예외·반올림: 월 합계가 1.0을 넘는지 볼 때도 반올림한 값을 쓴다.
- 관련 테이블: MM 컬럼(NUMERIC(6,2))
- 결정 Q-식-54(사용자 2026-10-20): 금액은 반올림한 MM으로 곱한다(시안대로).', 'UI-12, UI-13, UI-19', 58, 'SYSTEM'),
('식-59', 'SFR-909', '마감 후 정정(전월 조정)', '이번 달 전월 조정 = 마감한 달의 정정 차액. 영업이익 = 매출총이익 − 판관비 + 전월 조정', '- 쓰는 화면: UI-10(입력), UI-08, UI-09, UI-14
- 정의: 마감한 달은 고치지 않는다. 틀린 것을 나중에 알면 그 차액을 이번 달 “전월 조정” 줄로 반영한다. 큰 오류는 관리자가 마감을 풀고(사유 기록) 고친다.
- 수식: `이번 달 전월 조정 = 마감한 달의 정정 차액. 영업이익 = 매출총이익 − 판관비 + 전월 조정`
- 예시: 9월 마감 뒤 9월 급여 100,000원 누락 발견 → 9월 숫자는 그대로, 10월 “전월 조정”에 −100,000원을 넣는다. 10월 영업이익은 10만 원 줄어든다. 9월 매출 50,000원 누락이면 +50,000원.
- 예외·반올림: 마감한 달의 데이터는 삭제·수정할 수 없다.
- 관련 테이블: TB_CO_PREV_ADJ, TB_CO_MON_PL.PREV_ADJ_AMT
- 결정 Q-식-55(사용자 2026-10-21): 부호는 이익 기준이다. 영업이익이 늘어나는 조정은 +, 줄어드는 조정은 −로 적고 영업이익에 그대로 더한다(시안·테이블 정의서와 같음).
- 결정 35번 1(사용자 2026-10-22): 입력은 재무 항목(UI-10)의 「전월 조정」 표에서 한다. 칸: 대상 달(마감한 달), 조정 항목(매출·원가·판관비·기타), 금액(±), 사유.', 'UI-10(입력), UI-08, UI-09, UI-14', 59, 'SYSTEM'),
('식-60', 'SFR-910', '인력 공급(월·직무별)', '사람별 공급 = max(0, 1 − 확정 투입 MM − 철수한 건의 MM × 연장 확률). 그 달 투입이 하나도 없는 사람은 1. 직무 공급 = 사람별 합, 소수 첫째 자리 반올림', '- 쓰는 화면: UI-17, UI-28, UI-16, UI-29
- 정의: 그 달 그 직무에서 새 일을 맡을 수 있는 사람 수(MM)다. 휴직이 아니고 그 달에 퇴사하지 않은 직접 인력(퇴사한 다음 달부터 제외, 2026-10-27 사용자 결정)마다 1에서 그 달 확정 투입 MM을 뺀다(지금 대기 중이어도 그 달에 시작하는 확정 투입이 있으면 뺀다, 가배정은 빼지 않음). 철수 예정자는 연장 가능성만큼 계속 일하는 것으로 보고 그만큼 뺀다.
- 수식: `사람별 공급 = max(0, 1 − 확정 투입 MM − 철수한 건의 MM × 연장 확률). 그 달 투입이 하나도 없는 사람은 1. 직무 공급 = 사람별 합, 소수 첫째 자리 반올림`
- 예시: A 개발자는 0.5MM 투입 중 → 0.5. B 개발자는 대기 → 1.0. C 개발자는 11월 말 철수, 연장 가능성 “높음”(80%), 12월 → 1 − 1.0 × 0.8 = 0.2. 직무 공급 = 1.7.
- 예외·반올림: 연장 확률은 높음 80%, 보통 50%, 낮음 0%이고 미정이면 50%로 본다(시안 동작).
- 관련 테이블: TB_PJ_ASGN, TB_SY_OPER_STD(연장 확률)
- 결정 Q-식-56(사용자 2026-10-22): 명세서대로 철수 예정자의 연장될 몫은 공급에서 뺀다("남는다"는 그 프로젝트에 남는다는 뜻).
- 결정 Q-식-57(사용자 2026-10-22): 앞으로 시작할 확정 투입도 그 달 공급에서 뺀다. 예: 지금 대기인 김 개발자가 12월 1일부터 1.0MM 확정 → 12월 공급 0. 시안 v1.4 계산도 이렇게 고쳤다.
- 결정 Q-17-4(사용자 2026-10-22): 연장 가능성 미정은 50%(운영 기준값).', 'UI-17, UI-28, UI-16, UI-29', 60, 'SYSTEM'),
('식-61', 'SFR-910', '인력 수요(월·직무별)', '수요 = Σ (필요 인원 × 월 MM × 수주 확률) + Σ 진행 프로젝트 미충원 MM', '- 쓰는 화면: UI-17, UI-28, UI-29, UI-16, UI-33(부족 직무, 미충원 MM은 빼고 영업 기회 수요만 씀 2026-10-25), UI-05(부족 직무 안내), UI-26(인력 과부족 답)
- 정의: 그 달 그 직무에서 필요해질 사람 수(MM)다. 진행 중 영업 기회마다 필요 인력 × 월 MM × 수주 확률을 더하고, 진행 프로젝트에서 계획보다 덜 배정된 몫(미충원)을 더한다.
- 수식: `수요 = Σ (필요 인원 × 월 MM × 수주 확률) + Σ 진행 프로젝트 미충원 MM`
  - `미충원(프로젝트·직무·달) = max(0, 계획 MM − 확정 배정 MM) ÷ 남은 개월`, 남은 개월 = 다음 달부터 프로젝트 종료 달까지. 그 기간 안의 달에만 더하고 확률은 100%.
  - 수주 확률: 발굴 10%, 제안 30%, 협상 60%, 계약 직전 90%
- 예시: 기회 A는 백엔드 3명 × 1.0MM, 확률 60% → 1.8. 기회 B는 2명 × 1.0MM, 확률 30% → 0.6. 진행 프로젝트 P는 백엔드 계획 12MM, 확정 배정 9MM, 남은 기간 11~1월 → 미충원 3 ÷ 3 = 달마다 1.0. 11월 백엔드 수요 = 1.8 + 0.6 + 1.0 = 3.4.
- 예외·반올림: 필요 인력은 시작월부터 기간(개월) 동안만 센다. 단계를 바꾸면 확률이 단계 기본값으로 바뀌고 고칠 수 있다.
  - 미충원 대상: 상태 "진행"이고 계획 MM을 넣은 프로젝트·직무만. 수주예정·내부·계획이 빈 직무는 세지 않는다. 종료 달이 지났으면 0.
  - 확정 배정 MM = 그 프로젝트·직무의 투입 배정 MM 전체 합(지난 달 + 앞으로, [인원 계획] 탭 "실제 MM"과 같은 값). 가배정은 넣지 않는다(가배정된 사람은 공급에 그대로 있어 서로 맞는다).
  - 계획보다 많이 배정된 직무(초과)는 0으로 보고 수요에서 빼지 않는다.
  - 더 투입하지 않기로 한 프로젝트는 PM이 계획 MM을 실제에 맞게 줄인다(운영 규칙, [인원 계획] 탭 안내).
- 관련 테이블: TB_SL_OPP, TB_SL_OPP_NEED, TB_PJ_MNPW_PLAN(PLAN_MM), TB_PJ_ASGN, TB_SY_OPER_STD(수주 확률)
- 결정 Q-식-58·Q-28-2(사용자 2026-10-22): 진행 프로젝트 미충원을 수요에 더한다(B). 영업 기회를 수주 처리하면 기회 수요는 빠지지만, 프로젝트 계획 MM이 미충원으로 이어서 잡혀 필요 인력이 사라지지 않는다(34번). 수주 처리 때 기회의 필요 인력으로 인원 계획이 자동으로 만들어지므로(SFR-129-09, SRC_CD=OPP) PM이 따로 넣지 않아도 바로 이어진다.', 'UI-17, UI-28, UI-29, UI-16, UI-33(부족 직무, 미충원 MM은 빼고 영업 기회 수요만 씀 2026-10-25), UI-05(부족 직무 안내), UI-26(인력 과부족 답)', 61, 'SYSTEM'),
('식-62', 'SFR-910', '남는 인원·부족 인원', '남는 인원 = 공급 − 수요. 부족 표시: 남는 인원 < −0.4', '- 쓰는 화면: UI-17, UI-28, UI-16
- 정의: 공급에서 수요를 빼 남는지 모자라는지 본다. 음수면 부족이다.
- 수식: `남는 인원 = 공급 − 수요. 부족 표시: 남는 인원 < −0.4`
- 예시: 공급 3.0, 수요 1.8 → +1.2명 남음. 공급 2.0, 수요 2.8 → -0.8명 부족.
- 예외·반올림: 시안은 −0.4 아래를 “부족”(빨강), +1.0 위를 “남음”(주황)으로 색칠한다.
- 결정 Q-28-1(사용자 2026-10-22): 예상 대기 비용은 v2에만 있고 시안에 없어 쓰지 않는다.
- 관련 테이블: TB_PJ_ASGN, TB_SL_OPP_NEED, TB_HR_MON_PAY
- 결정 Q-17-3·Q-식-59(사용자 2026-10-22): 부족 0.4명(SHORT_PERSON)과 남음 1.0명(SURPLUS_PERSON) 모두 운영 기준값. 0.4는 UI-33 부족 직무 판정과 채용 시작 권장월(식-93)에도 같이 쓴다.', 'UI-17, UI-28, UI-16', 62, 'SYSTEM'),
('식-63', 'SFR-910', '남는 MM·비는 인원(보드)', '남는 MM = Σ max(0, 1 − 그 달 투입 MM 합). 비는 인원 = 그 달 상태가 “비어 있음”인 인원', '- 쓰는 화면: UI-28
- 정의: 인력 운영 보드 맨 아래 두 줄이다. “남는 MM”은 사람마다 1에서 그 달 MM을 뺀 값(0 미만이면 0)을 더한다. “비는 인원”은 그 달 비어 있는 사람 수다.
- 수식: `남는 MM = Σ max(0, 1 − 그 달 투입 MM 합). 비는 인원 = 그 달 상태가 “비어 있음”인 인원`
- 예시: 세 사람이 0.5MM, 0.7MM, 1.0MM 투입 → 남는 MM = 0.8. 투입이 하나도 없는 사람 2명이면 비는 인원 2명.
- 예외·반올림: 휴직자는 남는 MM에 넣지 않는다. 지난 달은 “–”로 비운다(오늘 이후만 계산).
- 관련 테이블: TB_PJ_ASGN', 'UI-28', 63, 'SYSTEM'),
('식-64', 'SFR-910', '고정비 비율', '고정비 비율 = (정규직 + 계약직 인건비) ÷ 전체 인건비 × 100', '- 쓰는 화면: UI-16
- 정의: 고정비 비율은 전체 인건비 중 정규직·계약직 인건비의 비율이다.
- 수식: `고정비 비율 = (정규직 + 계약직 인건비) ÷ 전체 인건비 × 100`
- 예시: 정규·계약 인건비 1.8억 원, 전체(프리랜서·외주 포함) 2.4억 원 → 75.0%.
- 예외·반올림: 직무별 대기 개월 수는 만들지 않는다.
- 관련 테이블: TB_HR_MON_PAY(EMP_TYPE_CD 그 달 직원구분, 2026-10-31 C17)
- 결정 Q-식-60(사용자 2026-10-22): 식도 시안도 없어 직무별 대기 개월 수는 만들지 않는다. 같은 내용은 보드 맨 아래 비는 인원·남는 MM(식-63)으로 본다.', 'UI-16', 64, 'SYSTEM'),
('식-66', 'SFR-910', '직무·등급별 인당 공헌이익', '인당 공헌이익 = Σ 개인 영업이익 ÷ 그 칸의 인원', '- 쓰는 화면: UI-03
- 정의: 개인 원가·수익표 위쪽의 접이식 표다. 직무와 등급이 같은 사람들의 개인 영업이익을 평균 낸다. 칸마다 인원 수를 함께 적는다.
- 수식: `인당 공헌이익 = Σ 개인 영업이익 ÷ 그 칸의 인원`
- 예시: 백엔드 중급 3명의 개인 영업이익이 1,930,000원, 1,500,000원, −400,000원 → 평균 1,010,000원(3명).
- 예외·반올림: “전체” 열은 등급 구분 없이 같은 직무의 평균이다. 턴키 단가 미포함 보기에서는 그 보기의 영업이익을 쓴다.
- 관련 테이블: TB_CO_MON_COST, TB_HR_DEV(직무·등급)', 'UI-03', 66, 'SYSTEM'),
('식-67', 'SFR-910', '인원 계획 대비 투입 차이', '차이 = 실제·예정 MM − 계획 MM. 차이 < 0이면 부족, > 0이면 초과, = 0이면 맞음', '- 쓰는 화면: UI-11
- 정의: 프로젝트·직무별로 한 해의 계획 MM과 실제(예정 포함) MM을 견준다. 실제가 모자라면 부족, 넘치면 초과, 같으면 맞음이다.
- 수식: `차이 = 실제·예정 MM − 계획 MM. 차이 < 0이면 부족, > 0이면 초과, = 0이면 맞음`
- 예시: 계획 12.0MM, 실제 11.5MM → 차이 -0.5, 부족 0.50. 실제 12.4MM이면 초과 0.40. 계획을 넣지 않은 직무는 계획 0으로 본다(빈 칸 저장 = 0, 2026-10-29): 실제 3.0MM이면 초과 3.00, 실제도 0이면 UI-11에 줄이 나오지 않는다.
- 예외·반올림: 실제 MM은 월 MM(그 해에 걸친 달만)의 합이다. 계획 MM은 프로젝트 상세 [인원 계획] 탭에서 직무별로 입력한 값(SFR-101-11)이고, UI-11에서는 그 해 몫 = 계획 MM × (그 해에 걸친 프로젝트 개월 수 ÷ 전체 프로젝트 개월 수)를 소수 둘째 자리로 반올림해 쓴다. 프로젝트 상세에서는 계획 MM 전체와 프로젝트 전체 실제 MM을 견준다.
- 관련 테이블: TB_PJ_MNPW_PLAN, TB_PJ_ASGN_MON
- 결정 Q-식-62(사용자 결정 2026-10-21): 시안의 계획 MM은 실제 MM에서 임의로 ±2를 뺀 가짜 값이다. 실제로는 프로젝트 상세 [인원 계획] 탭에서 사람이 입력한 계획 MM을 쓴다(Q-11-1).', 'UI-11', 67, 'SYSTEM'),
('식-68', 'SFR-911', 'AI 추천 총점', '총점 = 직무 25 + 스킬 20 + 고객사·업종 15 + 등급 10 + 과거 성과 5 + 비용 효과 5 + 투입 가능일 10 + 근무지 10 (각 항목은 배점 이하)', '- 쓰는 화면: UI-30
- 정의: 시스템이 8개 항목을 점수로 계산해 더한 100점 만점 값이다. AI는 조건을 해석하고 이유를 설명할 뿐 점수를 정하지 않는다. 높은 순 5명을 보여 준다(Q-30-1). 8개 항목은 명세서의 2026-10 결정과 시안을 따른다(28번 0번 사용자 결정 2026-10-22).
- 수식: `총점 = 직무 25 + 스킬 20 + 고객사·업종 15 + 등급 10 + 과거 성과 5 + 비용 효과 5 + 투입 가능일 10 + 근무지 10 (각 항목은 배점 이하)`
- 예시: 예: 직무 일치 25 + 스킬 14 + 같은 고객사 15 + 등급 한 단계 차이 5 + 과거 성과 4 + 비용 효과 4 + 투입 가능일 10 + 근무지 10 = 87점.
- 예외·반올림: 배점은 운영 기준값 AI_SCORE_JOB·SKILL·CUST·GRADE·PERF·COST·AVAIL·LOC 8개이고, 합계가 100이 아니면 저장을 막는다(2026-10-29, 06 §6). 나이·성별·학력 등은 쓰지 않는다. 추천은 참고용이고 배정은 담당자가 정한다.
- 관련 테이블: TB_SL_RCMD_LOG, TB_SY_OPER_STD(AI 배점)
- 결정 Q-식-63(사용자 2026-10-22): 동점이면 가용일 점수가 높은 사람 먼저, 그래도 같으면 관리번호 순.', 'UI-30', 68, 'SYSTEM'),
('식-69', 'SFR-911', 'AI 추천 제외 조건', '제외 = 휴직·퇴사 예정자, 시작월 확정 투입 MM이 이미 1.0인 사람, 파견 자리의 프리랜서·외주, 지금 고른 기회에 이미 가배정된 사람', '- 쓰는 화면: UI-30
- 정의: 점수를 매기기 전에 아래에 해당하는 사람은 후보에서 뺀다.
- 수식: `제외 = 휴직·퇴사 예정자, 시작월 확정 투입 MM이 이미 1.0인 사람, 파견 자리의 프리랜서·외주, 지금 고른 기회에 이미 가배정된 사람`
- 예시: 시작월 11월에 이미 1.0MM인 정규직 → 제외. 0.5MM만 찬 정규직 → 후보(남는 몫 0.5).
- 예외·반올림: “제외” 버튼으로 담당자가 추천 결과에서 직접 뺄 수도 있다.
- 파견 자리 판정: 추천 대상이 영업 기회면 TB_SL_OPP.CTRT_TYPE_CD, 프로젝트면 TB_PJ_PROJ.CTRT_TYPE_CD가 DP(파견)일 때. 직원구분 프리랜서·외주는 TB_HR_DEV.EMP_TYPE_CD로 본다.
- 관련 테이블: TB_HR_DEV_STAT, TB_PJ_ASGN, TB_SL_TENT_ASGN, TB_SL_OPP, TB_PJ_PROJ, TB_HR_DEV
- 결정 C13(2026-10-30): 영업 기회가 대상일 때도 기회 계약유형이 파견이면 프리랜서·외주를 뺀다(명세서 v1.9 「파견 자리에는 프리랜서·외주가 후보에 오르지 않는다」, v2.1 식-69). 시안 v1.6 반영.
- 결정 Q-식-64(사용자 2026-10-22): 명세서대로 뺀다. MM은 시작월의 확정 투입만 센다(가배정 제외). 다른 기회에 가배정된 사람은 빼지 않고 카드에 “가배정 있음”을 보인다(Q-30-4). 시안 v1.4 반영.', 'UI-30', 69, 'SYSTEM'),
('식-70', 'SFR-911', 'AI 추천 항목별 점수 규칙', '직무: 주·보조 직무 일치 25, 관련 직무 12, 그 외 3 / 스킬: 반올림(16 × 필요 스킬 중 보유 비율) + 프로젝트 특징에 보유 스킬 언급 시 4(최대 20) / 고객·업종: 같은 고객사 15, 같은 업종 9, 그 외 6 / 등급: 일치 10, 한 단계 5, 그 외 2 / 성과: 반올림(5 × 끝난 투입 중 연장된 비율) − 인력 사정 조기 철수 건수 × 2, 0~5, 끝난 투입 없으면 3 / 비용: 반올림(5 − (원가 ÷ 단가 − 0.6) × 10), 1~5 / 가용일: 대기·시작월 전 철수 10, 연장 가능성 낮음 6, 그 외 2 / 근무지: 프로젝트 근무지 주소의 맨 앞 시·도와 개발자 풀 거주지의 시·도가 같으면 10, 다르면 6, 근무지 없음 5(시·도 이름은 「서울특별시」→「서울」, 「경기도」→「경기」처럼 맞춰 비교. 맞추는 기준은 TB_SY_ADM_AREA의 시·도 정식·줄임 이름, SFR-021-06(2026-10-31 C9, 점수는 그대로). 원격(재택) 10점 규칙 없앰, 2026-10-27)', '- 쓰는 화면: UI-30
- 정의: 항목별 점수 규칙이다(28번 문서 사용자 결정 2026-10-22).
- 수식: `직무: 주·보조 직무 일치 25, 관련 직무 12, 그 외 3 / 스킬: 반올림(16 × 필요 스킬 중 보유 비율) + 프로젝트 특징에 보유 스킬 언급 시 4(최대 20) / 고객·업종: 같은 고객사 15, 같은 업종 9, 그 외 6 / 등급: 일치 10, 한 단계 5, 그 외 2 / 성과: 반올림(5 × 끝난 투입 중 연장된 비율) − 인력 사정 조기 철수 건수 × 2, 0~5, 끝난 투입 없으면 3 / 비용: 반올림(5 − (원가 ÷ 단가 − 0.6) × 10), 1~5 / 가용일: 대기·시작월 전 철수 10, 연장 가능성 낮음 6, 그 외 2 / 근무지: 프로젝트 근무지 주소의 맨 앞 시·도와 개발자 풀 거주지의 시·도가 같으면 10, 다르면 6, 근무지 없음 5(시·도 이름은 「서울특별시」→「서울」, 「경기도」→「경기」처럼 맞춰 비교. 맞추는 기준은 TB_SY_ADM_AREA의 시·도 정식·줄임 이름, SFR-021-06(2026-10-31 C9, 점수는 그대로). 원격(재택) 10점 규칙 없앰, 2026-10-27)`
- 예시: 스킬: 백엔드 필요 스킬 5개 중 3개 보유 → 반올림(16 × 0.6) = 10, 특징에 Java가 있고 Java 보유 → 14점. 성과: 끝난 투입 4건 중 3건 연장 → 반올림(3.75) = 4점, 인력 사정 조기 철수 1건이면 2점. 비용: 원가율 70% → 4점.
- 예외·반올림: 관련 직무는 공통코드 JOB의 ADD_VAL1, 직무별 필요 스킬은 공통코드 SKILL의 ADD_VAL1(쓰는 직무). 원가는 개인 원가(식-10), 단가는 기준 단가. 급여를 볼 수 없는 역할에는 비용 근거 숫자를 숨긴다. 과거 프로젝트 기술 스택(v2)과 날짜 단위 가용일 감점(v2)은 쓰지 않는다. 평가 입력 화면은 만들지 않는다(Q-30-2). 추천 대상이 영업 기회면 근무지는 TB_SL_OPP.WORK_PLC_ADDR, 특징은 TB_SL_OPP.RMRK로 같은 규칙을 쓴다(2026-10-29 C12). 등급 점수는 추천 조건에서 고른 등급(항상 있음)과 후보 등급을 비교하고, 후보 등급이 비어 있으면 「그 외」 2점(시안 그대로). 고른 등급은 추천 기록 REQ_GRADE_CD에 남긴다(2026-10-30 C11).
- 관련 테이블: TB_HR_DEV, TB_HR_DEV_SKILL, TB_PJ_ASGN(EXT_CNT·EARLY_WTHDR_YN), TB_PJ_ASGN_HIST(ASGN_CHG_RSN_CD), TB_PJ_PROJ(FEATURE_CN·WORK_PLC_ADDR), TB_SL_OPP(WORK_PLC_ADDR·RMRK), TB_BS_CLNT(INDUTY_CD), TB_SY_CODE(관련 직무·SKILL), TB_SL_RCMD_LOG(REQ_GRADE_CD), TB_SY_ADM_AREA(시·도 이름 맞춤, C9)
- 결정 Q-식-65~70(사용자 2026-10-22): 위 수식대로. 시안 v1.4 반영(성과는 시안에 이력 데이터가 없어 3점).
- (이전 질문) Q-식-65 직무 일치 25점은 주 직무일 때만인가, 보조 직무도 25점인가? 관련 직무는 몇 점인가?
- (이전 질문) Q-식-66 스킬 일치 20점은 어떻게 나누는가(요청 스킬 중 보유 비율 × 20?). 과거 프로젝트 기술 스택은 어디서 읽는가?
- (이전 질문) Q-식-67 같은 업종 경험은 몇 점인가(같은 고객사 15점, 같은 업종 몇 점)?
- (이전 질문) Q-식-68 과거 성과 10점: 연장 비율과 조기 철수 없음을 어떻게 점수로 바꾸는가?
- (이전 질문) Q-식-69 비용 효과 10점: “대기 비용이 드는 사람, 예상 공헌이익”을 어떻게 점수로 바꾸는가? 시안 식은 임시다.
- (이전 질문) Q-식-70 투입 가능일 10점: 시작일 대비 며칠 일찍/늦게 비는가에 따라 어떻게 줄이는가?', 'UI-30', 70, 'SYSTEM'),
('식-71', 'SFR-911', 'AI 배점 조정안', '조정안 = 채택/거절 기록을 근거로 한 항목별 새 배점. 적용은 관리자 승인 후', '- 쓰는 화면: UI-30, UI-27
- 정의: 쓰지 않는다(Q-식-71 사용자 결정 2026-10-22). 화면 설명과 시안 어디에도 없고 계산 방법도 비어 있다. 배점은 관리자가 운영 기준값(AI_SCORE)에서 직접 바꾸고, 채택·제외 기록(TB_SL_RCMD_LOG)은 판단 자료로 쌓인다. 아래는 원래 내용이다.
- (원래) 채택·거절 기록이 3개월 쌓이면 시스템이 배점 조정안을 낸다. 관리자가 승인해야 바뀐다.
- 수식: `조정안 = 채택/거절 기록을 근거로 한 항목별 새 배점. 적용은 관리자 승인 후`
- 예시: 예: 기록 3개월 후 “스킬 20 → 22, 비용 효과 10 → 8” 제안 → 관리자가 승인하면 그 달부터 적용(합계 100은 유지).
- 예외·반올림: 조정안의 계산 방법은 아직 정해지지 않았다.
- 관련 테이블: TB_SL_RCMD_LOG, TB_SY_OPER_STD
- 결정 Q-식-71(사용자 2026-10-22): 조정안을 만들지 않는다.', 'UI-30, UI-27', 71, 'SYSTEM'),
('식-72', 'SFR-912', '프로젝트 단계 비중과 예정 기간', '턴키·내부: 착수 5, 분석 15, 설계 20, 개발 35, 테스트 15, 오픈 5, 종료 5. 월단위·파견: 착수 5, 인수인계 10, 안정화 15, 정기 운영 65, 종료 5. 단계 종료일 = 시작일 + 반올림(전체 일수 × 누적 비중 ÷ 100)', '- 쓰는 화면: UI-31, UI-01
- 정의: 프로젝트의 단계마다 비중이 있고, 처음에는 계약 기간을 비중만큼 나눠 단계 예정 기간을 만든다. PM이 고칠 수 있다.
- 수식: `턴키·내부: 착수 5, 분석 15, 설계 20, 개발 35, 테스트 15, 오픈 5, 종료 5. 월단위·파견: 착수 5, 인수인계 10, 안정화 15, 정기 운영 65, 종료 5. 단계 종료일 = 시작일 + 반올림(전체 일수 × 누적 비중 ÷ 100)`
- 예시: 계약 기간 200일인 턴키 → 착수 10일, 분석 30일, 설계 40일, 개발 70일, 테스트 30일, 오픈 10일, 종료 10일(합 200일). 시작 후 10일째에 착수 종료, 40일째에 분석 종료.
- 예외·반올림: 단계 이름은 공통코드(프로젝트 단계명)에서 관리한다.
- 관련 테이블: TB_PJ_STEP, TB_SY_CODE(단계 비중)', 'UI-31, UI-01', 72, 'SYSTEM'),
('식-73', 'SFR-912', '계획 진척률', '계획 진척률 = 오늘까지 끝났어야 할 단계 비중의 합 + 진행 중인 단계 비중 × (지난 날 ÷ 그 단계 날 수)', '- 쓰는 화면: UI-31, UI-01
- 정의: 오늘 기준으로 일정대로라면 어디까지 와야 하는지를 %로 나타낸다. 이미 끝났어야 할 단계의 비중에 진행 중인 단계의 비중 × 지난 날 비율을 더한다.
- 수식: `계획 진척률 = 오늘까지 끝났어야 할 단계 비중의 합 + 진행 중인 단계 비중 × (지난 날 ÷ 그 단계 날 수)`
- 예시: 착수·분석·설계 끝(5+15+20), 개발 단계(비중 35)는 100일 중 30일 지남 → 40 + 35 × 30 ÷ 100 = 50.5%.
- 예외·반올림: 소수 첫째 자리까지 계산·표시하고 그 값으로 신호등을 판정한다(공통 표기 규칙 SFR-006-02 “비율 소수 1자리”, Q-식-72 사용자 결정 2026-10-20). 시안의 정수 반올림(50.5 → 51)은 쓰지 않는다.
- 관련 테이블: TB_PJ_STEP
- 결정 Q-식-72(사용자 2026-10-20): 소수 첫째 자리(50.5%)로 표시·판정한다.
- 결정(Q-31-6, 명세서 2026-10): 단계별 계획 시작일·종료일은 PM이 [진행 현황] 탭 [단계 계획]에서 입력한 값을 쓴다.', 'UI-31, UI-01', 73, 'SYSTEM'),
('식-74', 'SFR-912', '실적 진척률과 단계 상태', '단계 상태 = 완료(완료일 있음) / 지연(예정 종료일이 지났는데 완료 안 됨) / 진행 중(예정 기간 안) / 예정(시작 전)', '- 쓰는 화면: UI-31
- 정의: 실적 진척률은 PM이 주간 메모에 적은 값이다(턴키·내부). 월단위·파견은 계획 진척률과 같다. 단계 상태는 완료일과 예정 기간으로 자동 정한다.
- 수식: `단계 상태 = 완료(완료일 있음) / 지연(예정 종료일이 지났는데 완료 안 됨) / 진행 중(예정 기간 안) / 예정(시작 전)`
- 예시: 예정 종료일 10월 5일인 단계를 오늘(10월 12일)까지 완료 처리하지 않았다 → 지연. 완료일을 넣으면 완료.
- 예외·반올림: PM이 실적 진척률을 안 적으면 0%로 본다(시안은 계획 진척률로 대신했으나 변경, Q-식-73 사용자 결정 2026-10-20). 입력은 필수가 아니고, 적은 적이 있으면 가장 최근 값을 쓴다.
- 관련 테이블: TB_PJ_STEP
- 결정 Q-식-73(사용자 2026-10-20): 대신 쓰지 않고 0%로 본다. 주간 메모의 실적 진척률은 필수로 하지 않는다.', 'UI-31', 74, 'SYSTEM'),
('식-75', 'SFR-912', '원가 사용률과 진척 대비 원가', '원가 사용률 = 누적 원가 ÷ 예산(식-19, 프로젝트별 입력값. 기본값 계약금액 × (1 − 목표 마진율)) × 100. 진척 대비 원가 = 원가 사용률 − 실적 진척률', '- 쓰는 화면: UI-31, UI-04
- 정의: 턴키만 쓴다. 누적 원가가 예산의 몇 %인지(원가 사용률)를 구하고 실적 진척률과 견준다. 진척보다 원가를 많이 썼으면 양수다.
- 수식: `원가 사용률 = 누적 원가 ÷ 예산(식-19, 프로젝트별 입력값. 기본값 계약금액 × (1 − 목표 마진율)) × 100. 진척 대비 원가 = 원가 사용률 − 실적 진척률`
- 예시: 계약금액 5억 원, 누적 원가 2.3억 원, 실적 진척률 42% → 예산(식-19 기본값) 400,000,000원, 원가 사용률 57.5%, 진척 대비 원가 +15.5%p.
- 예외·반올림: 원가 사용률은 예산 소진율(식-19)과 같은 값이다.
- 관련 테이블: TB_PJ_PROJ.BDGT_AMT, TB_CO_MON_COST', 'UI-31, UI-04', 75, 'SYSTEM'),
('식-76', 'SFR-912', '신호등 판정', '일정 늦음 = 실적 진척률 − 계획 진척률(음수). 위험: 일정 10%p 이상 늦음 / 진척 대비 원가 15%p 이상 / 기한 지난 “높음” 이슈. 주의: 일정 5%p 이상 늦음 / 진척 대비 원가 5%p 이상 / 기한 지난 이슈 / 마지막 주간 메모가 14일 넘음. 그 밖은 정상', '- 쓰는 화면: UI-31, UI-16, UI-01
- 정의: 프로젝트마다 위험·주의·정상을 자동으로 정한다. 조건 하나라도 걸리면 그 단계다. 여러 신호가 겹치면 더 나쁜 쪽을 쓴다. 종료 프로젝트는 보이지 않는다.
- 수식: `일정 늦음 = 실적 진척률 − 계획 진척률(음수). 위험: 일정 10%p 이상 늦음 / 진척 대비 원가 15%p 이상 / 기한 지난 “높음” 이슈. 주의: 일정 5%p 이상 늦음 / 진척 대비 원가 5%p 이상 / 기한 지난 이슈 / 마지막 주간 메모가 14일 넘음. 그 밖은 정상`
- 예시: 계획 50.5%, 실적 42% → 8.5%p 늦음 → 주의. 같은 프로젝트의 진척 대비 원가 15.5%p → 위험. 둘이 겹치면 위험. 다른 조건이 모두 정상이고 마지막 주간 메모가 15일 전 → 주의.
- 예외·반올림: 경계값은 “이상”이다(정확히 10.0%p 늦으면 위험). 기준값(5·10%p, 5·15%p, 14일)은 고정값이다(관리자 변경 없음, Q-31-2 사용자 결정 2026-10-20). 신호등은 저장하지 않고 볼 때마다 계산한다(배치 없음, 06 D-9, 2026-10-31 C24). 주간 메모·이슈를 저장하면 다음 화면부터 바로 바뀐다. 「마지막 주간 메모」 날짜는 가장 최근에 새로 쓴 메모의 작성일(WRT_DT)이다. 옛날 메모를 고쳐도(MOD_DTTM) 바뀌지 않는다(모든 계약유형, 2026-10-31 C25).
- 관련 테이블: TB_PJ_STEP, TB_PJ_ISSUE, TB_PJ_WEEK_MEMO
- 결정 Q-식-74(사용자 2026-10-20): 월단위·파견 신호등은 원가 조건 없이 일정·기한 지난 이슈·메모 14일 기준으로만 판정한다. 마진율은 원가 화면(UI-04)에서 본다.
- 결정 Q-식-75(사용자 2026-10-20): “기한 지난 이슈”는 상태가 종료가 아니고 기한이 오늘보다 앞선 건이다. 기한 당일은 지난 것으로 보지 않는다.
- 열린 질문 Q-식-91: 식-75(진척 대비 원가)는 단계별 비중대로 원가가 고르게 든다고 가정한 지표다. 실제로는 초반(분석·설계)에 인건비가 몰리거나 후반(테스트·오픈)에 몰리는 등 단계별 원가 발생 속도가 다를 수 있어, 이 지표만으로 “위험”이라고 단정하면 오판할 수 있다. 예산은 이제 프로젝트별 입력값이라(Q-식-27 결정 참고) 고정 가정이라는 한계는 해소됐지만, 실적 진척률이 PM이 스스로 적는 숫자라는 점은 여전히 남아 있다. 결정: “위험”/“주의” 신호가 뜨면 이 지표는 1차 경보로만 쓰고, 실제 판단은 UI-04(프로젝트 원가 분석)의 상세 원가 내역을 확인한 뒤 내린다. 이 안내 문구를 식-75/식-76 설명에 같이 적어 둔다.', 'UI-31, UI-16, UI-01', 76, 'SYSTEM'),
('식-77', 'SFR-913', '철수 예정 판정과 남은 일수', '남은 일수(D-n) = 철수일 − 오늘. 철수 예정 = 0 ≤ D-n 이고 철수일이 속한 달 ≤ 이번 달 + 2개월', '- 쓰는 화면: UI-05, UI-16, UI-28
- 정의: 철수일이 오늘부터 이번 달 포함 3개월 안(이번 달, 다음 달, 다다음 달)에 있으면 “철수 예정”이다. 남은 일수는 철수일에서 오늘을 뺀다. 30일 이하는 빨강, 60일 이하는 주황으로 보인다.
- 수식: `남은 일수(D-n) = 철수일 − 오늘. 철수 예정 = 0 ≤ D-n 이고 철수일이 속한 달 ≤ 이번 달 + 2개월`
- 예시: 오늘 10월 12일, 철수일 11월 20일 → D-39. 철수일이 1월 5일이면 이번 달 + 2개월(12월)을 넘으므로 철수 예정이 아니다.
- 예외·반올림: 철수일이 지났으면 “철수 완료”로 보인다. 목록 필터는 3개월 안·3개월 뒤·이미 철수다(Q-05-2 결정 2026-10-20).
- 관련 테이블: TB_PJ_ASGN.INPUT_END_DT', 'UI-05, UI-16, UI-28', 77, 'SYSTEM'),
('식-79', 'SFR-913', '다음 자리 대상 조건', '조건 1: 철수 다음 달부터 3개월 안에 시작(이미 시작했으면 끝나기 전). 조건 2: 같은 직무(또는 관련 직무)의 가배정 인원 < 필요 인원. 공백 개월 = 자리 시작월 − 철수 다음 달, 0~3', '- 쓰는 화면: UI-05, UI-30, UI-29
- 정의: 철수 예정자에게 보여 줄 자리를 고르는 조건이다. 진행 중인 영업 기회의 필요 인력 줄 중에서 아래를 모두 만족해야 한다.
- 수식: `조건 1: 철수 다음 달부터 3개월 안에 시작(이미 시작했으면 끝나기 전). 조건 2: 같은 직무(또는 관련 직무)의 가배정 인원 < 필요 인원. 공백 개월 = 자리 시작월 − 철수 다음 달, 0~3`
- 예시: 필요 3명 중 2명이 가배정 → 자리 있음(1자리 남음). 3명이 가배정 → 자리 없음. 철수 11월 20일 → 철수 다음 달은 12월. 자리 시작 2월이면 공백 2개월. 시작 4월이면 공백 4개월 → 제외.
- 예외·반올림: 다음 배정이 없고 연장 가능성이 “높음”이 아닌 사람에게만 추천 버튼을 보여 준다.
- 관련 테이블: TB_SL_OPP, TB_SL_OPP_NEED, TB_SL_TENT_ASGN', 'UI-05, UI-30, UI-29', 79, 'SYSTEM'),
('식-80', 'SFR-913', '다음 자리 추천 점수', '점수 = 직무(일치 40, 관련 직무 20) + 등급(일치 20, 한 단계 차이 10) + 공백(없음 25, 1개월 15, 2개월 8, 3개월 3) + 같은 고객사 10 + 수주 확률 60% 이상 5', '- 쓰는 화면: UI-05, UI-30
- 정의: 철수 예정자와 자리가 얼마나 맞는지 100점 만점으로 매긴다. 점수 높은 순 5곳을 보여 준다.
- 수식: `점수 = 직무(일치 40, 관련 직무 20) + 등급(일치 20, 한 단계 차이 10) + 공백(없음 25, 1개월 15, 2개월 8, 3개월 3) + 같은 고객사 10 + 수주 확률 60% 이상 5`
- 예시: 철수 11월 20일, 같은 직무, 등급 한 단계 차이, 자리 시작 12월(공백 0), 다른 고객사, 확률 60% → 40 + 10 + 25 + 0 + 5 = 80점. 자리 시작이 다음 해 2월(공백 2개월)이면 40 + 10 + 8 + 0 + 5 = 63점.
- 예외·반올림: 관련 직무 표는 UI-30 추천의 관련 직무와 같다. 공백이 3개월을 넘으면 후보에서 뺀다(점수 없음). 자리의 필요 등급이 비어 있으면 중급으로 보고 비교한다(시안 그대로). 비교한 등급은 추천 기록 REQ_GRADE_CD에 남긴다(2026-10-30 C11).
- 관련 테이블: TB_SL_RCMD_LOG(RCMD_TYPE_CD=NEXT)
- 결정 Q-식-77(사용자 2026-10-20): 점수가 같으면 자리 시작이 빠른 순으로 둔다(시안은 기준 없음).
- 결정 35번 2(사용자 2026-10-22): 명세서 화면 설명의 98점 식(스킬·근무지 포함) 대신 이 식으로 통일한다. 스킬·근무지 점수는 없다. SFR-105-05와 시안 계산을 이 식에 맞췄다.', 'UI-05, UI-30', 80, 'SYSTEM'),
('식-81', 'SFR-913', '대기 예상 일수', '대기 예상 일수 = 자리 시작일 − 철수일 − 1', '- 쓰는 화면: UI-05
- 정의: 철수한 다음 날부터 자리가 시작되기 전날까지 비는 날 수다.
- 수식: `대기 예상 일수 = 자리 시작일 − 철수일 − 1`
- 예시: 철수 11월 20일, 자리 시작 12월 1일 → 10일.
- 예외·반올림: 자리 시작일이 월 단위로만 있으면 그 달 1일로 본다(시안 동작). 음수가 되면 0으로 한다.
- 관련 테이블: TB_PJ_ASGN, TB_SL_OPP
- 결정 Q-식-78(사용자 2026-10-20): 시안대로 자리 시작은 월 단위이고 그 달 1일로 계산한다. 0일이면 “바로 이어서 투입”으로 보여 준다.', 'UI-05', 81, 'SYSTEM'),
('식-82', 'SFR-913', '다음 배정 없는 철수 예정자 집계', 'A = 3개월 안 철수 예정자 수. B = A 중 가배정이 없고 연장 가능성이 “높음”이 아닌 사람 수. C = B 중 추천 자리가 1곳 이상 있는 사람 수', '- 쓰는 화면: UI-05, UI-16
- 정의: 철수 목록 맨 위 안내문과 대시보드 “철수 예정” 줄에 쓰는 숫자다.
- 수식: `A = 3개월 안 철수 예정자 수. B = A 중 가배정이 없고 연장 가능성이 “높음”이 아닌 사람 수. C = B 중 추천 자리가 1곳 이상 있는 사람 수`
- 예시: 3개월 안 철수 6명 → 가배정 없고 연장 “높음” 아님 4명 → 그중 추천 자리가 있는 사람 3명. 안내문: “다음 배정 없는 4명 중 3명은 맞는 자리 있음”.
- 예외·반올림: 가배정한 사람은 B에서 빠진다.
- 관련 테이블: TB_PJ_ASGN, TB_SL_TENT_ASGN', 'UI-05, UI-16', 82, 'SYSTEM'),
('식-83', 'SFR-914', '1인 월 원가(직무·등급 평균)', '1인 월 원가 = 같은 직무·등급 인력의 원가합계 평균 (급여 + 수당 + 4대보험 + 퇴직금 + 팀비·경비)', '- 쓰는 화면: UI-29
- 정의: 견적에서 직무·등급별 예상 원가를 구할 때 쓰는 값이다. 같은 직무·등급 인력(휴직 제외)의 평균 원가합계를 쓴다. 2명 미만이면 같은 등급 전체의 평균을 쓴다.
- 수식: `1인 월 원가 = 같은 직무·등급 인력의 원가합계 평균 (급여 + 수당 + 4대보험 + 퇴직금 + 팀비·경비)`
- 예시: 같은 직무·등급 3명의 원가합계 6,800,000원, 7,000,000원, 6,900,000원 → 6,900,000원. 1명뿐이면 같은 등급 전체 평균을 쓴다.
- 예외·반올림: 시안은 평균을 만 원 단위로 반올림한다.
- 관련 테이블: TB_CO_MON_COST, TB_HR_DEV
- 결정 Q-식-79(사용자 결정 2026-10-22): 시안대로 평균을 만 원 단위로 반올림한다.
- 기준 달(2026-10-31 C26): 가장 최근에 급여가 올라온 달의 월 원가(TB_CO_MON_COST)로 평균을 낸다. 그 달 급여 줄이 있는 사람만 넣고, 그 달 휴직인 사람은 뺀다(시안은 지금 급여로 계산하므로 같은 뜻).', 'UI-29', 83, 'SYSTEM'),
('식-84', 'SFR-914', '견적 예상 매출·원가·이익률', '예상 매출 = Σ (인원 × 월 MM × 개월 × 예상 단가). 예상 원가 = Σ (인원 × 월 MM × 개월 × 1인 월 원가). 예상 이익률 = (예상 매출 − 예상 원가) ÷ 예상 매출 × 100', '- 쓰는 화면: UI-29
- 정의: 영업 기회의 필요 인력 표를 입력하는 대로 예상 이익을 계산한다. 판관비는 넣지 않는다(목표 이익률 20% 안에서 감당).
- 수식: `예상 매출 = Σ (인원 × 월 MM × 개월 × 예상 단가). 예상 원가 = Σ (인원 × 월 MM × 개월 × 1인 월 원가). 예상 이익률 = (예상 매출 − 예상 원가) ÷ 예상 매출 × 100`
- 예시: 2명 × 1.0MM × 6개월, 예상 단가 800만 원, 1인 월 원가 690만 원 → 매출 96,000,000원, 원가 82,800,000원, 이익률 13.75%.
- 예외·반올림: 예상 단가를 비우면 시안은 직무별 기준 단가(예: 백엔드 600만 원)를 대신 넣는다. 줄에서 직무·인원·개월 중 하나라도 비면 그 줄은 계산에서 뺀다.
- 관련 테이블: TB_SL_OPP, TB_SL_OPP_NEED
- 결정 Q-식-80(사용자 결정 2026-10-22): 직무별 기준 단가는 공통코드 「직무」(JOB)의 추가값2(ADD_VAL2)에 둔다. 처음 값은 시안 숫자(PM 850만, PL 800만, 기획 650만, 아키텍트 880만, 백엔드 600만, 프론트엔드 550만, UI/UX 500만, 퍼블리싱 400만, QA 450만, 데이터 650만, 운영 450만, AI 750만 원).', 'UI-29', 84, 'SYSTEM'),
('식-85', 'SFR-914', '견적 최소 단가·목표 단가·최소 금액', '최소 단가 = 1인 월 원가. 목표 단가 = 1인 월 원가 ÷ (1 − 목표 이익률 20%), 만 원 단위 올림. 손해 안 보는 최소 금액 = 예상 원가', '- 쓰는 화면: UI-29
- 정의: 줄마다 손해를 안 보는 최소 단가와 목표 이익률 20%를 맞추는 목표 단가를 보여 준다. 전체로는 손해 안 보는 최소 금액을 보여 준다.
- 수식: `최소 단가 = 1인 월 원가. 목표 단가 = 1인 월 원가 ÷ (1 − 목표 이익률 20%), 만 원 단위 올림. 손해 안 보는 최소 금액 = 예상 원가`
- 예시: 1인 월 원가 690만 원 → 최소 단가 6,900,000원. 목표 단가 = 690만 ÷ 0.8 = 8,625,000원 → 만 원 올림 8,630,000원. 최소 금액 82,800,000원.
- 예외·반올림: 시안은 “목표 이익률을 맞추는 금액”(예상 원가 ÷ 0.8, 백만 원 올림)도 함께 보여 준다.
- 관련 테이블: TB_SL_OPP_NEED, TB_SY_OPER_STD
- 결정 Q-식-81(사용자 결정 2026-10-22): 화면에 둔다. 목표 이익률을 맞추는 금액 = 예상 원가 ÷ (1 − 목표 이익률), 백만 원 단위 올림. 예: 82,800,000 ÷ 0.8 = 103,500,000 → 104,000,000원.', 'UI-29', 85, 'SYSTEM'),
('식-86', 'SFR-914', '견적 승인 판정', '승인 대기 = 예상 이익률 < 15%. 다시 저장: |새 이익률 − 승인 때 이익률| ≤ 0.5%p면 승인 유지, 넘고 15% 미만이면 다시 승인 대기, 넘어도 15% 이상이면 승인 요구 해제(AUTO_REL). 반려된 건은 고쳐 저장하면 다시 요청', '- 쓰는 화면: UI-29, UI-16
- 정의: 예상 이익률이 승인 기준 15% 미만이면 저장할 때 “견적 승인 대기”가 된다. 경영진이 승인하거나 반려한다. 승인 뒤 다시 저장할 때는 이익률이 거의 같으면 승인을 유지하고, 크게 바뀌면 다시 승인을 요청한다.
- 수식: `승인 대기 = 예상 이익률 < 15%. 다시 저장: |새 이익률 − 승인 때 이익률| ≤ 0.5%p면 승인 유지, 넘고 15% 미만이면 다시 승인 대기, 넘어도 15% 이상이면 승인 요구 해제(AUTO_REL). 반려된 건은 고쳐 저장하면 다시 요청`
- 예시: 예상 이익률 13.75% → 15% 미만, 승인 대기. 승인 뒤 14.0%로 저장 → 차이 0.25%p, 유지. 12.9%로 저장 → 차이 0.85%p, 다시 승인 대기.
- 예외·반올림: 화면 표시 문구는 손해 예상(0% 미만), 승인 필요(15% 미만), 목표 미달(20% 미만), 기준 충족(20% 이상)이다. 승인 기준 15%와 목표 20%는 운영 기준값이다.
- 관련 테이블: TB_SL_OPP(견적 상태), TB_SL_APRV_HIST
- 결정 Q-식-82(사용자 결정 2026-10-22): 맞다. 15% 미만만 승인 대기이고 15.0%는 대기가 아니다. 20.0%도 기준 충족. 승인 기준·목표·단계 확률은 운영 기준값(Q-29-4).', 'UI-29, UI-16', 86, 'SYSTEM'),
('식-87', 'SFR-914', '외부 인력 후보의 자리별 예상 이익률', '예상 이익률 = (자리 단가 − 희망 단가 − 팀비·경비) ÷ 자리 단가 × 100. 외주는 팀비·경비 0', '- 쓰는 화면: UI-33
- 정의: 외부 후보(프리랜서·외주)가 영업 기회의 어느 자리에 맞는지 이익률로 본다. 같은 직무이고 후보가 가능한 시기에 시작하는 자리만 보인다.
- 수식: `예상 이익률 = (자리 단가 − 희망 단가 − 팀비·경비) ÷ 자리 단가 × 100. 외주는 팀비·경비 0`
- 예시: 자리 단가 800만 원, 프리랜서 희망 단가 650만 원, 팀비·경비 100,000원 → (800만 − 650만 − 10만) ÷ 800만 = 17.5%.
- 예외·반올림: 기준 15%보다 낮으면 빨강으로 보인다(단가를 다시 협의). 기준은 견적 승인 기준과 같은 값이다(운영 기준값 「견적 승인 기준 이익률」 QUOTE_APRV_RT, 처음 15%. 바꾸면 이 빨강 기준도 같이 바뀜, 2026-10-31 C27). 외주 희망 단가는 업체에 주는 금액(부가세 별도)이다. 자리는 등급 차이가 작은 순, 이익률 높은 순으로 4곳까지 보여 준다.
- 관련 테이블: TB_HR_CAND, TB_SL_OPP_NEED
- 결정 Q-33-4·Q-식-83(사용자 결정 2026-10-22): 프리랜서 팀비·경비 = 공통코드 팀비 기본값 + 프로젝트경비 기본값(지금 50,000 + 50,000 = 100,000원). 외주는 0. 기본값을 바꾸면 이 화면도 같이 바뀐다. v2 예시 20만 원은 쓰지 않는다.', 'UI-33', 87, 'SYSTEM'),
('식-88', 'SFR-915', '청구 누락 판정', '투입 기준 금액 = Σ (월 단가 × 그 달 MM), 무상지원 제외. 차이 = 투입 기준 금액 − 청구 공급가. 판정: 청구 0건 → 청구 없음 / |차이| > max(1만 원, 투입 기준 금액 × 0.5%) → 적게(차이 > 0) 또는 많이(차이 < 0) 청구 / 그 밖 → 맞음', '- 쓰는 화면: UI-13, UI-14, UI-16
- 정의: 월단위·파견 프로젝트마다 그 달 투입 기준 금액과 실제 청구 공급가를 견줘 청구가 빠졌거나 틀린 건을 찾는다. 턴키는 단계별 청구라 빠진다.
- 수식: `투입 기준 금액 = Σ (월 단가 × 그 달 MM), 무상지원 제외. 차이 = 투입 기준 금액 − 청구 공급가. 판정: 청구 0건 → 청구 없음 / |차이| > max(1만 원, 투입 기준 금액 × 0.5%) → 적게(차이 > 0) 또는 많이(차이 < 0) 청구 / 그 밖 → 맞음`
- 취소된 청구(CNCL_YN=Y)는 청구 공급가에 넣지 않는다(사용자 결정 2026-10-26).
- 예시: 투입 기준 700만 × 1.0 + 650만 × 0.5 = 10,250,000원. 청구 공급가 10,200,000원 → 차이 50,000원 ≤ 허용 51,250원 → 맞음. 청구 10,000,000원 → 차이 250,000원 → 적게 청구. 청구 10,600,000원 → 차이 −350,000원 → 많이 청구. 그 달 청구 0건 → 청구 없음.
- 예외·반올림: 이 달 새로 투입한 사람의 금액이 차이와 거의 같으면(오차 max(1만 원, 1%)) 그 사람 이름을 설명에 적는다. 월 마감 점검(UI-14)과 대시보드에 건수가 올라간다. 투입 기준 금액은 투입 건별로 원 단위 버림한 뒤 더한다(청구 등록 추정 공급가·식-02와 같음, 2026-10-30).
- 관련 테이블: TB_PJ_ASGN_MON, TB_BL_BILL, TB_CL_CLOSE_CHK', 'UI-13, UI-14, UI-16', 88, 'SYSTEM'),
('식-89', 'SFR-915', '미수 알림 단계', '지난 일수 = 오늘 − 입금 예정일. 7일 이상 1단계(PM), 30일 이상 2단계(영업 파트장), 60일 이상 3단계(경영진·재무). 1~6일은 알림 없음(상태는 연체, 식-06)', '- 쓰는 화면: UI-13, UI-16
- 정의: 입금 예정일이 지난 미수금마다 지난 일수에 따라 단계별로 알린다. 앞 단계 사람도 함께 받는다. 미수금이 0이 되면 알림이 멈춘다.
- 수식: `지난 일수 = 오늘 − 입금 예정일. 7일 이상 1단계(PM), 30일 이상 2단계(영업 파트장), 60일 이상 3단계(경영진·재무). 1~6일은 알림 없음(상태는 연체, 식-06)`
- 예시: 입금 예정일 9월 10일, 오늘 10월 13일, 미수금 남음 → 33일 → 2단계(영업 파트장까지). 미수금이 0원이 되면 멈춘다.
- 예외·반올림: 일반 파트의 2단계는 그 프로젝트 파트의 이번 달 파트장(영업 파트 탭 기준 이력, 2026-10-29)에게 보낸다. 본사 직접 영업 프로젝트는 2단계를 본사 영업 책임자(사용자 화면에서 담당 파트를 「본사(직접 영업)」으로 고른 영업 파트장 계정 전부, 없으면 경영진, N-23-1)에게, 파트 미지정은 경영진에게 보낸다. 기준 일수 7·30·60은 운영 기준값이다.
- 관련 테이블: TB_BL_BILL, TB_BL_DUN_HIST, TB_SY_OPER_STD(미수 일수)
- 결정 Q-식-84·Q-13-6(사용자 2026-10-21): 매일 09:00에 점검해서 단계가 바뀐 날 메일과 시스템 알림을 단계마다 한 번만 보낸다. 자동 반복은 없고, 그 사이에는 [다시 알림]으로 사람이 보낸다.', 'UI-13, UI-16', 89, 'SYSTEM'),
('식-90', 'SFR-915', '단가 재협상 계산', '이익률 = (단가 × MM − 배분 원가) ÷ (단가 × MM) × 100. 목표 단가 = 배분 원가 ÷ MM ÷ (1 − 20%). 필요 인상률 = (목표 단가 − 현재 단가) ÷ 현재 단가. 연 이익 증가 = 현재 단가 × 인상률 × MM × 12. 인상률은 필요 인상률 또는 가정 인상률(처음 5%)', '- 쓰는 화면: UI-32, UI-04
- 정의: 갱신이 다가오고 이익률이 낮은 투입에 대해 목표 단가와 단가를 올렸을 때의 효과를 계산한다. 대상은 월단위·파견 진행 프로젝트의 투입이다(무상지원 제외, 턴키 제외).
- 수식: `이익률 = (단가 × MM − 배분 원가) ÷ (단가 × MM) × 100. 목표 단가 = 배분 원가 ÷ MM ÷ (1 − 20%). 필요 인상률 = (목표 단가 − 현재 단가) ÷ 현재 단가. 연 이익 증가 = 현재 단가 × 인상률 × MM × 12. 인상률은 필요 인상률 또는 가정 인상률(처음 5%)`
- 예시: 단가 6,000,000원, 1.0MM, 배분 원가 5,400,000원 → 이익률 10.0%, 목표 단가 6,750,000원, 필요 인상률 12.5%, 연 이익 증가 9,000,000원. 가정 인상률 10%면 7,200,000원.
- 예외·반올림: 처음 조회 조건은 “갱신 6개월 안(투입 종료일까지 183일 이하) · 이익률 15% 미만”으로 고정이다(Q-32-1). 갱신 시점 조건의 일수(오늘부터 투입 종료일까지): 「3개월 안」 92일 이하, 「6개월 안」 183일 이하, 「1년 안」 366일 이하. 갱신일 표시는 30일 이하 빨강, 92일 이하 주황(2026-10-31 사용자 결정, 시안대로). 목표 단가는 만 원 단위로 올림한다(Q-식-85). **MM은 두 가지를 구분한다(2026-10-29 D17):** 이익률·목표 단가의 MM = 기준월 그 달 MM(TB_CO_MON_COST.ASGN_MM = TB_PJ_ASGN_MON.MON_MM, 배분 원가와 같은 달 값). 연 이익 증가(× 12)의 MM = 계약 MM(TB_PJ_ASGN.CTRT_MM, 1년 치 예상이라 시작·철수 달의 일할 MM을 쓰지 않음). 예: 10/15 시작, 단가 600만, 계약 1.0MM, 10월 MM 0.52, 10월 배분 원가 280만 → 목표 단가 = 280만 ÷ 0.52 ÷ 0.8 ≈ 673만 → 만 원 올림 6,740,000원(계약 MM으로 나누면 350만으로 틀림).
- 관련 테이블: TB_PJ_ASGN(CTRT_MM), TB_PJ_ASGN_MON(APLY_UPRC), TB_CO_MON_COST(ASGN_MM·LABOR_ALLOC_AMT)
- 결정 Q-식-85(사용자 2026-10-21): 명세서·시안대로 목표 단가는 만 원 단위로 올림한다. 올림이라 이익률이 목표 아래로 떨어지지 않고, 필요 인상률도 올린 목표 단가로 계산한다.
- 결정 Q-식-86(사용자 2026-10-21): 명세서대로 기준월 한 달의 배분 원가를 쓴다. 화면 열 이름은 “월 원가(기준월)”. 값이 튀는 경우가 생기면 그때 평균 기간을 다시 정한다.', 'UI-32, UI-04', 90, 'SYSTEM'),
('식-91', 'SFR-915', '원가 초과 원인 분석', '투입 초과 = max(0, 실제 MM − 지금까지 계획 MM) × MM당 원가. 단가 대비 원가 = 달마다 인력별 max(0, 배분 원가 − 단가 × MM × 80%)의 합을 더한 값. 무상지원 = 달마다 무상지원 MM의 배분 원가를 더한 값. 턴키 진척 대비 초과 = 누적 원가 − 예산 × 실적 진척률. 나머지 = 진척 대비 초과 − 세 원인의 합', '- 쓰는 화면: UI-04, UI-16
- 정의: 경보가 있는 프로젝트의 원가를 늘린 원인을 금액이 큰 순서로 보여 준다. 원인은 세 가지다. 턴키는 실적 진척률만큼이면 원가가 얼마여야 하는지와 초과 금액, 세 원인으로 설명 안 되는 나머지도 보여 준다.
- 수식: `투입 초과 = max(0, 실제 MM − 지금까지 계획 MM) × MM당 원가. 단가 대비 원가 = 달마다 인력별 max(0, 배분 원가 − 단가 × MM × 80%)의 합을 더한 값. 무상지원 = 달마다 무상지원 MM의 배분 원가를 더한 값. 턴키 진척 대비 초과 = 누적 원가 − 예산 × 실적 진척률. 나머지 = 진척 대비 초과 − 세 원인의 합`
- 예시: 투입 초과: 실제 30MM, 계획 26MM, MM당 원가 700만 원 → 28,000,000원. 단가 대비 원가: 배분 원가 600만 원, 단가 700만 원 × 1.0MM, 경과 5개월 → (600 − 560)만 × 5 = 2,000,000원. 턴키 진척 대비 초과: 누적 원가 2.3억, 예산 4억, 실적 42% → 62,000,000원. 무상지원: 5개월 동안 매달 2,485,000원 → 12,425,000원. 세 원인 합 42,425,000원 → 나머지 19,575,000원.
- 예외·반올림: 내부 프로젝트와 수주예정은 원인 분석을 하지 않는다. 80%는 운영 기준값이다. “지금까지 계획 MM” = 계획 MM × 경과 개월 ÷ 전체 개월. 계획 MM은 [인원 계획] 탭 직무별 합계(TB_PJ_MNPW_PLAN)이고, 없으면 투입 초과는 계산하지 않는다.
- 관련 테이블: TB_CO_MON_COST, TB_PJ_MNPW_PLAN, TB_PJ_STEP
- 결정 Q-식-87(사용자 2026-10-21): 실제 시스템은 월별 원가에서 달마다 실제 무상지원 원가를 더한다(곱하지 않음). 단가 대비 원가도 달별 합계. “× 경과 개월”은 한 달치 데이터만 있는 시안의 간이 계산이다.
- 결정 Q-식-88(사용자 2026-10-21): 시안대로 턴키는 진척 대비 초과가 세 원인 합보다 크면 “나머지 ○○원은 위 세 가지로 설명되지 않습니다” 문구로 보여 준다.
- 결정(사용자 2026-10-25): 화면 색: 주원인 막대는 **경보(저마진·예산 초과)가 있거나 턴키 진척 대비 원가가 초과일 때만 빨강**, 아니면 회색 + “경보가 없는 프로젝트라 참고용” 문구(2026-10-25 사용자 결정, 명세서에 색 규칙 없음).', 'UI-04, UI-16', 91, 'SYSTEM'),
('식-92', 'SFR-915', '매출 예측(앞으로 6개월)', '월단위·파견: 그 달 투입 인력의 (단가 × MM) 합, 철수일까지만(연장 미반영). 턴키: (계약금액 − 이미 청구한 공급가) ÷ 다음 달부터 계약 종료까지 남은 개월 수. 영업 기회: 예상 금액 ÷ 예상 기간(개월) × 수주 확률', '- 쓰는 화면: UI-17, UI-29, UI-16
- 정의: 다음 달부터 6개월의 월별 예상 매출이다. 진행 중 프로젝트와 확률을 반영한 영업 기회를 더한다.
- 수식: `월단위·파견: 그 달 투입 인력의 (단가 × MM) 합, 철수일까지만(연장 미반영). 턴키: (계약금액 − 이미 청구한 공급가) ÷ 다음 달부터 계약 종료까지 남은 개월 수. 영업 기회: 예상 금액 ÷ 예상 기간(개월) × 수주 확률`
- 예시: 턴키 계약 6억 원(1~12월), 10월까지 5억 4천만 원 청구 → 남은 6,000만 원 ÷ 2개월(11·12월) = 달마다 30,000,000원. 영업 기회 3억 원, 6개월, 확률 30% → 달마다 15,000,000원. 월단위 A 700만 × 1.0 + B 650만 × 0.5 = 10,250,000원, A가 12월 말 철수하면 1월부터 B만 3,250,000원.
- 예외·반올림: 턴키는 다음 달부터 계약 종료 달까지만 나눈다. 남은 금액이 0 이하이면 0, 계약 기간이 끝났는데 남은 금액이 있으면 언제 청구할지 몰라 예측에 넣지 않는다. 연장 확률은 매출 예측에 넣지 않는다.
- 관련 테이블: TB_PJ_ASGN, TB_PJ_PROJ, TB_SL_OPP, TB_BL_BILL(SPLY_AMT)
- 결정 Q-식-89·Q-01-4(사용자 2026-10-22): 청구 단계 입력 화면은 만들지 않는다(v2에만 있음). 턴키 예측은 남은 계약금액을 남은 개월에 나눈다. 이미 청구한 돈을 다시 세지 않아 예측과 실제 청구의 합이 계약금액을 넘지 않는다(33번 2).', 'UI-17, UI-29, UI-16', 92, 'SYSTEM'),
('식-93', 'SFR-915', '채용 시작 권장 월', '첫 부족 월 = 수요 − 공급 > 0.4인 첫 달. 권장 시작 월 = 첫 부족 월 − 2개월. 오늘 달 이전이면 “지금”', '- 쓰는 화면: UI-17, UI-29, UI-16
- 정의: 직무별로 확률을 반영한 수요가 공급보다 0.4명을 넘게 많아지는 첫 달을 찾고, 채용 준비 기간 2개월 앞을 권장 시작 월로 낸다. 이미 그 달이 지났으면 “지금”이다.
- 수식: `첫 부족 월 = 수요 − 공급 > 0.4인 첫 달. 권장 시작 월 = 첫 부족 월 − 2개월. 오늘 달 이전이면 “지금”`
- 예시: 첫 부족 월 2027-03 → 권장 시작 2027-01. 첫 부족 월 2026-11 → 2026-09는 지난 달이라 “지금”. 기준월은 2026-10.
- 예외·반올림: 채용 준비 기간 2개월과 0.4명은 운영 기준값이다. 후보 수(보류·계약 완료 제외)를 함께 보여 준다.
- 관련 테이블: TB_PJ_ASGN, TB_SL_OPP_NEED', 'UI-17, UI-29, UI-16', 93, 'SYSTEM'),
('식-94', 'SFR-915', '저마진·예산 초과 경보', '저마진: (턴키 예상 최종 마진율 또는 월단위·파견 월간 마진율) − 20%가 −5%p 미만. 예산 초과: 예산 소진율 ≥ 100%', '- 쓰는 화면: UI-04, UI-16
- 정의: 프로젝트 원가 분석의 경보다. 저마진은 목표 마진율(20%)보다 5%p 넘게 낮을 때, 예산 초과는 예산 소진율이 100% 이상일 때다. 80% 이상은 주황 표시다. 알림은 보내지 않는다(저마진 경보 알림은 없음, Q-공통-7 결정 2026-10-22).
- 수식: `저마진: (턴키 예상 최종 마진율 또는 월단위·파견 월간 마진율) − 20%가 −5%p 미만. 예산 초과: 예산 소진율 ≥ 100%`
- 예시: 월간 마진율 14.0%, 목표 20% → −6.0%p, 저마진. 마진율 15.0%면 −5.0%p라 경계값. 소진율 79.9%는 정상, 80%는 주황, 100%는 예산 초과.
- 예외·반올림: 내부 프로젝트와 수주예정은 대상이 아니다. 경보 줄마다 주원인(식-91)을 함께 적는다.
- 관련 테이블: TB_CO_MON_COST, TB_PJ_PROJ
- 결정 Q-식-90(사용자 2026-10-21): “넘게”(경계 제외)로 통일. 목표 20%에 15.0%면 경보 아님, 14.9%부터 경보. 명세서 v1.9 공통 알림 표의 “5%p 이상”은 다음 판에서 “넘게”로 고친다.', 'UI-04, UI-16', 94, 'SYSTEM'),
('식-95', 'SFR-915', 'MM 1.0 초과·미계약 선투입 경고', '경고 = 한 달 MM 합 > 1.0. 미계약 선투입 = 투입 프로젝트 상태가 수주예정', '- 쓰는 화면: UI-12, UI-03, UI-14, UI-16
- 정의: 한 사람의 한 달 MM 합이 1.0을 넘으면 저장은 하되 경고한다. 투입한 프로젝트가 수주예정이면 “미계약 선투입”으로 경고한다.
- 수식: `경고 = 한 달 MM 합 > 1.0. 미계약 선투입 = 투입 프로젝트 상태가 수주예정`
- 예시: A 프로젝트 1.0MM + B 프로젝트 0.5MM = 1.5 > 1.0 → 경고. 원가는 합계로 나눈 비율(2/3, 1/3)로 배분한다(식-13). MM 합이 정확히 1.0이면 경고 없음.
- 예외·반올림: 월 마감 점검(UI-14)에 “MM 1.0 초과자” 건수로 올라간다. 경고 기준 1.0은 운영 기준값이다.
- 관련 테이블: TB_PJ_ASGN_MON, TB_PJ_PROJ.PROJ_STAT_CD', 'UI-12, UI-03, UI-14, UI-16', 95, 'SYSTEM'),
('식-96', 'SFR-905', '사람별 가동률', '가동률 = 투입된 달 ÷ 재직한 달 × 100(가득률은 사람별로 보여 주지 않음). 재직한 달 = 조회 연도 1월(그 해 입사면 입사한 달)부터 기준월(퇴사자는 퇴사한 달)까지', '- 쓰는 화면: UI-03(사람별 표의 가동률 열)
- 정의: 한 사람이 조회 기간에 재직한 달 중 몇 달 투입됐는지 본다. MM이 아니라 달 수로 센다. 월 MM 0.4처럼 일부만 일하는 사람도 그 달은 투입된 달이라 놀고 있다고 보이지 않는다(2026-10-27 사용자 결정).
- 수식: `가동률 = 투입된 달 ÷ 재직한 달 × 100`. 턴키·무상지원·내부 투입도 투입된 달로 센다. 투입된 달 = 그 달 MM > 0인 배정이 하나라도 있는 달(식-37과 같은 기준). 재직한 달 = 조회 연도 1월(그 해 입사면 입사한 달)부터 기준월(지난 해는 12월, 퇴사자는 퇴사한 달)까지
- 예시: 2024년 입사, 2026년 1~10월 중 9달 투입 → 9 ÷ 10 = 90.0%. 2026년 5월 입사, 5~10월 6달 모두 투입 → 6 ÷ 6 = 100.0%. 1~2월 투입, 3월 쉼, 4~10월 투입 → 9 ÷ 10 = 90.0%
- 결정(2026-10-27 사용자 결정): 가득률 열도 넣었다가 뺐다. 이 열은 “쉬지 않고 회사에 도움을 주는지”를 보려는 것인데, 가득률(식-38)은 월단위·파견만 세서 턴키만 하는 사람이 0%로 보인다. 요약 칸의 회사 전체 가득률(식-38·39)은 그대로
- 예외·반올림: 소수 첫째 자리. 재직한 달이 0이면 “–”. 입사일(JNCMP_DT)이 비어 있으면 조회 연도 1월부터 센다. 칸에 마우스를 올리면 “9달 ÷ 10달”
- 관련 테이블: TB_PJ_ASGN_MON, TB_HR_DEV', 'UI-03(사람별 표의 가동률 열)', 96, 'SYSTEM');

COMMIT;
