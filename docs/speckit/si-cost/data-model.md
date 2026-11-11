# 도메인 데이터 계약 — 원가관리시스템 기반 구축·공통코드

## 설계 범위·원천과 현재 판정

정본은 이 문서이며 data-model.json은 필드 표의 기계 투영물이다. SPEC-REQUEST.md 476행 전체와 승인 spec.md·plan.md·tasks.md·요구 원장 156개를 읽고 기존 canonical_id를 보존한다. 기존 원장 두 파일은 이미 문서 작성 전에 존재하며 재번호·재정의하지 않는다. 이번 배치는 data-model.md, data-model.json, contracts/openapi.yaml만 생성한다. 다른 관리 문서를 추가하거나 상류를 수정하지 않는다.

원문 L17이 위임한 저장소 example/project_ddl.sql 전체를 읽기 전용으로 확보했다. 고정 revision은 d1e18c27afb077bfa5b1d92b982af194676ebd91이며 UTF-8 BOM 제거 후 텍스트 SHA-256은 96729f305751ab102c430451b99c7edb22e983bd4c234ec6bbfea6d2bcc1bb55다. 이 해시는 원본 raw bytes 해시와 구별한다. 파일 상단 설명의 67테이블/825컬럼은 실제 정의 및 TB_SY_CALC_RULE 추가분과 다르다. 전체 CREATE 정의 파싱 결과 68테이블·836컬럼·73 FK 제약·78 명시 인덱스·13시퀀스·46 CHECK·5 UK이며 원문 REQ-003과 일치한다. 실제 PostgreSQL 설치·데이터 적재·JPA validate를 실행한 결과가 아니다. example/project_data.sql의 revision/실행본 diff·초기값 수량은 미검증이며 원문 수량을 Seed Data Contract의 기대값으로 유지한다(REQ-001, REQ-003, REQ-004, REQ-006).

판정: 설계 후보·구현 착수 차단. OQ-001(Java21/Boot3.5.x 대 Java17/Boot3.3.x), OQ-007(spec.md·REQ-144의 JPA 대 plan.md exact block의 mybatis)를 해소하지 않는다. plan.md가 실행·영속성 결정의 의미상 정본이며 imperative/blocking 흐름만 공통으로 확정 가능하다. DB 물리 구조를 확보해도 영속성 프레임워크 승인과 같지 않다. OQ-006 current request_key는 환경/상류에 없고 질문 전달도 불가능하여 data-model.json.meta.request_key=null로 남긴다. 이는 완성된 메타데이터가 아니며 BASIC-129나 폴더명을 대신 넣지 않는다. generated_at은 실제 생성시각이다.

OQ-002는 DDL 물리명 확보 범위에서만 보완한다. 상류의 미확보 기록을 수정하거나 과거에 확보되었던 것으로 쓰지 않으며, 초기값 파일 전체 검증과 DB 실측은 계속 미검증이다. 모든 후속 구현은 승인된 상류의 차단과 이 문서의 미결을 먼저 해결한다.

## Entity Registry

다음은 실제 physical table의 전수 목록이다. DDL 설치 범위와 업무 UI/API 범위를 구분한다. 여기에 테이블이 있다는 사실은 해당 업무 기능을 만드는 허가가 아니다. 설치 후 업무 쓰기 허용은 정확히 6테이블이며 그 밖의 테이블은 조회/설치 전용이다. 컬럼과 FK의 상세는 아래 표가 정본이고 문서 제목을 엔터티로 추출하지 않는다.

| entity/table | 표시명 | 식별자 | 설치 후 접근 경계 | canonical_id |
| --- | --- | --- | --- | --- |

| TB_SY_USER | 사용자 | USER_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-025 |

| TB_SY_ROLE | 역할 | ROLE_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_USER_ROLE | 사용자역할 | USER_ID,ROLE_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_USER_SCOPE | 사용자담당범위 | USER_ID,SCOPE_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_MENU | 메뉴 | MENU_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-035 |

| TB_SY_ROLE_MENU_AUTH | 역할메뉴권한 | ROLE_ID,MENU_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_CODE_GRP | 공통코드그룹 | CODE_GRP_ID | 이번 업무 쓰기 허용 | REQ-076 |

| TB_SY_CODE | 공통코드 | CODE_GRP_ID,CODE | 이번 업무 쓰기 허용 | REQ-071 |

| TB_SY_CODE_MAP | 엑셀표기대응 | MAP_ID | 이번 업무 쓰기 허용 | REQ-117 |

| TB_SY_OPER_STD | 운영기준값 | STD_CD,APLY_BGN_YM | 이번 업무 쓰기 허용 | REQ-102 |

| TB_SY_USER_PREF | 사용자화면설정 | USER_ID,PREF_TYPE_CD,PREF_KEY | 이번 업무 쓰기 허용 | REQ-048 |

| TB_SY_NOTI | 알림 | NOTI_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_CHG_HIST | 변경이력 | HIST_ID | 이번 업무 쓰기 허용 | REQ-125 |

| TB_SY_ACCS_LOG | 접근기록 | LOG_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_UPLD | 엑셀업로드 | UPLD_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_UPLD_ERR | 엑셀업로드검사결과 | UPLD_ID,RSLT_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_AI_QNA | AI질문기록 | QNA_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_ADM_AREA | 행정구역 | AREA_CD | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-002 |

| TB_SY_ADM_HDONG | 행정동변환 | HDONG_CD,AREA_CD | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-002 |

| TB_BS_CLNT | 거래처 | CLNT_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_BS_PART | 영업파트 | PART_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_BS_PART_STD | 영업파트기준이력 | PART_ID,APLY_BGN_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_HR_DEV | 개발자 | DEV_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_HR_DEV_SKILL | 개발자보유스킬 | DEV_ID,SKILL_NM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_HR_DEV_PRC | 개발자단가급여이력 | DEV_ID,APLY_BGN_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_HR_DEV_STAT | 개발자근무상태이력 | DEV_ID,STAT_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_HR_MON_PAY | 월별급여원가 - 부록 B.1 업로드 | PAY_YM,DEV_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_HR_CAND | 외부인력후보 | CAND_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_HR_CAND_SKILL | 후보보유스킬 | CAND_ID,SKILL_NM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_HR_CAND_CNTC | 후보연락기록 | CAND_ID,CNTC_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_PROJ | 프로젝트 | PROJ_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_PROJ_PART | 프로젝트영업파트이력 | PROJ_ID,APLY_BGN_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_PROJ_CHG | 계약변경이력 | PROJ_ID,HIST_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_MNPW_PLAN | 인원계획 | PROJ_ID,JOB_CD | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_STEP | 프로젝트단계 | PROJ_ID,STEP_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_WEEK_MEMO | 주간메모 | PROJ_ID,MEMO_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_ISSUE | 이슈 | ISSUE_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_TK_DSTRB | 턴키배분표 | PROJ_ID,DSTRB_STEP_CD,ROW_SN,DSTRB_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_DRCT_EXPS | 프로젝트직접경비 | PROJ_ID,EXPS_YM,EXPS_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_ASGN | 투입 | ASGN_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_ASGN_HIST | 투입변경이력 | ASGN_ID,HIST_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_PJ_ASGN_MON | 월별투입 - 마감 스냅샷 | ASGN_ID,INPUT_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SL_OPP | 영업기회 | OPP_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SL_OPP_NEED | 영업기회필요인력 | OPP_ID,NEED_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SL_TENT_ASGN | 가배정 | TENT_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SL_APRV_HIST | 견적승인이력 | OPP_ID,HIST_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SL_RCMD_LOG | AI추천기록 | RCMD_ID,RANK_ORD | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_BL_BILL | 청구 | BILL_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_BL_RCPT | 입금 | RCPT_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_BL_DUN_HIST | 미수알림이력 | BILL_ID,DUN_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_CO_MON_COST | 월별원가배분 - 마감 스냅샷 | COST_YM,DEV_ID,PROJ_ID,ASGN_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_CO_MON_PL | 월별손익 - 마감 스냅샷 · 2025(시작한 달 전) 이관 | PL_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_CO_PREV_ADJ | 전월조정 | ADJ_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_CO_PART_SETL | 영업파트정산 | PART_ID,SETL_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_CO_PART_SETL_DTL | 영업파트정산상세 | PART_ID,SETL_YM,DEV_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_ITEM | 재무항목 | FIN_YM,ITEM_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_CORP_TAX | 연간법인세 | TAX_YR | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_TAX_PAY | 법인세납부 | TAX_YR,PAY_TYPE_CD | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_LOAN | 대여금입출금 | LOAN_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_CARD | 법인카드 | CARD_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_CARD_MON | 법인카드월합계 | PAY_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_CARD_PAY | 법인카드월결제 | PAY_YM,CARD_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_XFER | 계좌이체지출 | XFER_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_FI_BIZ_PLAN | 사업계획목표 - 부록 B.10 | PLAN_YM,PART_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_CL_CLOSE | 월마감 | CLOSE_YM | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-066 |

| TB_CL_CLOSE_HIST | 마감이력 | CLOSE_YM,HIST_SN | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_CL_CLOSE_CHK | 마감점검결과 | CLOSE_YM,CHK_ITEM_CD | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-003 |

| TB_SY_CALC_RULE | 계산규칙 | CALC_ID | 설치만; 요청 근거가 있는 조회만, 업무 쓰기 금지 | REQ-005 |

## Entity/Table Contract

원천 우선순위는 SPEC-REQUEST.md의 요구/명확화 → 승인 spec.md·원장의 의미 → plan.md의 실행·영속성 exact 결정이다. 서로 상충하면 OQ로 보존하며 조용히 치환하지 않는다. 필드/PK/FK/DEFAULT/UK/CHECK/명시 인덱스/코멘트는 위 고정 DDL 전체를 따라야 한다. 실제 DB가 다르면 REQ-009에 따라 차이와 실패 항목을 보고하고 이름/타입을 추정 보정하지 않는다. 추가 version 컬럼·이력용 임의 MOD 필드·파일 저장용 컬럼을 만들지 않는다.

### 핵심 집합의 책임과 생명주기

| 집합 | 원천·업무 목적 | 생명주기 소유자·쓰기 경계 | 업무 삭제·이력 정책 | canonical_id |
| --- | --- | --- | --- | --- |
| TB_SY_CODE_GRP | DB 전체 그룹; 시스템/값 여부와 정렬 원천 | createCodeGroup은 신규 일반 그룹만; 기존 이름/사용/삭제 기능 없음 | 신규 SYS=N/VAL=N/USE=Y, SORT=max+1, NEW 이력; 그룹 삭제 금지 | REQ-069, REQ-075, REQ-076, REQ-153 |
| TB_SY_CODE | 그룹 소속 코드·공통 선택 값/단위/추가값 | saveGroupCodes가 그룹 전체 revision을 검증; SYS 우선 이름만, 일반 값그룹은 기존 codeVal만 | 일반코드 USE=N/Y 중지/재개·DEL_YN=Y 논리삭제; 물리행/PK 유지; 변경/STOP/RESUME/DEL 이력 | REQ-071, REQ-074, REQ-089, REQ-092, REQ-095, REQ-096 |
| TB_SY_CODE_MAP | 엑셀 표기 sourceText를 실제 그룹/코드에 대응 | createCodeMapping은 즉시 추가, deleteCodeMappings는 다중 원자 삭제; 수정 endpoint 없음 | 물리DELETE 전 sourceText/groupId/code/codeName JSON을 DEL 이력에 보존 | REQ-116, REQ-117, REQ-118, REQ-119, REQ-120 |
| TB_SY_OPER_STD | 적용월별 기준값의 정본; 현재/과거/예정 조회 | saveOperatingStandards가 요청월·키별 전체이력과 묶음 경계 검증 | 일반은 월별 UPDATE/INSERT; CASH두키는 기존 적용월을 보존한 UPDATE만; 키생성/삭제 없음 | REQ-099, REQ-100, REQ-101, REQ-102, REQ-111, REQ-113, REQ-154 |
| TB_SY_USER_PREF | admin의 서버 저장 설정; JSON 문자열 | putPreference가 type/key별 JSON 검증·명시 저장, 최초 기본값도 INSERT | deletePreference로 지정 행만 물리삭제; 다른 설정 유지, UI-27 이력 없음 | REQ-047, REQ-048, REQ-049, REQ-050, REQ-061 |
| TB_SY_CHG_HIST | 위 그룹/코드/값/기준/대응의 관찰 가능한 변경 내역 | 성공 본체 변경과 같은 트랜잭션으로 append; 등록자/등록시각만 사용 | 수정·삭제·만료 정리 없음, 무기한 보존; no-op은 기록 없음 | REQ-011, REQ-028, REQ-031, REQ-123, REQ-125, REQ-131, REQ-155 |
| TB_SY_MENU | 이름/상위/화면ID/아이콘/PC전용 원천 | getMenus 조회·잘못된 트리 제외/진단; DB 자동 수리 없음 | 데이터 유지; 조회 진단이 DML로 바뀌지 않음 | REQ-035, REQ-037 |
| TB_CL_CLOSE | 실제 마감 상태 조회와 기준값 쓰기 보호 근거 | CLOSE/WORK/행 없음 판정만; 마감 실행 서비스 없음 | 이번 단계 신규마감·해제·스냅샷 생성 없음 | REQ-066, REQ-113, REQ-148 |
| TB_SY_USER·TB_SY_USER_ROLE·TB_SY_ROLE | 초기 admin 및 역할 연결 확인 | CurrentUserProvider 단일 교체 지점, 인증/실제권한은 이번 미적용 | 사용자·역할 변경 API/비밀번호 흐름을 만들지 않음 | REQ-025, REQ-026, REQ-027 |
| TB_SY_ADM_AREA·TB_SY_ADM_HDONG | 행정구역 DDL·제약 보존 | 구조만 생성, 데이터0행 | 주소입력·파일 확보/복사/적재 제외 | REQ-002, REQ-151 |
| TB_SY_CALC_RULE | 계산규칙 11컬럼·원문 데이터94행 보존 | 설치만; 편집·계산 엔진 없음 | 데이터/규칙을 임의 재작성하지 않음 | REQ-003, REQ-005, REQ-152 |

### 전수 필드 계약

Constraint에는 PK 구성 여부·nullable·DEFAULT·단일열 unique를 명시한다. 복합 UK의 유일성은 필드별 unique=true가 아니라 아래 키 목록으로 보존한다. SQL CHECK의 Y/N은 YesNo, 이력 유형은 ChangeType, 설정 유형은 PreferenceType을 OpenAPI components.schemas 한곳에서 참조한다. 코드 그룹의 동적 CODE 값들을 정적 enum으로 고정하지 않는다. DDL 코멘트에 향후 상태/업무 설명이 있더라도 이번 허용 쓰기나 새 API로 승격하지 않는다. Type은 실제 SQL type이고 JSON nullable/pk/fk는 아래 행에서만 투영한다.

| Entity | Field | Type | Constraint | Enum | Relationship | Audit/Delete Policy | canonical_id |
| --- | --- | --- | --- | --- | --- | --- | --- |

| TB_SY_USER | USER_ID | VARCHAR(30) | PK 구성열; NOT NULL; 사용자ID: 로그인 아이디(예: fin01). 중복 불가; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | USER_NM | VARCHAR(50) | NOT NULL; 사용자명; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | EMAIL_ADDR | VARCHAR(100) | NOT NULL; 이메일주소: 임시 비밀번호·알림 발송; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | PWD_HASH | VARCHAR(200) | NOT NULL; 비밀번호해시: 단방향 해시 BCrypt(강도 10, 60자)(D-8); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | PWD_CHG_DTTM | TIMESTAMP | NULL 허용; 비밀번호변경일시: 90일 변경 안내 기준; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | TEMP_PWD_YN | CHAR(1) | NOT NULL; 임시비밀번호여부: Y면 첫 로그인 때 변경 강제; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | PWD_ERR_CNT | NUMERIC(2) | NOT NULL; 비밀번호오류횟수: 기본 0, 5회면 잠김; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | USER_STAT_CD | VARCHAR(10) | NOT NULL; 사용자상태코드: USE 사용 / LOCK 잠김 / STOP 중지; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | LAST_LOGIN_DTTM | TIMESTAMP | NULL 허용; 마지막접속일시: UI-23 목록; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | MFA_USE_YN | CHAR(1) | NOT NULL; 2단계인증사용여부: 시스템 관리자 역할이 있으면 자동 Y(이메일 인증), 역할을 빼면 N(2026-10-29); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | MFA_CODE_HASH | VARCHAR(200) | NULL 허용; 2단계인증번호해시: 이메일로 보낸 6자리 숫자의 단방향 해시. 다시 보내면 새 값으로 덮어써 이전 번호는 못 씀, 성공하면 NULL(2026-10-29 A25); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | MFA_EXPR_DTTM | TIMESTAMP | NULL 허용; 2단계인증만료일시: 보낸 시각 + 5분. 지나면 [다시 보내기] 안내; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | MFA_ERR_CNT | NUMERIC(2) | NOT NULL; 2단계인증오류횟수: 기본 0. 5번 틀리면 로그인 처음으로(계정 잠금 PWD_ERR_CNT와 따로), 비밀번호 로그인으로 새 번호를 보낼 때 0; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_USER | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-025 |

| TB_SY_ROLE | ROLE_ID | VARCHAR(20) | PK 구성열; NOT NULL; 역할ID: 기본 5개 ADMIN, EXEC, FIN, SALES_LDR, PM. 새로 만든 역할은 R-nnn 순번 자동(R-001부터, 지운 번호 재사용 안 함, 2026-10-29); NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | ROLE_NM | VARCHAR(50) | NOT NULL; 단일열 UNIQUE; 역할명: 시스템 관리자 / 경영진 / 재무 담당 / 영업 파트장 / 일반 사용자(PM); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | DATA_SCOPE_CD | VARCHAR(10) | NOT NULL; 데이터범위코드: ALL 전체 / PART 담당 파트 / PROJ 담당 프로젝트 (SELF 본인은 뺌, N-24-1); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | SNSTV_VIEW_YN | CHAR(1) | NOT NULL; 민감항목보기여부: 급여·지급 단가·개인별 원가·계좌; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | SYS_ROLE_YN | CHAR(1) | NOT NULL; 시스템역할여부: 기본 5개 역할은 Y(삭제 불가); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | SORT_ORD | NUMERIC(5) | NOT NULL; 정렬순서: 권한 화면 역할 탭 순서. 기본 5개는 1~5(시스템 관리자·경영진·재무 담당·영업 파트장·일반 사용자(PM)), 새로 만든 역할은 그때 가장 큰 번호 + 1. 바꾸는 화면 없음. 사용 중지해도 번호는 그대로(2026-10-30 A28); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | USE_YN | CHAR(1) | NOT NULL; 사용여부: N = 사용 중지(추가한 역할만, 시스템 관리자만, 그 역할 사용자가 없을 때). 중지 역할은 역할 탭·사용자 등록 목록에서 숨김(2026-10-29); DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_ROLE | USER_ID | VARCHAR(30) | PK 구성열; NOT NULL; 사용자ID: → TB_SY_USER; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_ROLE | ROLE_ID | VARCHAR(20) | PK 구성열; NOT NULL; 역할ID: → TB_SY_ROLE. 1명당 1개 이상(Q-23-1 사용자 결정 2026-10-19: 여러 개 허용); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_ROLE.ROLE_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_ROLE | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_ROLE | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_ROLE | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_ROLE | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | USER_ID | VARCHAR(30) | PK 구성열; NOT NULL; 사용자ID: → TB_SY_USER; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | SCOPE_SN | NUMERIC(5) | PK 구성열; NOT NULL; 범위일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | SCOPE_TYPE_CD | VARCHAR(10) | NOT NULL; 범위유형코드: PART / PROJ; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | PART_ID | VARCHAR(20) | NULL 허용; 영업파트ID: SCOPE_TYPE_CD=PART일 때. 'HQ'(본사 영업 책임자)만 저장. 영업 파트의 파트장 범위는 TB_BS_PART_STD.PART_LDR_USER_ID(이번 달 줄)에서 읽음(2026-10-29); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_PART.PART_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | PROJ_ID | VARCHAR(20) | NULL 허용; 프로젝트ID: SCOPE_TYPE_CD=PROJ일 때. PM 역할 사용자의 담당 프로젝트 = 그 프로젝트의 PM(프로젝트당 1명, 35번 3); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_USER_SCOPE | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_MENU | MENU_ID | VARCHAR(20) | PK 구성열; NOT NULL; 메뉴ID: 시스템이 자동으로 매김(입력칸 없음). 대메뉴 G001·G002…, 서브메뉴 M001·M002… 순번. 한 번 정하면 바뀌지 않음(2026-10-29 사용자 결정); NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | UPPER_MENU_ID | VARCHAR(20) | NULL 허용; 상위메뉴ID: NULL이면 메뉴 묶음; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_MENU.MENU_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | MENU_NM | VARCHAR(50) | NOT NULL; 메뉴명: 메뉴 화면에서 관리자가 수정 가능(Q-25-1); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | SCRN_ID | VARCHAR(10) | NULL 허용; 화면ID: 화면 번호(UI-nn). React 화면(라우트)과 짝. 묶음은 NULL; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | MENU_LVL | NUMERIC(1) | NOT NULL; 메뉴레벨: 1 묶음(대메뉴) / 2 화면(서브메뉴). 등록할 때 고르고, 2면 상위 묶음을 고른다; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | SORT_ORD | NUMERIC(5) | NOT NULL; 정렬순서: 끌어서 변경; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | USE_YN | CHAR(1) | NOT NULL; 사용여부: N이면 모든 사용자에게 숨김(데이터는 유지); DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | PC_ONLY_YN | CHAR(1) | NOT NULL; PC전용여부: 메뉴 화면의 “PC 전용” 체크(). Y면 휴대폰 메뉴·즐겨찾기·최근에서 빠지고 주소로 직접 열어도 막는다(). 초기값 UI-10·14·23·24·25·27 = Y. 휴대폰 표시 컬럼은 이 컬럼 하나로 합쳤다(2026-10-19); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | ICON_NM | VARCHAR(30) | NULL 허용; 아이콘명: 대메뉴만. 처음 들어 있는 대메뉴 10개는 시안 아이콘(chart·folder·wallet 등), 새로 등록한 대메뉴는 folder 고정(고르는 기능 없음). 서브메뉴는 NULL(2026-10-29 사용자 결정); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | LOCK_YN | CHAR(1) | NOT NULL; 끄기금지여부: 시스템 묶음 메뉴 전체(사용자·권한·메뉴·공통코드) = Y (Q-25-3 사용자 결정 2026-10-21). Y면 끄기 금지 + 다른 묶음으로 이동 금지(Q-25-2); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_MENU | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-035 |

| TB_SY_ROLE_MENU_AUTH | ROLE_ID | VARCHAR(20) | PK 구성열; NOT NULL; 역할ID: → TB_SY_ROLE; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_ROLE.ROLE_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | MENU_ID | VARCHAR(20) | PK 구성열; NOT NULL; 메뉴ID: → TB_SY_MENU (화면 메뉴만); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_MENU.MENU_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | INQ_YN | CHAR(1) | NOT NULL; 조회여부: 새 메뉴는 모든 역할 N으로 생성; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | REG_YN | CHAR(1) | NOT NULL; 등록여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | UPD_YN | CHAR(1) | NOT NULL; 수정여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | DEL_YN | CHAR(1) | NOT NULL; 삭제여부: 사용중지 포함; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | UPLD_YN | CHAR(1) | NOT NULL; 업로드여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | DNLD_YN | CHAR(1) | NOT NULL; 다운로드여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | CNFM_YN | CHAR(1) | NOT NULL; 확정여부: 월 마감·파트 정산 확정·견적 승인(Q-공통-9 사용자 결정 2026-10-22. 청구 확정은 v2라 2026-10-30 D19에서 뺌); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_CODE_GRP | CODE_GRP_ID | VARCHAR(30) | PK 구성열; NOT NULL; 코드그룹ID: 예: CTRT_TYPE. 새 그룹은 [그룹 추가]에서 직접 입력: 영문 대문자로 시작, 영문 대문자·숫자·_ 30자까지, 중복 불가, 만든 뒤 변경 불가(2026-10-29 A23); NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | CODE_GRP_NM | VARCHAR(50) | NOT NULL; 코드그룹명; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | SYS_CODE_YN | CHAR(1) | NOT NULL; 시스템코드여부: Y면 이름만 수정, 추가·삭제·사용끄기 금지. 명세서 공통-16의 시스템 코드 그룹 11개 + 프로젝트 상태·거래처 구분·데이터 범위 = 14개(Q-27-2 결정 2026-10-21, 단가 기준 2026-10-27 삭제) + 테이블 코드 칸 고정값 30개(2026-10-28, 2026-10-29 자금수지 3개 삭제, 2026-10-30 XFER_CLS 추가) = 44개(5장 시스템 여부 Y); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | VAL_CODE_YN | CHAR(1) | NOT NULL; 값코드여부: Y면 CODE_VAL·UNIT 사용(수당코드); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | SORT_ORD | NUMERIC(5) | NOT NULL; 정렬순서; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | USE_YN | CHAR(1) | NOT NULL; 사용여부; DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE_GRP | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-076 |

| TB_SY_CODE | CODE_GRP_ID | VARCHAR(30) | PK 구성열; NOT NULL; 코드그룹ID: → TB_SY_CODE_GRP; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_CODE_GRP.CODE_GRP_ID | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | CODE | VARCHAR(20) | PK 구성열; NOT NULL; 코드: 그룹 안 중복 불가; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | CODE_NM | VARCHAR(100) | NOT NULL; 코드명: 표에서 바로 수정; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | SORT_ORD | NUMERIC(5) | NOT NULL; 정렬순서; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | USE_YN | CHAR(1) | NOT NULL; 사용여부: 쓴 코드는 삭제 대신 N; DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | DEL_YN | CHAR(1) | NOT NULL; 삭제여부: 기본 N. 한 번도 쓰지 않은 업무 코드를 UI-27 [삭제]하면 Y(행은 남기고 코드 표·선택 목록에서 뺌, 같은 코드 값 재사용 불가). 쓴 코드는 Y로 못 바꿈(2026-11-01 사용자 결정 16/18, 04 ); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | CODE_VAL | NUMERIC(15,4) | NULL 허용; 코드값: 수당코드: 200000, 9.4 등. 「수당코드 추가」 창의 처음 값 0(2026-10-31 필수 표시); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | UNIT_NM | VARCHAR(10) | NULL 허용; 단위명: 원 / %; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | ADD_VAL1 | VARCHAR(100) | NULL 허용; 추가값1: 그룹마다 뜻이 다름(JOB=관련 직무, SKILL=필요한 직무, PROJ_STEP=비중, BANK=은행 코드, RSM_LABEL=넣을 칸, RSM_JOB_KW=직무, 2026-11-03). 이 여섯 그룹에선 코드 추가 때 필수(화면에서 검사), 다른 그룹은 비움(2026-10-31 필수 표시); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | ADD_VAL2 | VARCHAR(100) | NULL 허용; 추가값2: 그룹마다 뜻이 다름(JOB=견적 기준 단가, PROJ_STEP=적용 계약유형). 이 두 그룹에선 코드 추가 때 필수(화면에서 검사)(2026-10-31 필수 표시); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | ADD_VAL3 | VARCHAR(100) | NULL 허용; 추가값3: 예비. 지금 쓰는 그룹 없음; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 일반 미참조 코드 DEL_YN=Y; 물리행·PK·이력 보존, 시스템/값 삭제 금지 | REQ-071 |

| TB_SY_CODE_MAP | MAP_ID | NUMERIC(10) | PK 구성열; NOT NULL; 대응ID: 시퀀스; DEFAULT nextval('SQ_TB_SY_CODE_MAP') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 확인 후 물리삭제; 이전 JSON DEL 이력과 단일 트랜잭션 | REQ-117 |

| TB_SY_CODE_MAP | SRC_TEXT | VARCHAR(100) | NOT NULL; 엑셀표기: 예: ‘업체’, ‘정직원’; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 확인 후 물리삭제; 이전 JSON DEL 이력과 단일 트랜잭션 | REQ-117 |

| TB_SY_CODE_MAP | CODE_GRP_ID | VARCHAR(30) | NOT NULL; 코드그룹ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_CODE.CODE_GRP_ID | 확인 후 물리삭제; 이전 JSON DEL 이력과 단일 트랜잭션 | REQ-117 |

| TB_SY_CODE_MAP | CODE | VARCHAR(20) | NOT NULL; 코드: 바꿔 읽을 코드; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_CODE.CODE | 확인 후 물리삭제; 이전 JSON DEL 이력과 단일 트랜잭션 | REQ-117 |

| TB_SY_CODE_MAP | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 확인 후 물리삭제; 이전 JSON DEL 이력과 단일 트랜잭션 | REQ-117 |

| TB_SY_CODE_MAP | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 확인 후 물리삭제; 이전 JSON DEL 이력과 단일 트랜잭션 | REQ-117 |

| TB_SY_CODE_MAP | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 확인 후 물리삭제; 이전 JSON DEL 이력과 단일 트랜잭션 | REQ-117 |

| TB_SY_CODE_MAP | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 확인 후 물리삭제; 이전 JSON DEL 이력과 단일 트랜잭션 | REQ-117 |

| TB_SY_OPER_STD | STD_CD | VARCHAR(30) | PK 구성열; NOT NULL; 기준값코드: §6 초기값 표. 값 하나에 코드 하나(화면에서 한 줄에 칸이 여러 개인 묶음 값도 칸마다 코드 따로, 2026-10-29); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | APLY_BGN_YM | CHAR(6) | PK 구성열; NOT NULL; 적용시작연월: 이력 관리; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | STD_NM | VARCHAR(100) | NOT NULL; 기준값명; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | STD_VAL | NUMERIC(15,4) | NOT NULL; 기준값: 숫자 하나. 연월(CASH_BASE_YM)은 YYYYMM 숫자(202601); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | UNIT_NM | VARCHAR(10) | NOT NULL; 단위명: %, %p, 원, 일, 명, 개월, MM, 회, 분, 점, 연월; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | STD_DESC | VARCHAR(300) | NULL 허용; 기준값설명: 화면 「쓰는 곳」 칸(예: 식-89); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_OPER_STD | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 서버 등록/수정 필드; 업무이력 동시 저장; 삭제 기능 금지 | REQ-102 |

| TB_SY_USER_PREF | USER_ID | VARCHAR(30) | PK 구성열; NOT NULL; 사용자ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 지정 admin/type/key만 초기화 물리삭제; UI-27 이력 기록 안 함 | REQ-048 |

| TB_SY_USER_PREF | PREF_TYPE_CD | VARCHAR(20) | PK 구성열; NOT NULL; 설정유형코드: FAV 즐겨찾기 / RECENT 최근 / COLS 열 설정 / PAGE_SIZE / AMT_UNIT / MENU_FOLD / DENSE / PERIOD; NOT NULL; 복합키의 구성열 | FAV/RECENT/MENU_FOLD/PAGE_SIZE/DENSE/COLS/AMT_UNIT/PERIOD | 명시 FK 없음 | 지정 admin/type/key만 초기화 물리삭제; UI-27 이력 기록 안 함 | REQ-048 |

| TB_SY_USER_PREF | PREF_KEY | VARCHAR(30) | PK 구성열; NOT NULL; 설정키: 화면ID 또는 '*'. COLS 열 설정은 화면ID(화면마다), PAGE_SIZE·DENSE는 '*'(계정에 한 값, 02 ·05, 2026-10-31 C22); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 지정 admin/type/key만 초기화 물리삭제; UI-27 이력 기록 안 함 | REQ-048 |

| TB_SY_USER_PREF | PREF_VAL | VARCHAR(2000) | NOT NULL; 설정값: JSON 문자열; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 지정 admin/type/key만 초기화 물리삭제; UI-27 이력 기록 안 함 | REQ-048 |

| TB_SY_USER_PREF | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 지정 admin/type/key만 초기화 물리삭제; UI-27 이력 기록 안 함 | REQ-048 |

| TB_SY_USER_PREF | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 지정 admin/type/key만 초기화 물리삭제; UI-27 이력 기록 안 함 | REQ-048 |

| TB_SY_USER_PREF | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 지정 admin/type/key만 초기화 물리삭제; UI-27 이력 기록 안 함 | REQ-048 |

| TB_SY_USER_PREF | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 지정 admin/type/key만 초기화 물리삭제; UI-27 이력 기록 안 함 | REQ-048 |

| TB_SY_NOTI | NOTI_ID | NUMERIC(12) | PK 구성열; NOT NULL; 알림ID: 시퀀스; DEFAULT nextval('SQ_TB_SY_NOTI') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | RCVR_USER_ID | VARCHAR(30) | NOT NULL; 수신사용자ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | NOTI_TYPE_CD | VARCHAR(20) | NOT NULL; 알림유형코드: DUN 미수 / WTHDR / CASH_SHORT / UPLD_DONE / AI_DONE / QUOTE_REJ (6종, Q-공통-7); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | NOTI_CN | VARCHAR(300) | NOT NULL; 알림내용; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | LINK_SCRN_ID | VARCHAR(10) | NULL 허용; 연결화면ID: 누르면 이동; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | LINK_KEY | VARCHAR(50) | NULL 허용; 연결키: 청구번호, 프로젝트ID 등; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | SEND_MTHD_CD | VARCHAR(10) | NOT NULL; 발송방법코드: SYS / MAIL / BOTH; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | READ_YN | CHAR(1) | NOT NULL; 읽음여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | READ_DTTM | TIMESTAMP | NULL 허용; 읽은일시; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | NOTI_DTTM | TIMESTAMP | NOT NULL; 알림일시: 30일 지나면 삭제 배치; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_NOTI | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_CHG_HIST | HIST_ID | NUMERIC(12) | PK 구성열; NOT NULL; 이력ID: 시퀀스; DEFAULT nextval('SQ_TB_SY_CHG_HIST') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | TRGT_TBL_NM | VARCHAR(30) | NOT NULL; 대상테이블명: TB_BS_CLNT, TB_HR_DEV, TB_SY_CODE, TB_SY_USER, TB_SY_ROLE_MENU_AUTH, TB_SY_OPER_STD 등; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | TRGT_KEY | VARCHAR(100) | NOT NULL; 대상키: 대상 행의 PK 문자열; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | CHG_TYPE_CD | VARCHAR(10) | NOT NULL; 변경유형코드: 공통코드 CHG_TYPE: NEW 신규 등록 / UPD 수정 / STOP 사용중지 / RESUME 다시 사용 / DEL 삭제(엑셀 표기 대응 줄 삭제). 퇴사 취소는 UPD(항목 「퇴사 취소」, CHG_RSN = 취소 사유)(2026-10-31 C1); NOT NULL; 비PK | NEW/UPD/STOP/RESUME/DEL | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | CHG_ITEM_NM | VARCHAR(50) | NULL 허용; 변경항목명: 화면 표시용 한글 항목명; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | BFR_VAL | VARCHAR(500) | NULL 허용; 변경전값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | AFT_VAL | VARCHAR(500) | NULL 허용; 변경후값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | CHG_RSN | VARCHAR(500) | NULL 허용; 변경사유; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_CHG_HIST | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 등록 필드만 append; 변경·삭제·기간 만료 삭제 금지; 무기한 보존 | REQ-125 |

| TB_SY_ACCS_LOG | LOG_ID | NUMERIC(15) | PK 구성열; NOT NULL; 로그ID: 시퀀스; DEFAULT nextval('SQ_TB_SY_ACCS_LOG') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ACCS_LOG | USER_ID | VARCHAR(30) | NOT NULL; 사용자ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ACCS_LOG | ACT_TYPE_CD | VARCHAR(20) | NOT NULL; 작업유형코드: LOGIN / LOGOUT / LOGIN_FAIL / DNLD / CLOSE / UNCLOSE / AUTH_CHG / USER_REG / SNSTV_VIEW / ACCT_VIEW (비밀번호 초기화·잠금 해제는 남기지 않음, Q-23-3); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ACCS_LOG | SCRN_ID | VARCHAR(10) | NULL 허용; 화면ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ACCS_LOG | TRGT_DESC | VARCHAR(300) | NULL 허용; 대상설명: 예: “개인 원가·수익표”, “2026-09”; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ACCS_LOG | CLIENT_IP | VARCHAR(45) | NULL 허용; 접속IP; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ACCS_LOG | ACT_DTTM | TIMESTAMP | NOT NULL; 작업일시: 보관 3년(D-26); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ACCS_LOG | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ACCS_LOG | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | UPLD_ID | NUMERIC(12) | PK 구성열; NOT NULL; 업로드ID; DEFAULT nextval('SQ_TB_SY_UPLD') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | FORM_TYPE_CD | VARCHAR(10) | NOT NULL; 양식유형코드: B1 급여 / B2 재무항목 / B5 투입목록 / B6 턴키배분표 / B7 거래처 / B8 개발자 / B9 프로젝트 / B10 사업계획 / B11 전년손익; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | SCRN_ID | VARCHAR(10) | NOT NULL; 화면ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | FILE_NM | VARCHAR(200) | NOT NULL; 파일명: xlsx만; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | FILE_SIZE | NUMERIC(12) | NOT NULL; 파일크기: 10MB 제한(Q-공통-20 사용자 결정 2026-10-22); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | TRGT_YM | CHAR(6) | NULL 허용; 대상연월: 한 달 양식; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | TRGT_PROJ_ID | VARCHAR(20) | NULL 허용; 대상프로젝트ID: B6; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | TRGT_DSTRB_STEP_CD | VARCHAR(10) | NULL 허용; 대상배분단계코드: B6. 올릴 때 고른 단계(EST 견적 / CTRT 최종 계약 / PLAN 실제 투입계획). 같은 단계를 다시 올리면 배분표 줄은 바뀌어도 「올린 기록」에서 예전 업로드의 단계를 보여 주려고 둠(2026-10-29 테이블 검증 2장 B4); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | TOT_ROW_CNT | NUMERIC(7) | NOT NULL; 전체행수: 5,000행 제한; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | SUCC_CNT | NUMERIC(7) | NOT NULL; 성공건수; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | WARN_CNT | NUMERIC(7) | NOT NULL; 경고건수; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | ERR_CNT | NUMERIC(7) | NOT NULL; 오류건수; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | UPLD_STAT_CD | VARCHAR(10) | NOT NULL; 업로드상태코드: CHECKED 검사완료(반영 안 하고 창을 닫으면 이 상태로 남음) / APPLIED 반영 / CANCEL. 검사를 통과한 줄은 따로 두지 않고 [반영] 때 같은 파일을 다시 검사해 저장(02 , 2026-10-31 C21); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | APLY_DTTM | TIMESTAMP | NULL 허용; 반영일시; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | UPLD_ID | NUMERIC(12) | PK 구성열; NOT NULL; 업로드ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_UPLD.UPLD_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | RSLT_SN | NUMERIC(7) | PK 구성열; NOT NULL; 결과일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | RSLT_TYPE_CD | VARCHAR(10) | NOT NULL; 결과유형코드: WARN / ERR; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | ROW_NO | NUMERIC(7) | NOT NULL; 행번호: 엑셀 행; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | COL_NM | VARCHAR(50) | NULL 허용; 열이름; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | INPUT_VAL | VARCHAR(200) | NULL 허용; 입력값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | RSN_CN | VARCHAR(300) | NOT NULL; 사유내용: 예: “’정직원’을 ’정규직’으로 바꿔 읽었습니다”; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_UPLD_ERR | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | QNA_ID | NUMERIC(12) | PK 구성열; NOT NULL; 질의응답ID; DEFAULT nextval('SQ_TB_SY_AI_QNA') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | USER_ID | VARCHAR(30) | NOT NULL; 사용자ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | QSTN_CN | VARCHAR(1000) | NOT NULL; 질문내용; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | ANS_CN | TEXT | NULL 허용; 답변내용: 결론·표·가정; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | USE_DATA_DESC | VARCHAR(500) | NULL 허용; 사용데이터설명: 데이터, 기간, 기준월; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | BASE_YM | CHAR(6) | NULL 허용; 기준연월; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | PRVS_YN | CHAR(1) | NOT NULL; 잠정여부: 마감 전 달 숫자면 Y; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | ANS_STAT_CD | VARCHAR(10) | NOT NULL; 답변상태코드: OK 답변(권한 밖 숫자를 빼고 답한 것 포함) / NO_ANS 답할 수 없음(데이터에 없음·원인 판단 불가·권한 밖 질문이라 답하지 않음, 화면 「이 내용을 볼 권한이 없습니다」 포함) / ERR 오류. 명세서 v1.9 「급여 없이 답하거나 답할 수 없음」(2026-10-30 C19); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | QSTN_DTTM | TIMESTAMP | NOT NULL; 질문일시; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_AI_QNA | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_ADM_AREA | AREA_CD | VARCHAR(10) | PK 구성열; NOT NULL; 행정구역코드: 법정동코드 10자리. 시·도 단계(예: 1100000000 서울)·시·군·구 단계(1121500000 광진구)·읍·면·동 단계(자양동) 줄이 모두 있음(숫자는 설명용 예시); NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | AREA_LVL_CD | VARCHAR(10) | NOT NULL; 단계코드: SIDO 시·도 / SGG 시·군·구 / EMD 읍·면·동; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | UPPER_AREA_CD | VARCHAR(10) | NULL 허용; 상위행정구역코드: → TB_SY_ADM_AREA. 시·군·구는 시·도, 읍·면·동은 시·군·구(세종처럼 시·군·구가 없으면 시·도). 시·도 단계는 NULL. 화면 목록은 이 칸으로 거름; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_ADM_AREA.AREA_CD | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | SIDO_NM | VARCHAR(20) | NOT NULL; 시도명: 줄임 이름(서울·부산·경기·세종·제주 …). HOME_ADDR 맨 앞에 쓰고 근무지 점수 비교(식-70)에 쓰는 이름; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | SIDO_FULL_NM | VARCHAR(30) | NOT NULL; 시도정식명: 파일의 정식 이름(서울특별시·경기도·강원특별자치도 …). 엑셀·근무지 글자를 줄임 이름으로 맞출 때 씀; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | SGG_NM | VARCHAR(30) | NULL 허용; 시군구명: 예: 광진구, 성남시 분당구(두 단어도 한 값). 시·도 단계와 시·군·구가 없는 시·도는 NULL; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | EMD_NM | VARCHAR(30) | NULL 허용; 읍면동명: 법정동 이름(예: 자양동, 조치원읍). 시·도·시·군·구 단계는 NULL; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | USE_YN | CHAR(1) | NOT NULL; 사용여부: 새 파일에서 없어진 구역은 지우지 않고 N. 목록에는 Y만 나오고, 이미 저장된 거주지는 N이어도 그대로 보임; DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_AREA | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_HDONG | HDONG_CD | VARCHAR(10) | PK 구성열; NOT NULL; 행정동코드: 행정동코드 10자리; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_HDONG | AREA_CD | VARCHAR(10) | PK 구성열; NOT NULL; 행정구역코드: → TB_SY_ADM_AREA. 이 행정동이 걸친 법정동(읍·면·동 단계). 행정동 하나가 법정동 여러 개에 걸치면 여러 줄; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_ADM_AREA.AREA_CD | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_HDONG | HDONG_NM | VARCHAR(30) | NOT NULL; 행정동명: 파일 표기 그대로(예: 자양제3동). 같은 시·군·구 안에서 이 이름으로 찾되 숫자 앞 「제」는 빼고 비교(자양3동 = 자양제3동, 02 ); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_HDONG | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_HDONG | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_HDONG | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_SY_ADM_HDONG | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-002 |

| TB_BS_CLNT | CLNT_ID | VARCHAR(20) | PK 구성열; NOT NULL; 거래처ID: C-001; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | CLNT_TYPE_CD | VARCHAR(10) | NOT NULL; 거래처구분코드: CUST 고객사 / VNDR 협력사 / BOTH 고객·협력; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | CLNT_NM | VARCHAR(50) | NOT NULL; 상호: 같은 상호 허용(사업자번호가 다르면 경고만, Q-15-2); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | BIZ_REG_NO | VARCHAR(12) | NOT NULL; 단일열 UNIQUE; 사업자등록번호: 000-00-00000. 등록 후 변경 불가. 형식·중복만 검사, 국세청 조회 없음(Q-15-5); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | CEO_NM | VARCHAR(20) | NULL 허용; 대표자명; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | INDUTY_CD | VARCHAR(10) | NULL 허용; 업종코드: 공통코드 INDUTY(35번 5). AI 추천 같은 업종 = 같은 코드; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | CHRG_NM | VARCHAR(20) | NULL 허용; 담당자명; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | TELNO | VARCHAR(20) | NULL 허용; 연락처; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | TAX_EMAIL_ADDR | VARCHAR(100) | NULL 허용; 세금계산서이메일: CUST·BOTH 필수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | BANK_CD | VARCHAR(10) | NULL 허용; 은행코드: VNDR·BOTH 필수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | ACCT_NO_ENC | VARCHAR(200) | NULL 허용; 계좌번호(암호화): 숫자·하이픈 8~20자 원문을 AES-256-GCM으로 암호화(IV·암호문·인증 태그를 Base64로 저장, 약 64자). 키는 서버 환경변수(D-8). 화면은 끝 4자리; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | DPSTR_NM | VARCHAR(30) | NULL 허용; 예금주명: VNDR·BOTH 필수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | USE_YN | CHAR(1) | NOT NULL; 사용여부: 진행 프로젝트·소속 인력 있으면 N 불가; DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_CLNT | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART | PART_ID | VARCHAR(20) | PK 구성열; NOT NULL; 영업파트ID: 'HQ'는 본사(직접 영업) 예약; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART | PART_NM | VARCHAR(30) | NOT NULL; 단일열 UNIQUE; 영업파트명: 같은 이름 불가. 바꾸면 화면 표시는 모두 바뀜(ID 참조); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART | HQ_YN | CHAR(1) | NOT NULL; 본사직접영업여부: Y면 파트 이익금 정산 제외; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART | USE_YN | CHAR(1) | NOT NULL; 사용여부: 소속 인력·진행 프로젝트 있으면 N 불가; DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | PART_ID | VARCHAR(20) | PK 구성열; NOT NULL; 영업파트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_PART.PART_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | APLY_BGN_YM | CHAR(6) | PK 구성열; NOT NULL; 적용시작연월: 마감월 다음 달 이후만. 이익 배분율·공통관리비가 바뀌는 줄은 이 달 이후에 TB_CO_PART_SETL.SETL_STAT_CD=CNFM인 달이 있으면 저장 불가(확정 취소 후 변경). 정산 테이블에 배분율을 따로 저장하지 않으므로 확정된 달의 머리글 배분율과 고정 이익금이 어긋나지 않게 함(2026-10-29 D14); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | APLY_END_YM | CHAR(6) | NULL 허용; 적용종료연월: 다음 기준 등록 시 자동 설정, 현재 기준은 NULL; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | PART_LDR_USER_ID | VARCHAR(30) | NOT NULL; 파트장사용자ID: 사용 중이고 영업 파트장 역할인 사용자. 한 사람이 여러 파트 가능(Q-15-3). 이번 달 줄의 파트장 = 그 파트의 데이터 범위·미수 2단계 대상(2026-10-29); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | PRFT_DSTRB_RT | NUMERIC(5,2) | NOT NULL; 이익배분율: 0~100, 기본 40(예시값, 파트마다 다름, Q-15-6); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | SALES_EXPS_AMT | NUMERIC(15) | NOT NULL; 공통관리비금액: 1인·월, 기본 1,500,000(예시값, 파트마다 다름); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BS_PART_STD | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | DEV_ID | VARCHAR(20) | PK 구성열; NOT NULL; 개발자ID: 내부 키; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | MNG_NO | VARCHAR(20) | NOT NULL; 단일열 UNIQUE; 관리번호: K-0001. 비우면 자동(이미 쓰는 번호는 건너뜀), 직접 넣으면 영문·숫자·하이픈 20자 이내. 등록 후 수정 불가; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | DEV_NM | VARCHAR(50) | NOT NULL; 개발자명; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | EMP_TYPE_CD | VARCHAR(10) | NOT NULL; 직원구분코드: FT 정규직 / CT 계약직 / FL 프리랜서 / OS 외주 (시스템); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | BLNG_CLNT_ID | VARCHAR(20) | NULL 허용; 소속거래처ID: NULL = 자사, 외주는 협력사 필수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_CLNT.CLNT_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | GRADE_CD | VARCHAR(10) | NULL 허용; 등급코드: JR 초급 / MD 중급 / SR 고급 / EX 특급; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | JOB_CD | VARCHAR(10) | NOT NULL; 주직무코드: PM, PL, PLN, ARC, BE, FE, UX, PUB, QA, DATA, OPS, AI; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | SUB_JOB_CD | VARCHAR(10) | NULL 허용; 보조직무코드: 등록 화면 선택 입력(Q-02-3), AI 추천 직무 일치(식-70); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | JBPS_CD | VARCHAR(10) | NULL 허용; 직위코드: MEMBER 팀원 / TEAM 팀장 / GROUP 그룹장 (수당 계산). 등록 화면 입력, 기본 팀원(Q-02-3); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | DRCT_CLS_CD | VARCHAR(10) | NOT NULL; 직간접구분코드: DRCT 직접 / INDRCT 간접(관리직). 등록 화면 입력, 기본 DRCT(Q-02-5). 간접은 판관비; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | PART_ID | VARCHAR(20) | NOT NULL; 영업파트ID: 파트 정산 집계 기준. 필수(화면·업로드). 'HQ' = 본사(직접 영업), 파트 정산 제외. 배정 때 프로젝트 파트와 다르면 경고만(N-02-3); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_PART.PART_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | JNCMP_DT | DATE | NULL 허용; 입사일자; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | RETIRE_DT | DATE | NULL 허용; 퇴사일자: **복사값**: 원본은 TB_HR_DEV_STAT의 RETIRE 줄 BGN_DT. 이 칸을 따로 고치는 화면·업로드는 없고, 퇴사 등록 때 같은 날짜로 넣고 퇴사 취소 때 NULL로 같이 바꿈. 두 값이 다르면 STAT 기준으로 맞춤. 가동률 등 계산이 이력을 뒤지지 않고 바로 읽으려고 둠(2026-10-29). 개발자 상세 [근무 상태 이력]에서 퇴사 등록 시 저장. 지나면 RETIRE·USE_YN=N(다시 사용 불가). 퇴사 취소 시 NULL. 가동률·인력 공급은 이 날짜의 다음 달부터 뺌(2026-10-27 사용자 ; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | WORK_STAT_CD | VARCHAR(10) | NOT NULL; 근무상태코드: INPUT 투입중 / WTHDR 철수예정 / IDLE 대기 / LEAVE 휴직 / RETIRE 퇴사 (배치 갱신, Q-02-1 사용자 결정: 자동); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | HOME_AREA_CD | VARCHAR(10) | NULL 허용; 거주지행정구역코드: → TB_SY_ADM_AREA. 고른 마지막 단계의 코드(동까지 고르면 동, 시·군·구까지면 시·군·구, 시·도만이면 시·도). [수정]은 이 코드로 시·도·시·군·구·동 목록을 다시 채움(HOME_ADDR을 잘라 쓰지 않음). 거주지가 없으면 NULL(02 , 2026-10-31 C9); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_ADM_AREA.AREA_CD | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | HOME_ADDR | VARCHAR(50) | NULL 허용; 거주지: 시·도 + 시·군·구 + 동(예: 서울 강남구 역삼동, 동은 선택, 2026-10-27). 개발자 풀 등록·수정·엑셀 업로드에서 입력(Q-공통-12). HOME_AREA_CD로 만든 보여 주기용 글자: 시·도 줄임 이름 + 시·군·구 + 법정동을 띄어 써서 이음(「경기 성남시 분당구 정자동」, 「서울 광진구」, C9). 근무지 점수는 앞의 시·도로 비교(식-70); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | TELNO | VARCHAR(20) | NULL 허용; 연락처: 등록 화면에서 받지 않음(Q-02-3). 엑셀 이관 값만 보관; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | USE_YN | CHAR(1) | NOT NULL; 사용여부; DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_SKILL | DEV_ID | VARCHAR(20) | PK 구성열; NOT NULL; 개발자ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_SKILL | SKILL_NM | VARCHAR(50) | PK 구성열; NOT NULL; 스킬명: 쉼표로 입력한 값을 행으로 분리. 공통코드 SKILL 권장; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_SKILL | SORT_ORD | NUMERIC(3) | NOT NULL; 정렬순서; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_SKILL | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_SKILL | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_SKILL | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_SKILL | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | DEV_ID | VARCHAR(20) | PK 구성열; NOT NULL; 개발자ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | APLY_BGN_YM | CHAR(6) | PK 구성열; NOT NULL; 적용시작연월: 화면에서 고르지 않고 자동(2026-10-29): 등록 때 첫 줄은 입사월(입사일이 없으면 등록한 달), 수정에서 월 단가·월 급여가 바뀌면 저장한 달로 새 줄. 같은 달에 또 바꾸면 그 달 줄을 고침. 다른 칸(등급 등)만 바꾸면 줄을 만들지 않음; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | APLY_END_YM | CHAR(6) | NULL 허용; 적용종료연월: 새 줄이 생기면 앞 줄을 새 시작월의 전달로 닫음. 지금 줄은 NULL; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | MON_UPRC | NUMERIC(15) | NULL 허용; 월단가: 고객에게 받는 기본 단가(목록 표시용); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | MON_PAY_AMT | NUMERIC(15) | NULL 허용; 월급여지급액: 회사가 주는 돈. 민감 항목; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_PRC | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | DEV_ID | VARCHAR(20) | PK 구성열; NOT NULL; 개발자ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | STAT_SN | NUMERIC(5) | PK 구성열; NOT NULL; 상태일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | WORK_STAT_CD | VARCHAR(10) | NOT NULL; 근무상태코드: 직접 입력하는 상태: LEAVE 휴직 / RETIRE 퇴사(END_DT 없음) / WORK 재직(휴직 복귀, 진행 중인 휴직의 END_DT = 복귀일 전날). 개발자 상세 [근무 상태 이력]에서 등록(N-02-1, Q-28-5, 퇴사·재직 2026-10-27 사용자 결정); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | BGN_DT | DATE | NOT NULL; 시작일자: 휴직=시작일, 재직=복귀일, 퇴사=퇴사일. RETIRE 줄의 BGN_DT가 퇴사일 원본(TB_HR_DEV.RETIRE_DT는 복사값, 2026-10-29); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | END_DT | DATE | NULL 허용; 종료일자: NULL=복직일 미정. [휴직 종료] 시 실제 복직일로 변경; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | STAT_RSN | VARCHAR(300) | NULL 허용; 상태사유: 예: 육아휴직; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | CLOSE_LEAVE_SN | NUMERIC(5) | NULL 허용; 닫은휴직일련번호: WORK·RETIRE 줄만. 이 줄을 등록하면서 자동으로 닫은 휴직 줄의 STAT_SN(같은 DEV_ID). 닫은 휴직이 없으면 NULL. 아래 「휴직 닫기·되돌리기 규칙」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | ORGNL_LEAVE_END_DT | DATE | NULL 허용; 원래휴직종료일자: WORK·RETIRE 줄만. 닫기 직전 그 휴직 줄의 END_DT(NULL이면 복직일 미정이었음). 이 줄을 취소할 때 되돌릴 값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_DEV_STAT | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | PAY_YM | CHAR(6) | PK 구성열; NOT NULL; 급여연월: 같은 연월 재업로드 시 덮어씀, 마감월 불가; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | DEV_ID | VARCHAR(20) | PK 구성열; NOT NULL; 개발자ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | PAY_AMT | NUMERIC(15) | NOT NULL; 급여금액: 프리랜서는 지급액(원천세 차감 전); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | ALWNC_AMT | NUMERIC(15) | NOT NULL; 수당금액: 직위 수당(식-08). 엑셀 칸 아님: 업로드 때 공통코드(ALLOW_TEAM·ALLOW_GROUP)와 그 달 직위로 계산해 저장, 코드를 고치면 마감 안 된 달은 다시 계산(C4); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | INSR_CMPNY_AMT | NUMERIC(15) | NOT NULL; 4대보험회사부담금액: 업로드값 우선, 없으면 INS_RATE; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | INSR_SELF_AMT | NUMERIC(15) | NULL 허용; 4대보험본인부담금액: 급여 업로드 값. 개인 원가·수익표(UI-03)에서는 참고 열(원가 아님). 실지급액(식-17)과 자금수지표 제경비(다음 달, 식-48)에 씀. 비어 있으면 0으로 봄(프리랜서·외주는 원래 없음, 2026-10-30 E4); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | RETIRE_RSV_AMT | NUMERIC(15) | NOT NULL; 퇴직금적립금액: 정규직만(식-09). 엑셀 칸 아님: 업로드 때 (급여 + 수당) × SEV_RATE로 계산해 저장, 코드를 고치면 마감 안 된 달은 다시 계산(C4); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | INCM_TAX_AMT | NUMERIC(15) | NULL 허용; 근로소득세금액: 급여 업로드 값. 실지급액(식-17)과 자금수지표 제경비(다음 달, 식-48)에 씀. 비어 있으면 0으로 봄(2026-10-30 E4); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | WTHLD_TAX_AMT | NUMERIC(15) | NULL 허용; 원천세금액: 3.3%(식-17). 급여 양식에 칸 없음, 업로드 때 시스템이 계산해 저장(2026-10-31); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | REAL_PAY_AMT | NUMERIC(15) | NULL 허용; 실지급금액: 식-17. 급여 양식에 칸 없음, 업로드 때 시스템이 계산해 저장(2026-10-31). 자금수지표 정규직·계약직 지출(2026-10-28 경비정산금액 EXPS_RFND_AMT 칸 삭제); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | VNDR_SPLY_AMT | NUMERIC(15) | NULL 허용; 외주공급가금액: 외주 원가; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | EMP_TYPE_CD | VARCHAR(10) | NOT NULL; 직원구분코드: 업로드 때 TB_HR_DEV.EMP_TYPE_CD를 복사(엑셀 칸 아님, 그 달 기준 보존). 그 달 계산의 직원구분은 이 값: 개인 월 인건비(식-10)·배분 분모(식-13)·파트 월 비용(식-42)·자금수지 인건비 줄(식-48·49)·고정비 비율(식-64). 직원구분을 바꾸면 다음에 올리는 급여부터 새 값, 지난 달 줄은 그대로. 마감 전 같은 달을 다시 올리면 다시 복사(예: 10월까지 정규직, 11월부터 프리랜서 → 9·10월 FT, 11월 FL)(2026-10-31 C17); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | DRCT_CLS_CD | VARCHAR(10) | NOT NULL; 직간접구분코드: 업로드 때 TB_HR_DEV.DRCT_CLS_CD를 복사(엑셀 칸 아님, 그 달 기준 보존). 간접이면 판관비(식-15); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | UPLD_ID | NUMERIC(12) | NULL 허용; 업로드ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_UPLD.UPLD_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_MON_PAY | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | CAND_ID | VARCHAR(20) | PK 구성열; NOT NULL; 후보ID; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | CAND_NM | VARCHAR(50) | NOT NULL; 후보명; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | EMP_TYPE_CD | VARCHAR(10) | NOT NULL; 직원구분코드: FL / OS; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | BLNG_CLNT_ID | VARCHAR(20) | NULL 허용; 소속거래처ID: OS면 필수. 거래처 중 협력사·고객·협력(사용 중)만 고름. FL이면 비움(2026-10-25); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_CLNT.CLNT_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | TELNO | VARCHAR(20) | NULL 허용; 전화번호: 선택, 숫자와 -(2026-10-27). 민감; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | EMAIL_ADDR | VARCHAR(100) | NULL 허용; 이메일주소: 선택, 형식 검사(2026-10-27). 민감; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | PRVC_AGRE_YN | CHAR(1) | NOT NULL; 개인정보동의여부: 기본 N. TELNO·EMAIL_ADDR 중 하나라도 있으면 Y여야 저장(2026-10-27); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | PRVC_AGRE_DT | DATE | NULL 허용; 개인정보동의확인일자: 처음 Y로 저장한 날; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | PRVC_AGRE_USER_ID | VARCHAR(30) | NULL 허용; 개인정보동의확인자ID: 체크한 로그인 계정(TB_SY_USER.USER_ID). 상세에는 이름으로 「동의 확인 10-27 홍길동」(맨 앞 「사용자 ID 칸 표시」, 2026-10-30 C18); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | JOB_CD | VARCHAR(10) | NOT NULL; 직무코드: 주 직무; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | SUB_JOB_CD | VARCHAR(10) | NULL 허용; 보조직무코드: 선택, JOB_CD와 달라야 함. 표시만(2026-10-27); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | GRADE_CD | VARCHAR(10) | NOT NULL; 등급코드: 기본 MD; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | HOME_AREA_CD | VARCHAR(10) | NULL 허용; 거주지행정구역코드: → TB_SY_ADM_AREA. 고른 마지막 단계 코드(시·군·구 또는 시·도). [수정]은 이 코드로 목록을 다시 채움(02 , 2026-10-31 C9); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_ADM_AREA.AREA_CD | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | HOME_ADDR | VARCHAR(50) | NULL 허용; 거주지: 시·도 + 시·군·구(예: 서울 강남구). 등록 창에서 시·도 → 시·군·구 순서로 고름(개발자 풀과 같음, 2026-10-25). HOME_AREA_CD로 만든 보여 주기용 글자(시·도 줄임 이름 + 시·군·구). AI 추천 점수에는 쓰지 않음(C9); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | HOPE_UPRC | NUMERIC(15) | NOT NULL; 희망단가: 프리랜서=본인 지급액, 외주=업체 지급액(부가세 별도). 민감; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | INPUT_PSBL_DT | DATE | NOT NULL; 투입가능일자: 기본 오늘+30일; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | CNTC_STAT_CD | VARCHAR(10) | NOT NULL; 연락상태코드: BEFORE 연락 전 / DONE 연락함 / ITVW 면접 예정 / HOLD 보류 / CTRT 계약 완료; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | LAST_CNTC_DT | DATE | NULL 허용; 최근연락일자: 등록 때 등록일, 연락 기록 저장 때 그날(상태가 그대로여도). 정보 수정은 안 바꿈. 30일 넘게 연락 안 함 = 오늘 − 이 값 > 30일, 연락 상태 보류·계약 완료는 뺌(2026-10-31 C8); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | CAND_SRC_CD | VARCHAR(10) | NOT NULL; 후보출처코드: 공통코드 CAND_SRC: SITE 구인 사이트 / VNDR 협력사 추천 / ACQ 지인 추천 / PAST 과거 투입; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | MEMO_CN | VARCHAR(500) | NULL 허용; 메모: 후보 자체 메모(등록·수정 창, 상세 「메모」). 연락할 때마다 남기는 메모는 TB_HR_CAND_CNTC.CNTC_MEMO(2026-10-29 C15); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_SKILL | CAND_ID | VARCHAR(20) | PK 구성열; NOT NULL; 후보ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_CAND.CAND_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_SKILL | SKILL_NM | VARCHAR(50) | PK 구성열; NOT NULL; 스킬명: 쉼표로 입력한 값을 행으로 분리. 공통코드 SKILL 권장(개발자 보유 스킬과 같음, 2026-10-30 C16); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_SKILL | SORT_ORD | NUMERIC(3) | NOT NULL; 정렬순서: 입력한 순서(1부터). 목록·상세 기술 칩과 수정 창이 이 순서(2026-10-30 C16); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_SKILL | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_SKILL | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_SKILL | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_SKILL | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | CAND_ID | VARCHAR(20) | PK 구성열; NOT NULL; 후보ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_CAND.CAND_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | CNTC_SN | NUMERIC(5) | PK 구성열; NOT NULL; 연락일련번호: 최신이 위; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | CNTC_STAT_CD | VARCHAR(10) | NOT NULL; 연락상태코드: 저장 시 후보 상태도 변경; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | CNTC_MEMO | VARCHAR(200) | NOT NULL; 연락메모: 필수. 등록·정보 수정 때는 시스템이 「등록」·「정보 수정」을 넣음(최근 연락일은 등록 때만 바뀜, 2026-10-31 C8); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | CNTC_DT | DATE | NOT NULL; 연락일자: 오늘; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_HR_CAND_CNTC | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID: P-2601 자동; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | PROJ_NM | VARCHAR(100) | NOT NULL; 프로젝트명; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | CUST_CLNT_ID | VARCHAR(20) | NULL 허용; 고객사거래처ID: CUST·BOTH만. 내부는 NULL; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_CLNT.CLNT_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | CTRT_TYPE_CD | VARCHAR(10) | NOT NULL; 계약유형코드: TK 턴키 / MO 월단위 / DP 파견 / IN 내부 (시스템); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | PROJ_STAT_CD | VARCHAR(10) | NOT NULL; 프로젝트상태코드: ING 진행 / PRE 수주예정 / END 종료 / LOSE 실주. 등록·수주 처리에서 진행/수주예정을 고르고(Q-01-1), 수정 폼에서는 바꾸지 않는다. PRE일 때만 상세 [⋯]에서 ING 또는 LOSE로 바꾸고(ING→PRE 되돌리기 없음), 종료는 계약 종료일이 지나면 자동(2026-10-30 B1, 매일 02:00 배치 ③, 02 PER-004); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | LOSE_DT | DATE | NULL 허용; 실주처리일자: 실주 처리한 날. 화면에서 받지 않고 처리한 날(오늘)로 저장. PROJ_STAT_CD=LOSE일 때만 값(03 ); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | LOSE_RSN_CD | VARCHAR(10) | NULL 허용; 실주사유코드: 공통코드 LOSE_RSN(영업 기회와 같은 목록). PROJ_STAT_CD=LOSE일 때 필수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | LOSE_MEMO | VARCHAR(500) | NULL 허용; 실주메모: 실주 처리 창의 메모(선택); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | CTRT_BGN_DT | DATE | NOT NULL; 계약시작일자; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | CTRT_END_DT | DATE | NOT NULL; 계약종료일자: 시작일 이후; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | WORK_PLC_ADDR | VARCHAR(200) | NULL 허용; 근무지주소: 근무지 주소 전체 텍스트(예: 서울 중구 을지로 66). 내부는 NULL. AI 추천 근무지 점수는 맨 앞 시·도로 거주지와 비교(식-70). 맨 앞 단어는 TB_SY_ADM_AREA의 시·도 정식·줄임 이름으로 맞춰 줄임 이름으로 바꿔 비교(「서울특별시」·「서울시」→「서울」). 근무지는 행정구역 목록·코드를 쓰지 않음(02 , C9). 공통코드 「근무지」 폐지(2026-10-27); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | EST_AMT | NUMERIC(15) | NULL 허용; 견적금액: 부가세 제외. 등록·수정 폼에서 입력, 수정으로 바뀌면 TB_PJ_PROJ_CHG에 ETC(견적 금액) 한 줄(2026-10-29 B9). 참고용이라 계산에 쓰지 않고 상세 화면에도 보이지 않음(2026-10-30 B12); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | ORGNL_CTRT_AMT | NUMERIC(15) | NULL 허용; 원계약금액: 식-56. 등록·수주 처리 때 CTRT_AMT 복사. 수정에서 CTRT_AMT를 바꿀 때 취소 안 된 청구(TB_BL_BILL CNCL_YN='N')가 하나도 없으면 같이 바꾸고(정정), 있으면 그대로(추가 계약)(2026-10-29 B11); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | CTRT_AMT | NUMERIC(15) | NOT NULL; 계약금액: 현재 계약금액(부가세 제외). 내부는 입력 선택, 비우면 0(매출 아님, Q-01-2); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | BDGT_AMT | NUMERIC(15) | NULL 허용; 예산금액: 기본 CTRT_AMT × (1 − 목표 마진율 TGT_MRGN_RT)(식-19); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | BDGT_MANUAL_YN | CHAR(1) | NOT NULL; 예산수기입력여부: Y면 계약금액 바뀌어도 자동 덮어쓰지 않음. 등록·수정 폼에서 예산을 직접 고치거나 상세 예산 [변경]으로 저장하면 Y(2026-10-29 B8). 변경 때 TB_PJ_PROJ_CHG에 BUDGET 한 줄; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | TEAM_EXPS_AMT | NUMERIC(15) | NOT NULL; 팀비금액: 1인당 월(회식비), 기본 TEAM_FEE; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | PROJ_EXPS_AMT | NUMERIC(15) | NOT NULL; 프로젝트경비금액: 1인당 월(물품 구매비), 기본 PEXP_FEE; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | FEATURE_CN | VARCHAR(500) | NULL 허용; 특징내용: AI 추천 스킬 매칭에 사용; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | OPP_ID | VARCHAR(20) | NULL 허용; 영업기회ID: 수주 처리로 생성된 경우; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SL_OPP.OPP_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_PART | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_PART | APLY_BGN_YM | CHAR(6) | PK 구성열; NOT NULL; 적용시작연월: 최초 = 계약 시작월. 변경은 마감월 다음 달부터. 새 변경을 넣으면 마감 안 된 달부터 시작하는 이전 변경 줄(최초 줄 제외)은 지우고 새 줄 1개로 바꿈(2026-10-29 덮어쓰기). 그 달의 파트 = 적용시작연월 ≤ 그 달인 가장 최근 줄; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_PART | PART_ID | VARCHAR(20) | NULL 허용; 영업파트ID: NULL = 미지정, 'HQ' = 본사; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_PART.PART_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_PART | CHG_RSN | VARCHAR(500) | NULL 허용; 변경사유: 선택. 영업 파트 변경 창의 사유. 프로젝트 상세 영업 파트 변경 이력 줄에서 사유가 있는 줄 옆 ⓘ에 마우스를 올리면 「사유: … · 변경자」로 보임(B14); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_PART | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_PART | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_PART | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_PART | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | HIST_SN | NUMERIC(5) | PK 구성열; NOT NULL; 이력일련번호: 최초 등록 시 ‘계약’ 1줄; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | CHG_ITEM_CD | VARCHAR(10) | NOT NULL; 변경항목코드: 공통코드 CHG_ITEM: CTRT 계약(등록·수주 처리 때 자동 첫 줄) / AMT / PERIOD / PART / LOC / BUDGET / TEAM 팀비 / PEXP 프로젝트경비 / ETC; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | CHG_ITEM_NM | VARCHAR(50) | NOT NULL; 변경항목명: 프로젝트명, 계약 종료일 등 실제 칸 이름; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | BFR_VAL | VARCHAR(500) | NULL 허용; 변경전값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | AFT_VAL | VARCHAR(500) | NULL 허용; 변경후값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | CHG_DT | DATE | NOT NULL; 변경일자; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_PROJ_CHG | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_MNPW_PLAN | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_MNPW_PLAN | JOB_CD | VARCHAR(10) | PK 구성열; NOT NULL; 직무코드; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_MNPW_PLAN | PLAN_MM | NUMERIC(6,2) | NOT NULL; 계획MM: 프로젝트 전체 기간의 직무별 계획 MM. 프로젝트 상세 [인원 계획] 탭에서 입력(). 빈 칸으로 저장하면 0(2026-10-29). UI-11의 그 해 몫은 기간 비율로 계산(식-67). UI-11은 계획 0이고 투입도 없는 직무 줄은 보이지 않음; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_MNPW_PLAN | SRC_CD | VARCHAR(10) | NOT NULL; 출처코드: 공통코드 SRC: MANUAL / OPP(수주 시 필요 인력에서 생성); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_MNPW_PLAN | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_MNPW_PLAN | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_MNPW_PLAN | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_MNPW_PLAN | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | STEP_SN | NUMERIC(2) | PK 구성열; NOT NULL; 단계일련번호: 순서; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | PROJ_STEP_CD | VARCHAR(10) | NOT NULL; 프로젝트단계코드: 공통코드 PROJ_STEP; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | STEP_WGHT_RT | NUMERIC(5,2) | NOT NULL; 단계비중: 계약유형별 고정값(식-72); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | PLAN_BGN_DT | DATE | NOT NULL; 계획시작일자: 첫 단계 = 계약 시작일; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | PLAN_END_DT | DATE | NOT NULL; 계획종료일자: 마지막 단계 = 계약 종료일, 겹침 불가; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | CMPL_DT | DATE | NULL 허용; 완료일자: [완료] 클릭 시 오늘. 완료 후 계획일 수정 불가; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_STEP | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | MEMO_SN | NUMERIC(5) | PK 구성열; NOT NULL; 메모일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | WRT_DT | DATE | NOT NULL; 작성일자: 처음 적은 날(수정해도 유지); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | THIS_WEEK_CN | VARCHAR(200) | NULL 허용; 이번주한일: 선택(2026-10-27); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | NEXT_WEEK_CN | VARCHAR(200) | NULL 허용; 다음주할일: 선택(2026-10-27); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | REQ_CN | VARCHAR(200) | NULL 허용; 요청사항; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | ACTL_PRGRS_RT | NUMERIC(3) | NULL 허용; 실적진척률: 0~100 정수, 턴키·내부만; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_WEEK_MEMO | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | ISSUE_ID | NUMERIC(10) | PK 구성열; NOT NULL; 이슈ID; DEFAULT nextval('SQ_TB_PJ_ISSUE') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | PROJ_ID | VARCHAR(20) | NOT NULL; 프로젝트ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | ISSUE_TTL | VARCHAR(100) | NOT NULL; 이슈제목; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | IMPR_CD | VARCHAR(10) | NOT NULL; 중요도코드: HIGH / MID(기본) / LOW; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | DDLN_DT | DATE | NOT NULL; 기한일자: 기본 오늘+7; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | ISSUE_STAT_CD | VARCHAR(10) | NOT NULL; 이슈상태코드: OPEN 열림 / ACT 조치 중 / CLOSE 종료; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | ACTN_CN | VARCHAR(500) | NULL 허용; 조치내용; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ISSUE | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID: 턴키만; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | DSTRB_STEP_CD | VARCHAR(10) | PK 구성열; NOT NULL; 배분단계코드: EST 견적 / CTRT 최종 계약 / PLAN 실제 투입계획 (식-53); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | ROW_SN | NUMERIC(5) | PK 구성열; NOT NULL; 행일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | DSTRB_YM | CHAR(6) | PK 구성열; NOT NULL; 배분연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | ROW_TYPE_CD | VARCHAR(10) | NOT NULL; 행유형코드: DEV 사람 줄 / ITEM 항목 줄(제경비·기술료); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | DEV_ID | VARCHAR(20) | NULL 허용; 개발자ID: 사람 줄. 직무는 저장하지 않음: 화면에서 이 프로젝트의 TB_PJ_ASGN.JOB_CD(배정취소 제외, 여러 개면 모두)를 조회해 보이고, 배정이 없으면 TB_HR_DEV.JOB_CD (2026-10-29); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | ITEM_NM | VARCHAR(50) | NULL 허용; 항목명: 항목 줄; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | DSTRB_AMT | NUMERIC(15) | NOT NULL; 배분금액; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | UPLD_ID | NUMERIC(12) | NULL 허용; 업로드ID: B.6; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_UPLD.UPLD_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_TK_DSTRB | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | EXPS_YM | CHAR(6) | PK 구성열; NOT NULL; 경비연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | EXPS_SN | NUMERIC(5) | PK 구성열; NOT NULL; 경비일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | DRCT_EXPS_ITEM_CD | VARCHAR(10) | NOT NULL; 직접경비항목코드: 공통코드 DRCT_EXPS_ITEM(출장비·장비 임대·회식비·기타, 2026-10-28); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | EXPS_AMT | NUMERIC(15) | NOT NULL; 경비금액: 이 프로젝트에서 쓴 금액(부가세 칸 없음, 2026-10-28); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | RMRK | VARCHAR(500) | NULL 허용; 비고: 화면의 메모; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_DRCT_EXPS | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | ASGN_ID | VARCHAR(20) | PK 구성열; NOT NULL; 투입ID: A-0001; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | PROJ_ID | VARCHAR(20) | NOT NULL; 프로젝트ID: 종료·실주 안 된 프로젝트; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | DEV_ID | VARCHAR(20) | NOT NULL; 개발자ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | JOB_CD | VARCHAR(10) | NOT NULL; 직무코드; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | INPUT_BGN_DT | DATE | NOT NULL; 투입시작일자; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | INPUT_END_DT | DATE | NOT NULL; 투입종료일자: 철수일. 연장·조기철수 시 변경; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | CTRT_MM | NUMERIC(4,2) | NOT NULL; 월MM: 현재 월 MM: 오늘이 속한 달에 적용되는 값(시작 전 투입은 시작 달 값). 변경 기록을 저장할 때와 매달 1일 배치로 다시 맞춘다(2026-10-31 C6). 달별 값은 TB_PJ_ASGN_MON.MON_MM(식-01). 기본 1.00(풀 출근, 0 초과 1.0 이하). 풀로 출근하지 않으면 줄여서 금액을 맞춤(예: 주 3일 0.6). 일·시간 단가는 없음(2026-10-27); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | BILL_UPRC | NUMERIC(15) | NULL 허용; 청구단가: 현재 단가: 오늘이 속한 달에 적용되는 값(시작 전 투입은 시작 달 값). 변경 기록을 저장할 때와 매달 1일 배치로 다시 맞춘다(2026-10-31 C6). 달별 청구는 TB_PJ_ASGN_MON.APLY_UPRC(식-02). 기본값 개발자 월 단가, 대체투입이면 계약상 인력 단가(Q-식-12). 턴키는 NULL(배분표), 내부는 NULL, 무상지원은 0(칸 숨김, 2026-10-26 사용자 결정); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | INPUT_TYPE_CD | VARCHAR(10) | NOT NULL; 투입구분코드: NEW 신규 / ADD 추가투입 / SUB 대체투입 / FREE 무상지원 (MOV 이관은 안 씀, 2026-10-27) (시스템); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | CTRT_DEV_ID | VARCHAR(20) | NULL 허용; 계약상개발자ID: 대체투입일 때 청구서에 쓰는 이름. 배정 창에서는 이 프로젝트에 배정된 적 있는 사람(철수 포함) 중에서 고르고 개발자 ID를 저장, 업로드(B.5)는 관리번호. 계산에는 안 씀(2026-10-30 B17); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | EXT_PSBL_CD | VARCHAR(10) | NULL 허용; 연장가능성코드: H 높음 / M 보통 / L 낮음 / NULL 미정; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | EXT_CNT | NUMERIC(3) | NOT NULL; 연장횟수: 이력에서 정정 제외하고 셈(배치/트리거 갱신); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | EARLY_WTHDR_YN | CHAR(1) | NOT NULL; 조기철수여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | ASGN_STAT_CD | VARCHAR(10) | NOT NULL; 투입상태코드: ACT 유효 / CNCL 배정취소(Q-12-5 사용자 결정: 지우지 않고 이력에 취소로 남김); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | ASGN_ID | VARCHAR(20) | PK 구성열; NOT NULL; 투입ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_ASGN.ASGN_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | HIST_SN | NUMERIC(5) | PK 구성열; NOT NULL; 이력일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | ASGN_CHG_TYPE_CD | VARCHAR(10) | NOT NULL; 투입변경유형코드: 공통코드 ASGN_CHG_TYPE: INPUT 투입 / EXT 연장 / EARLY 조기철수 / UPRC 단가변경 / EXTP 연장가능성변경 / KIND 투입구분변경(배정 전체에 적용, 2026-10-26) / CRCT 정정 / CNCL 취소 / RATE 월MM변경(Q-12-6, 예전 이름 투입률 변경); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | APLY_DT | DATE | NOT NULL; 적용일자: 투입일 이전 불가. UPRC·RATE 줄은 적용 시작월 1일(화면에서 월만 고름, 2026-10-26 사용자 결정); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | BFR_VAL | VARCHAR(200) | NULL 허용; 변경전값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | AFT_VAL | VARCHAR(200) | NULL 허용; 변경후값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | ASGN_CHG_RSN_CD | VARCHAR(10) | NULL 허용; 투입변경사유코드: 공통코드 ASGN_CHG_RSN: CUST 고객 요청 / SCHD 일정 변경 / NEGO 단가 재협상 / HR 인력 사정 / FIX 입력 오류 / ETC. 대체투입 [같이 처리]의 조기 철수도 확인 창에서 이 코드를 고르고 메모(CHG_MEMO) 기본값은 「대체투입(실제 투입자)」(C7); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | CHG_MEMO | VARCHAR(200) | NULL 허용; 변경메모; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | NEW_UPRC | NUMERIC(15) | NULL 허용; 새청구단가: INPUT·UPRC 줄만. 월 단가. 그 달 단가 = 적용 시작월(APLY_DT의 월) ≤ 그 달인 최근 줄 값(식-02, 2026-10-26 사용자 결정); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | NEW_CTRT_MM | NUMERIC(4,2) | NULL 허용; 새월MM: INPUT·RATE 줄만. 그 달 월 MM = 적용 시작월 ≤ 그 달인 최근 줄 값(식-01, 2026-10-26 사용자 결정); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | RTRO_YN | CHAR(1) | NOT NULL; 소급입력여부: 적용일이 오늘 이전; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | CLOSE_CROSS_YN | CHAR(1) | NOT NULL; 마감월걸침여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | CRCT_YN | CHAR(1) | NOT NULL; 정정됨여부: 이 줄이 정정된 경우 Y; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | CRCT_TRGT_SN | NUMERIC(5) | NULL 허용; 정정대상일련번호: CRCT 줄이 가리키는 원래 줄; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_HIST | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | ASGN_ID | VARCHAR(20) | PK 구성열; NOT NULL; 투입ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_ASGN.ASGN_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | INPUT_YM | CHAR(6) | PK 구성열; NOT NULL; 투입연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | DEV_ID | VARCHAR(20) | NOT NULL; 개발자ID: 조회 편의(비정규화); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | PROJ_ID | VARCHAR(20) | NOT NULL; 프로젝트ID: 비정규화; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | INPUT_DAY_CNT | NUMERIC(3) | NOT NULL; 투입일수: 달력일; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | MON_MM | NUMERIC(6,2) | NOT NULL; 월MM: 식-01, 소수 둘째 자리 반올림; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | APLY_UPRC | NUMERIC(15) | NULL 허용; 적용단가: 그 달 적용 청구 단가; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | SALES_AMT | NUMERIC(15) | NOT NULL; 매출금액: 식-02, 무상지원·내부 0; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | FIX_YN | CHAR(1) | NOT NULL; 마감고정여부: 마감되면 Y; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_PJ_ASGN_MON | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | OPP_ID | VARCHAR(20) | PK 구성열; NOT NULL; 영업기회ID; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | OPP_NM | VARCHAR(100) | NOT NULL; 기회명; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | CUST_CLNT_ID | VARCHAR(20) | NOT NULL; 고객사거래처ID: 사용 중인 고객사; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_CLNT.CLNT_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | PART_ID | VARCHAR(20) | NOT NULL; 영업파트ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_PART.PART_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | SALES_STEP_CD | VARCHAR(10) | NOT NULL; 영업단계코드: FIND 발굴 / PROP 제안 / NEGO 협상 / PRE 계약 직전 (시스템); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | WIN_PRBL_RT | NUMERIC(5,2) | NOT NULL; 수주확률: 0~100, 단계 기본값 10/30/60/90; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | PRBL_MANUAL_YN | CHAR(1) | NOT NULL; 확률수기여부: Y면 단계 이동해도 유지; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | EXPCT_AMT | NUMERIC(15) | NOT NULL; 예상금액: 부가세 제외, 0 초과; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | BGN_PLAN_YM | CHAR(6) | NOT NULL; 시작예정연월; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | CTRT_TYPE_CD | VARCHAR(10) | NULL 허용; 계약유형코드: 공통코드 CTRT_TYPE 중 TK 턴키 / MO 월단위 / DP 파견(IN 내부 제외). 수주 처리 창 기본값, AI 인력 추천 파견 자리 판정(식-69); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | EXPCT_PRD_MCNT | NUMERIC(3) | NULL 허용; 예상기간개월수: 1 이상. 비우면 6개월로 봄. 매출 예측(식-92), 수주 처리 창 종료일 기본값; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | NEXT_TODO_CN | VARCHAR(200) | NULL 허용; 다음할일; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | NEXT_TODO_DDLN_DT | DATE | NULL 허용; 다음할일기한: 지나면 빨강; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | WORK_PLC_ADDR | VARCHAR(200) | NULL 허용; 근무지주소: 주소 전체(프로젝트 근무지와 같이 직접 입력). AI 인력 추천 근무지 점수(식-70, 맨 앞 시·도 비교는 프로젝트 근무지와 같음, 02 ), 수주 처리 때 TB_PJ_PROJ.WORK_PLC_ADDR로 넘김(2026-10-29 C12); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | RMRK | VARCHAR(500) | NULL 허용; 비고: AI 인력 추천 대상이 이 기회면 특징으로 씀(식-70); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | OPP_STAT_CD | VARCHAR(10) | NOT NULL; 기회상태코드: ING 진행 / WIN 수주 / LOSE 실주 / STOP 사용 안 함(Q-공통-21·Q-29-2, [다시 사용]이면 ING로 돌아감); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | EXPCT_PRFT_RT | NUMERIC(7,2) | NULL 허용; 예상이익률: 저장 시 계산(식-84); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | APRV_STAT_CD | VARCHAR(10) | NOT NULL; 승인상태코드: NONE 승인 불필요 / WAIT 승인 대기 / OK 승인 / REJ 반려; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | APRV_PRFT_RT | NUMERIC(7,2) | NULL 허용; 승인시이익률: 재저장 비교(±0.5%p); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | RSLT_DT | DATE | NULL 허용; 결과일자: 수주·실주일, 사용 안 함(STOP) 처리일. [다시 사용]으로 ING가 되면 NULL(2026-10-30 C6); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | LOSE_RSN_CD | VARCHAR(10) | NULL 허용; 실주사유코드: PRICE / TECH / SCHD / CNCL / ETC; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | LOSE_MEMO | VARCHAR(500) | NULL 허용; 실주메모; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | PROJ_ID | VARCHAR(20) | NULL 허용; 프로젝트ID: 수주 시 생성된 프로젝트; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | OPP_ID | VARCHAR(20) | PK 구성열; NOT NULL; 영업기회ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SL_OPP.OPP_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | NEED_SN | NUMERIC(3) | PK 구성열; NOT NULL; 필요일련번호: 1줄 이상; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | JOB_CD | VARCHAR(10) | NOT NULL; 직무코드; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | GRADE_CD | VARCHAR(10) | NULL 허용; 등급코드; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | NEED_CNT | NUMERIC(3) | NOT NULL; 필요인원: 1 이상 정수; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | BGN_YM | CHAR(6) | NOT NULL; 시작연월; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | PRD_MCNT | NUMERIC(3) | NOT NULL; 기간개월수: 1 이상; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | MON_MM | NUMERIC(4,2) | NOT NULL; 월MM: 0 초과 1.0 이하; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | EXPCT_UPRC | NUMERIC(15) | NULL 허용; 예상단가: 비우면 직무 기준 단가(Q-식-80); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_OPP_NEED | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | TENT_ID | NUMERIC(10) | PK 구성열; NOT NULL; 가배정ID; DEFAULT nextval('SQ_TB_SL_TENT_ASGN') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | OPP_ID | VARCHAR(20) | NOT NULL; 영업기회ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SL_OPP_NEED.OPP_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | NEED_SN | NUMERIC(3) | NOT NULL; 필요일련번호: 가배정 자리(그 기회의 필요 인력 줄). UI-30은 조건 「가배정 자리」(같은 직무 줄 자동, 없으면 첫 줄), UI-05는 누른 추천 카드의 줄, UI-29 수정 화면은 그 표의 줄(2026-10-29); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SL_OPP_NEED.NEED_SN | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | DEV_ID | VARCHAR(20) | NOT NULL; 개발자ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | BGN_YM | CHAR(6) | NOT NULL; 시작연월: max(철수 다음 달, 자리 시작월); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | TENT_STAT_CD | VARCHAR(10) | NOT NULL; 가배정상태코드: ACT 가배정 / REL 해제 / CNVT 배정 후보로 넘김 / DONE 배정 확정(UI-12 [확정] 후 투입 저장) / DROP 후보에서 뺌(UI-12 [빼기]). UI-12 「배정 후보」 목록은 CNVT만 보임(2026-10-28). 한 사람은 ACT 줄이 하나만: 다른 기회에 새로 가배정하면(UI-05·UI-29·UI-30) 이전 ACT 줄은 REL(2026-10-29); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | SRC_SCRN_ID | VARCHAR(10) | NOT NULL; 출처화면ID: UI-05 / UI-29 / UI-30; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | PROC_DTTM | TIMESTAMP | NULL 허용; 처리일시: ACT에서 상태가 바뀐 때: 해제(실주·삭제·바꾸기)·배정 후보로 넘김(수주 처리)·확정·빼기. 수정일시(MOD_DTTM)는 시작월만 고쳐도 바뀌어서 따로 둠(2026-10-28, 예전 REL_DTTM 해제일시); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_TENT_ASGN | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_APRV_HIST | OPP_ID | VARCHAR(20) | PK 구성열; NOT NULL; 영업기회ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SL_OPP.OPP_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_APRV_HIST | HIST_SN | NUMERIC(5) | PK 구성열; NOT NULL; 이력일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_APRV_HIST | APRV_ACT_CD | VARCHAR(10) | NOT NULL; 승인작업코드: REQ 요청 / OK 승인 / REJ 반려 / AUTO_REL 요구 해제; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_APRV_HIST | PRFT_RT | NUMERIC(7,2) | NOT NULL; 이익률: 그 시점 예상 이익률; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_APRV_HIST | ACT_RSN | VARCHAR(300) | NULL 허용; 작업사유: 반려 사유. 반려 확인 창의 「반려 사유」(선택)에서 입력, 기회 상세 반려 띠·담당자 알림에 표시(2026-10-29 C9); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_APRV_HIST | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_APRV_HIST | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | RCMD_ID | NUMERIC(12) | PK 구성열; NOT NULL; 추천ID: 추천 1회; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | RANK_ORD | NUMERIC(3) | PK 구성열; NOT NULL; 순위: 1~5(Q-30-1); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | RCMD_TYPE_CD | VARCHAR(10) | NOT NULL; 추천유형코드: AI = AI 인력 추천(UI-30) / NEXT = 다음 자리(UI-05); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | OPP_ID | VARCHAR(20) | NULL 허용; 영업기회ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SL_OPP.OPP_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | NEED_SN | NUMERIC(3) | NULL 허용; 필요일련번호; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | PROJ_ID | VARCHAR(20) | NULL 허용; 프로젝트ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | JOB_CD | VARCHAR(10) | NOT NULL; 직무코드; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | BGN_YM | CHAR(6) | NOT NULL; 시작연월; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | REQ_GRADE_CD | VARCHAR(10) | NOT NULL; 요청등급코드: 등급 점수를 매길 때 비교한 기준 등급(공통코드 GRADE: JR 초급 / MD 중급 / SR 고급 / EX 특급). AI = 추천 조건에서 고른 등급(필수, 처음 값 SR). NEXT = 자리(TB_SL_OPP_NEED)의 필요 등급, 비어 있으면 MD(중급으로 비교, 식-80). 후보 개발자 등급이 비어 있어도 이 값은 있음(2026-10-30 C11); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | DEV_ID | VARCHAR(20) | NOT NULL; 개발자ID: 후보; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | TOT_SCORE | NUMERIC(5,1) | NOT NULL; 총점: 100점 만점; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | SCORE_DTL | VARCHAR(500) | NOT NULL; 항목별점수: JSON(직무·스킬·고객/업종·등급·성과·비용·가용일·근무지); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | RCMD_RSN | VARCHAR(300) | NULL 허용; 추천이유; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | USER_ACT_CD | VARCHAR(10) | NULL 허용; 사용자작업코드: ASGN 배정 / TENT 가배정 / EXCL 제외; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | RCMD_EXCL_CD | VARCHAR(10) | NULL 허용; 추천제외사유코드: 공통코드 RCMD_EXCL. 제외(EXCL)일 때 필수(Q-30-3). 추천할 때 같은 대상(OPP_ID 또는 PROJ_ID) + JOB_CD에 EXCL 기록이 있는 DEV_ID는 그 대상이 닫힐 때까지 후보에서 뺀다(2026-10-24 사용자 결정, 컬럼 추가 없음); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | ACT_USER_ID | VARCHAR(30) | NULL 허용; 작업사용자ID: USER_ACT_CD(배정·가배정·제외)를 누른 사용자. 로그 테이블이라 MOD_* 가 없어 따로 둠(2026-10-29 C14); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | ACT_DTTM | TIMESTAMP | NULL 허용; 작업일시: USER_ACT_CD를 넣은 시각(2026-10-29 C14); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SL_RCMD_LOG | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | BILL_ID | VARCHAR(20) | PK 구성열; NOT NULL; 청구번호: B-202610-001. B-청구월(YYYYMM)-그 달 안 순번 3자리(001부터). 취소된 번호는 다시 쓰지 않음(01 2.7, 2026-10-30 D12); NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | PROJ_ID | VARCHAR(20) | NOT NULL; 프로젝트ID: 내부·수주예정 제외; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_PJ_PROJ.PROJ_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | BILL_ORD | NUMERIC(3) | NOT NULL; 청구차수: 그 프로젝트의 모든 청구(취소 포함) 중 가장 큰 차수 + 1. 등록할 때 저장하고 바꾸지 않음. 취소된 차수는 다시 쓰지 않음(청구월 순서와 다를 수 있음, 03 , 2026-10-31 C10); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | BILL_YM | CHAR(6) | NOT NULL; 청구연월: 귀속월. 기본 이번 달; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | BILL_DT | DATE | NOT NULL; 청구일자: 턴키 매출 인식 기준(식-04); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | SPLY_AMT | NUMERIC(15) | NOT NULL; 공급가액: 추정값 자동 채움 후 수정 가능; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | VAT_RT | NUMERIC(5,2) | NOT NULL; 부가세율: % 입력, 기본 10(운영 기준값 VAT_RT), 면세·영세율 0(Q-13-4); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | VAT_AMT | NUMERIC(15) | NOT NULL; 부가세금액: 공급가 × 부가세율 원 단위 버림(직접 입력 없음); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | TOT_AMT | NUMERIC(15) | NOT NULL; 합계금액: 공급가 + 부가세; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | RCPT_SUM_AMT | NUMERIC(15) | NOT NULL; 입금합계금액: 입금 등록 시 갱신; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | UNPAID_AMT | NUMERIC(15) | NOT NULL; 미수금액: 합계 − 입금합계; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | PAY_DUE_DT | DATE | NOT NULL; 입금예정일자: 청구 창에서 입력(필수). 기본 청구월 다음 달 25일(Q-식-10); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | TAX_INVC_NO | VARCHAR(30) | NULL 허용; 세금계산서번호: 청구 창에서 입력. 비워도 저장, 비면 “번호 미입력” 경고와 월 마감 점검 누락(Q-13-2); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | CNCL_YN | CHAR(1) | NOT NULL; 취소여부: Y=취소됨. 잘못 등록한 청구를 지우지 않고 남김. 청구월이 마감 전이고 정상 입금이 없을 때만 취소. 취소된 청구는 턴키 매출·미수금·청구 누락 점검·미수 알림에서 뺌(사용자 결정 2026-10-26). 차수(BILL_ORD)는 비워 두고 다시 쓰지 않음(2026-10-31 C10); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | CNCL_RSN | VARCHAR(500) | NULL 허용; 취소사유: 취소할 때 필수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | CNCL_USER_ID | VARCHAR(30) | NULL 허용; 취소자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | CNCL_DTTM | TIMESTAMP | NULL 허용; 취소일시; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_BILL | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | RCPT_ID | NUMERIC(12) | PK 구성열; NOT NULL; 입금ID; DEFAULT nextval('SQ_TB_BL_RCPT') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | BILL_ID | VARCHAR(20) | NOT NULL; 청구번호; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BL_BILL.BILL_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | RCPT_DT | DATE | NOT NULL; 입금일자: 자금수지 실적 기준; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | RCPT_AMT | NUMERIC(15) | NOT NULL; 입금금액: 0 초과, 미수금 이하; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | CNCL_YN | CHAR(1) | NOT NULL; 취소여부: Y=취소됨. 지우지 않고 남김, 입금합계에서 뺌(Q-13-5). RCPT_DT가 속한 달이 마감되면 취소 불가(청구월 무관, 2026-10-25); DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | CNCL_RSN | VARCHAR(500) | NULL 허용; 취소사유: 취소할 때 필수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | CNCL_USER_ID | VARCHAR(30) | NULL 허용; 취소자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | CNCL_DTTM | TIMESTAMP | NULL 허용; 취소일시; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_RCPT | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | BILL_ID | VARCHAR(20) | PK 구성열; NOT NULL; 청구번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BL_BILL.BILL_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | DUN_SN | NUMERIC(5) | PK 구성열; NOT NULL; 알림일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | DUN_STEP | NUMERIC(1) | NOT NULL; 알림단계: 1 / 2 / 3 (7·30·60일); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | SEND_DT | DATE | NOT NULL; 발송일자; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | RCVR_DESC | VARCHAR(300) | NOT NULL; 수신자설명: 받은 사람 목록; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | SEND_MTHD_CD | VARCHAR(10) | NOT NULL; 발송방법코드: BOTH(메일+시스템); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | RESEND_YN | CHAR(1) | NOT NULL; 다시알림여부: [다시 알림]; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_BL_DUN_HIST | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | COST_YM | CHAR(6) | PK 구성열; NOT NULL; 원가연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | DEV_ID | VARCHAR(20) | PK 구성열; NOT NULL; 개발자ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | PROJ_ID | VARCHAR(20) | PK 구성열; NOT NULL; 프로젝트ID: 대기 몫은 'IDLE', 간접 인력 줄은 'INDRCT'(2026-10-28); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | ASGN_ID | VARCHAR(20) | PK 구성열; NOT NULL; 투입ID: TB_PJ_ASGN.ASGN_ID. **한 줄 = 그 달 투입 1건**(2026-10-28). 대기 줄은 'IDLE', 간접 인력 줄은 'INDRCT'(정해진 값이라 FK 없음, 01 규칙). 같은 사람이 같은 프로젝트에 투입 2건(예: 유상 0.5 + 무상지원 0.5, 직무가 다른 추가투입)이면 2줄로 저장; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | ASGN_MM | NUMERIC(6,2) | NOT NULL; 투입MM: 이 투입의 그 달 MM(= TB_PJ_ASGN_MON.MON_MM). 대기 줄은 1.0 − 그 달 MM 합, 간접 줄은 1.0; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | FREE_MM | NUMERIC(6,2) | NOT NULL; 무상지원MM: 이 투입이 무상지원(TB_PJ_ASGN.INPUT_TYPE_CD=FREE)이면 ASGN_MM과 같고, 아니면 0. 무상지원 원가(식-27) = FREE_MM > 0인 줄의 LABOR_ALLOC_AMT 합; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | SALES_AMT | NUMERIC(15) | NOT NULL; 매출금액: 개인 단가(턴키는 배분 금액); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | LABOR_ALLOC_AMT | NUMERIC(15) | NOT NULL; 인건비배분금액: 식-13 / 대기는 식-14; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | TEAM_EXPS_AMT | NUMERIC(15) | NOT NULL; 팀비금액: 식-11. 마감 때 TB_PJ_PROJ.TEAM_EXPS_AMT(그때 값) × 이 줄 ASGN_MM, 원 단위 버림. 외주·대기('IDLE')·간접 줄은 0. 프로젝트 팀비를 나중에 바꿔도 이 값은 그대로(2026-10-28); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | PROJ_EXPS_AMT | NUMERIC(15) | NOT NULL; 프로젝트경비금액: 식-11. 마감 때 TB_PJ_PROJ.PROJ_EXPS_AMT(그때 값) × 이 줄 ASGN_MM, 원 단위 버림. 외주·대기·간접 줄은 0(2026-10-28); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | DRCT_EXPS_AMT | NUMERIC(15) | NOT NULL; 직접경비금액: 식-11. = TEAM_EXPS_AMT + PROJ_EXPS_AMT(두 칸 합, 마감 때 같이 저장). 합계만 쓰는 화면(UI-04 누적 원가·UI-08 등)은 이 칸 합에 그 달 TB_PJ_DRCT_EXPS.EXPS_AMT 합(추가 직접 경비)을 더한다(2026-10-31); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | COST_CLS_CD | VARCHAR(10) | NOT NULL; 원가구분코드: ALLOC 매출원가 / IDLE 대기원가 / RND 연구개발비 / INDRCT 간접인건비 / PRE 선투입; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | CTRT_TYPE_CD | VARCHAR(10) | NULL 허용; 계약유형코드: 유형별 기여도 집계용; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | PART_ID | VARCHAR(20) | NULL 허용; 영업파트ID: 그 달 프로젝트 파트; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | FIX_YN | CHAR(1) | NOT NULL; 마감고정여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_COST | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | PL_YM | CHAR(6) | PK 구성열; NOT NULL; 손익연월; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | SALES_AMT | NUMERIC(15) | NOT NULL; 매출액; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | ALLOC_COST_AMT | NUMERIC(15) | NULL 허용; 배분원가: 내부 제외. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | IDLE_COST_AMT | NUMERIC(15) | NULL 허용; 대기원가: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | DRCT_EXPS_AMT | NUMERIC(15) | NULL 허용; 직접경비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | SALES_COST_AMT | NUMERIC(15) | NOT NULL; 매출원가: 식-28; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | GROSS_PRFT_AMT | NUMERIC(15) | NULL 허용; 매출총이익: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | SGA_INPUT_AMT | NUMERIC(15) | NULL 허용; 판관비입력: UI-10. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | INDRCT_LABOR_AMT | NUMERIC(15) | NULL 허용; 간접인건비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | RND_AMT | NUMERIC(15) | NULL 허용; 연구개발비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | SGA_TOT_AMT | NUMERIC(15) | NOT NULL; 판관비합계: 식-29; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | PREV_ADJ_AMT | NUMERIC(15) | NULL 허용; 전월조정: 식-59. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | OPRT_PRFT_AMT | NUMERIC(15) | NOT NULL; 영업이익: 식-30; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | NOPR_INCM_AMT | NUMERIC(15) | NULL 허용; 영업외수익: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | NOPR_EXPS_AMT | NUMERIC(15) | NULL 허용; 영업외비용: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | CORP_TAX_AMT | NUMERIC(15) | NULL 허용; 법인세월할: 식-31, 마감 시 고정. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | NET_PRFT_AMT | NUMERIC(15) | NULL 허용; 당기순이익: 식-32. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | MIGR_YN | CHAR(1) | NOT NULL; 이관여부: 2025년(시작한 달 전) B.11 = Y(세부 구성 일부만). B.11 열(2026-10-29 사용자 결정, 테이블 검증 2장 E11): 필수 = 연월·매출·매출원가·판관비 합계·영업이익, 있으면 넣음 = 배분원가·대기원가·직접경비·매출총이익·판관비 입력·간접인건비·연구개발비·전월조정·영업외수익·영업외비용·법인세·당기순이익. Y인 달은 엑셀 값을 그대로 저장하고 다시 계산하지 않음. 빈칸은 0이 아니라 NULL(화면 「–」). N인 달은 모든 금액 칸에 값이 있음; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | FIX_YN | CHAR(1) | NOT NULL; 마감고정여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_MON_PL | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | ADJ_ID | NUMERIC(10) | PK 구성열; NOT NULL; 조정ID; DEFAULT nextval('SQ_TB_CO_PREV_ADJ') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | APLY_YM | CHAR(6) | NOT NULL; 반영연월: 조정을 넣는 이번 달; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | TRGT_YM | CHAR(6) | NOT NULL; 대상연월: 원래 마감된 달; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | ADJ_ITEM_CD | VARCHAR(10) | NOT NULL; 조정항목코드: SALES 매출 / COST 원가 / SGA 판관비 / ETC 기타 (UI-10 전월 조정 표, 35번 1); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | ADJ_AMT | NUMERIC(15) | NOT NULL; 조정금액: 영업이익에 더하는 값(이익 늘면 +, 줄면 −, Q-식-55 결정 2026-10-21); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | ADJ_RSN | VARCHAR(300) | NOT NULL; 조정사유: UI-10 전월 조정 표의 사유(필수, ); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PREV_ADJ | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | PART_ID | VARCHAR(20) | PK 구성열; NOT NULL; 영업파트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_BS_PART.PART_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | SETL_YM | CHAR(6) | PK 구성열; NOT NULL; 정산연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | CORP_CARD_AMT | NUMERIC(15) | NOT NULL; 법인카드사용금액: 기본 0; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | PART_PRFT_AMT | NUMERIC(15) | NULL 허용; 파트이익금: 확정 시 고정(식-44); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | SETL_STAT_CD | VARCHAR(10) | NOT NULL; 정산상태코드: WORK 작업중 / CNFM 확정 / AUTO 마감 시 자동 확정(확정자 = 마감한 사람, 2026-10-25); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | CNFM_USER_ID | VARCHAR(30) | NULL 허용; 확정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | CNFM_DTTM | TIMESTAMP | NULL 허용; 확정일시; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | CNCL_RSN | VARCHAR(500) | NULL 허용; 확정취소사유: 마지막 취소 사유; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | PART_ID | VARCHAR(20) | PK 구성열; NOT NULL; 영업파트ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_CO_PART_SETL.PART_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | SETL_YM | CHAR(6) | PK 구성열; NOT NULL; 정산연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_CO_PART_SETL.SETL_YM | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | DEV_ID | VARCHAR(20) | PK 구성열; NOT NULL; 개발자ID; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_HR_DEV.DEV_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | EMP_TYPE_CD | VARCHAR(10) | NOT NULL; 직원구분코드: 확정 때(직접 확정·월 마감 자동 확정) 그 달 직원구분을 저장: TB_HR_MON_PAY.EMP_TYPE_CD, 그 달 급여가 없으면 TB_HR_DEV.EMP_TYPE_CD. 확정된 달 화면 「구분」 칸은 이 값(나중에 개발자 풀 직원구분을 바꿔도 그대로)(2026-10-31 C17); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | PROJ_NM_LIST | VARCHAR(1000) | NOT NULL; 프로젝트명목록: 확정 때(직접 확정·월 마감 자동 확정) 그 사람이 그 달 투입한 프로젝트 이름을 모두 「, 」로 이어 저장(같은 프로젝트는 한 번, 그 달 MM 큰 순, 같으면 이름순). 예: 가온은행 차세대, 누리카드 정산. 프로젝트명은 확정 때 이름 그대로 남음. 화면은 첫 이름 + 「외 n건」, 마우스를 올리면 전부. 작업중(확정 전·확정 취소 뒤)에는 그 달 투입에서 구해 보여 줌. 줄은 그 달 MM > 0인 사람만 생기므로 항상 1건 이상(2026-10-30 D15); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | MM | NUMERIC(6,2) | NOT NULL; MM; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | UPRC_AMT | NUMERIC(15) | NOT NULL; 월단가금액: 식-41; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | PAY_AMT | NUMERIC(15) | NOT NULL; 급여금액: 시작 달 일할; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | SALES_EXPS_AMT | NUMERIC(15) | NOT NULL; 공통관리비금액: 외주 0; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | RETIRE_AMT | NUMERIC(15) | NOT NULL; 퇴직금금액: 정규직만; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | COST_AMT | NUMERIC(15) | NOT NULL; 비용계: 식-42; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | DIFF_AMT | NUMERIC(15) | NOT NULL; 차액: 월 단가 − 비용 계(식-43, 적자면 음수). 화면 열 이름은 「이익」(2026-10-29), 요약은 「이익 합계」(2026-10-31 C14); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | PRFT_AMT | NUMERIC(15) | NOT NULL; 이익금: 식-43, 음수 가능; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | RMRK | VARCHAR(100) | NULL 허용; 비고: 해당하는 것을 「 · 」로 이어 씀: 공통관리비(계약직·프리랜서) 또는 공통관리비+퇴직금(정규직), 외주는 둘 다 없음 / 시작 달 n/말일일(시작 달 일할) / ⚠ 월 급여 없음(시작 달 급여 0)(시작 달인데 개발자 풀 월 급여·지급액이 비어 있음, 화면 빨간 글씨, 막지 않음, N-02-4) / 무상지원. 하나도 없으면 비움(화면 「–」). 예: 「공통관리비+퇴직금 · 시작 달 12/31일 · ⚠ 월 급여 없음(시작 달 급여 0)」(04 , 2026-10-31 C14); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CO_PART_SETL_DTL | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | FIN_YM | CHAR(6) | PK 구성열; NOT NULL; 재무연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | ITEM_SN | NUMERIC(5) | PK 구성열; NOT NULL; 항목일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | FIN_CLS_CD | VARCHAR(10) | NOT NULL; 재무구분코드: SGA 판관비 / NOPR_IN 영업외수익 / NOPR_OUT 영업외비용 (시스템); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | SGA_ITEM_CD | VARCHAR(10) | NULL 허용; 판관비항목코드: 공통코드 SGA_ITEM. 직접 쓴 항목은 비움(Q-10-1); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | ITEM_NM | VARCHAR(50) | NOT NULL; 세부항목명: 필수; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | AMT | NUMERIC(15) | NOT NULL; 금액: 부가세 제외, 필수; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | VAT_AMT | NUMERIC(15) | NOT NULL; 부가세금액: 화면·엑셀(B.2)에서 선택 입력, 비우면 0으로 저장. 자금수지 지출에만 더함(금액 + 부가세)(2026-10-31 C13); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | IO_DT | DATE | NULL 허용; 입출금일자: 판관비·영업외비용은 자금수지 제경비 달(없으면 그 달), 영업외수익은 자금수지 잡수익 예정일(2026-10-28 지급 관리 삭제); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | RPT_YN | CHAR(1) | NOT NULL; 매달반복여부; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | AUTO_FILL_YN | CHAR(1) | NOT NULL; 자동채움여부: “자동” 표시; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | RMRK | VARCHAR(500) | NULL 허용; 비고; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | UPLD_ID | NUMERIC(12) | NULL 허용; 업로드ID: 엑셀(B.2)로 올린 줄이면 그 업로드 ID, 직접 입력·자동 채움 줄은 NULL. 같은 달 재업로드 때 이 값이 있는 줄만 지우고 새로 넣는다(2026-10-29 사용자 결정, 테이블 검증 2장 E9); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_UPLD.UPLD_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_ITEM | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CORP_TAX | TAX_YR | CHAR(4) | PK 구성열; NOT NULL; 과세연도; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CORP_TAX | EST_TAX_AMT | NUMERIC(15) | NOT NULL; 연간예상법인세: 바꾸면 마감 안 된 달부터 재분배. 새 해 줄은 그 해 재무 항목을 처음 열 때 작년 값으로 만들어짐(없으면 0), 재무가 고침(2026-10-29); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CORP_TAX | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CORP_TAX | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CORP_TAX | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CORP_TAX | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | TAX_YR | CHAR(4) | PK 구성열; NOT NULL; 과세연도; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | PAY_TYPE_CD | VARCHAR(10) | PK 구성열; NOT NULL; 납부유형코드: CNFM 확정 신고(다음 해 3월) / MID 중간예납(8월); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | PAY_DUE_DT | DATE | NOT NULL; 납부예정일자; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | PAY_AMT | NUMERIC(15) | NULL 허용; 납부금액: 금액 원본. MID: 재무가 세무사 안내 금액을 입력(비어 있으면 0으로 봄). CNFM: NULL이면 자동 = TB_FI_CORP_TAX.EST_TAX_AMT − 같은 과세연도 MID의 PAY_AMT(식-50)로 보여 주고, 사람이 고치면 그 값을 저장(납부 확인 전에 자동 값과 같게 넣으면 NULL로 둠). 납부 확인 때 실제 낸 금액을 숫자로 저장(자동 값과 같아도 NULL로 두지 않음)하고, 그 뒤로는 연간 예상액을 고쳐도 바뀌지 않으며 PAY_YN=Y 줄은 고칠 수 없음(2026-10-30 B2); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | PAY_DT | DATE | NULL 허용; 납부일자: 납부 확인 때 넣는 실제 낸 날(기본 PAY_DUE_DT). 자금수지 「법인세 납부」는 PAY_YN=Y면 이 날의 달, N이면 PAY_DUE_DT의 달에 잡힘(2026-10-29); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | PAY_YN | CHAR(1) | NOT NULL; 납부여부: N 예정 / Y 납부 확인. 날짜가 지나도 자동으로 Y가 되지 않고 [납부 확인]으로만 Y(2026-10-29). 화면 상태: Y → 납부, N이고 PAY_DUE_DT < 오늘 → 확인 필요(대시보드 경고), 그 밖 → 예정; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_TAX_PAY | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | LOAN_SN | NUMERIC(10) | PK 구성열; NOT NULL; 대여금일련번호; DEFAULT nextval('SQ_TB_FI_LOAN') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | LOAN_DT | DATE | NOT NULL; 입출금일자: 이 날짜의 달 자금수지에 잡힘; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | IO_CD | VARCHAR(10) | NOT NULL; 입출금구분코드: IN 받음 / OUT 갚음; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | AMT | NUMERIC(15) | NOT NULL; 금액: 양수로 저장, 갚음은 자금수지에서 −; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | CNTPRT_NM | VARCHAR(100) | NULL 허용; 상대명: 대표이사·은행 등; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | RMRK | VARCHAR(200) | NULL 허용; 비고; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_LOAN | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD | CARD_ID | VARCHAR(20) | PK 구성열; NOT NULL; 카드ID: K-01 형식; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD | CARD_NM | VARCHAR(100) | NOT NULL; 카드명: 예: 영업 1팀 카드; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD | CARD_NO_L4 | CHAR(4) | NOT NULL; 카드번호끝4자리: 전체 카드번호는 저장하지 않음; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD | USE_YN | CHAR(1) | NOT NULL; 사용여부: 화면 [삭제]는 N으로 바꿈(물리 삭제 안 함, D-4). N이어도 결제 금액이 있는 달에는 목록에 보임. [되살리기]는 Y; DEFAULT 'Y' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_MON | PAY_YM | CHAR(6) | PK 구성열; NOT NULL; 결제연월: 통장에서 카드 대금이 나간 달; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_MON | CARD_SUM_AMT | NUMERIC(15) | NOT NULL; 카드별합계금액: TB_FI_CARD_PAY 합계(저장 때 계산); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_MON | CASH_AMT | NUMERIC(15) | NOT NULL; 자금수지반영금액: 기본은 카드별 합계. 카드사 청구서와 다르면 재무가 직접 고침; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_MON | EDIT_YN | CHAR(1) | NOT NULL; 직접수정여부: Y면 화면에 카드별 합계와의 차액 표시, [자동 합계로]를 누르면 N; DEFAULT 'N' NOT NULL; 비PK | Y/N | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_MON | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_MON | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_MON | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_MON | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_PAY | PAY_YM | CHAR(6) | PK 구성열; NOT NULL; 결제연월: → TB_FI_CARD_MON; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_FI_CARD_MON.PAY_YM | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_PAY | CARD_ID | VARCHAR(20) | PK 구성열; NOT NULL; 카드ID: → TB_FI_CARD; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_FI_CARD.CARD_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_PAY | PAY_AMT | NUMERIC(15) | NOT NULL; 결제금액: 그 달 카드 대금(부가세 포함). 빈칸은 0; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_PAY | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_PAY | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_PAY | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_CARD_PAY | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | XFER_SN | NUMERIC(10) | PK 구성열; NOT NULL; 이체일련번호; DEFAULT nextval('SQ_TB_FI_XFER') NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | XFER_DT | DATE | NOT NULL; 이체일자: 이 날짜의 달 자금수지에 잡힘; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | XFER_CLS_CD | VARCHAR(10) | NOT NULL; 이체구분코드: 공통코드 XFER_CLS: EXP 계좌이체(자금수지 제경비) / SPCL 특별비(자금수지 미지급 인건비). 화면 기본 선택은 EXP; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | ITEM_NM | VARCHAR(100) | NOT NULL; 내용: 예: 장비 임대, 퇴직금(홍길동), 퇴직연금 11월 납입; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | RCVR_NM | VARCHAR(100) | NULL 허용; 받는곳명: 업체 이름(특별비는 받는 사람); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | AMT | NUMERIC(15) | NOT NULL; 금액: 실제 나간 금액(부가세 포함); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | RMRK | VARCHAR(200) | NULL 허용; 비고; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_XFER | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | PLAN_YM | CHAR(6) | PK 구성열; NOT NULL; 계획연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | PART_ID | VARCHAR(20) | PK 구성열; NOT NULL; 영업파트ID: 'ALL' 고정. 파트별 목표는 넣지 않음(Q-18-2 결정); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | SALES_TGT_AMT | NUMERIC(15) | NOT NULL; 매출목표금액: 업로드 빈칸은 0(목표 없음, Q-18-3); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | OPRT_PRFT_TGT_AMT | NUMERIC(15) | NOT NULL; 영업이익목표금액: 업로드 빈칸은 0; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | UPLD_ID | NUMERIC(12) | NULL 허용; 업로드ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_UPLD.UPLD_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_FI_BIZ_PLAN | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE | CLOSE_YM | CHAR(6) | PK 구성열; NOT NULL; 마감연월; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-066 |

| TB_CL_CLOSE | CLOSE_STAT_CD | VARCHAR(10) | NOT NULL; 마감상태코드: WORK 작업중 / CLOSE 마감; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-066 |

| TB_CL_CLOSE | CLOSE_USER_ID | VARCHAR(30) | NULL 허용; 마감자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_SY_USER.USER_ID | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-066 |

| TB_CL_CLOSE | CLOSE_DTTM | TIMESTAMP | NULL 허용; 마감일시; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-066 |

| TB_CL_CLOSE | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-066 |

| TB_CL_CLOSE | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-066 |

| TB_CL_CLOSE | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-066 |

| TB_CL_CLOSE | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-066 |

| TB_CL_CLOSE_HIST | CLOSE_YM | CHAR(6) | PK 구성열; NOT NULL; 마감연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_CL_CLOSE.CLOSE_YM | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_HIST | HIST_SN | NUMERIC(5) | PK 구성열; NOT NULL; 이력일련번호; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_HIST | ACT_CD | VARCHAR(10) | NOT NULL; 작업코드: CLOSE 마감 / OPEN 해제; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_HIST | ACT_RSN | VARCHAR(500) | NULL 허용; 작업사유: 해제는 필수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_HIST | REMAIN_CHK_CN | VARCHAR(500) | NULL 허용; 남은점검항목: 마감 시 남은 항목(Q-14-3); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_HIST | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_HIST | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | CLOSE_YM | CHAR(6) | PK 구성열; NOT NULL; 마감연월; NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | FK TB_CL_CLOSE.CLOSE_YM | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | CHK_ITEM_CD | VARCHAR(10) | PK 구성열; NOT NULL; 점검항목코드: PAY 급여 업로드 / MM_OVER MM 1.0 초과자 / BILL 세금계산서 번호 미입력(그 달 취소 안 된 청구 중 TAX_INVC_NO 빈 건. 청구 확정은 v2라 판정하지 않음, 2026-10-29 D9) / FI 재무 항목 / SETL 영업 파트 정산 / BILL_MISS 청구 누락 점검. 이름은 마감 화면과 같게(2026-10-30 E16) (PAYOUT 지급 미처리는 2026-10-28 삭제); NOT NULL; 복합키의 구성열 | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | CHK_STAT_CD | VARCHAR(10) | NOT NULL; 점검상태코드: OK 정상 / WARN 확인 필요; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | CHK_CNT | NUMERIC(7) | NULL 허용; 점검건수; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | CHK_CN | VARCHAR(300) | NULL 허용; 점검내용: 한 줄 설명; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | LINK_SCRN_ID | VARCHAR(10) | NULL 허용; 연결화면ID: [확인하기]; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | CHK_DTTM | TIMESTAMP | NOT NULL; 점검일시: [다시 점검]; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID: 등록한 사용자(TB_SY_USER.USER_ID); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_CL_CLOSE_CHK | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시: 동시 수정 감지(낙관적 잠금); 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-003 |

| TB_SY_CALC_RULE | CALC_ID | VARCHAR(10) | PK 구성열; NOT NULL; 식번호: 명세서 식 번호 그대로(예: 식-16). 식-00은 계산 공통 원칙; NOT NULL; PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | SFR_ID | VARCHAR(10) | NULL 허용; 계산식 묶음 요구사항ID: ~912. 식-00은 없음; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | CALC_NM | VARCHAR(100) | NOT NULL; 식이름; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | FRML_CN | TEXT | NOT NULL; 수식: 한 줄 식; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | DTL_CN | TEXT | NOT NULL; 상세내용: 쓰는 화면·정의·수식·예시·예외·관련 테이블·결정(명세서 글 그대로, Markdown); NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | USE_SCRN_CN | VARCHAR(200) | NULL 허용; 쓰는화면: 예: UI-01, UI-12; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | SORT_ORD | NUMERIC(5) | NOT NULL; 정렬순서: 식 번호 숫자; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | REG_USER_ID | VARCHAR(30) | NOT NULL; 등록자ID; NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | REG_DTTM | TIMESTAMP | NOT NULL; 등록일시; DEFAULT CURRENT_TIMESTAMP NOT NULL; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | MOD_USER_ID | VARCHAR(30) | NULL 허용; 수정자ID; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

| TB_SY_CALC_RULE | MOD_DTTM | TIMESTAMP | NULL 허용; 수정일시; 별도 DEFAULT 없음; 비PK | 정적 enum 미선언; 동적 코드/정책을 추정하지 않음 | 명시 FK 없음 | 설치 후 업무 변경·삭제 없음; 기존 등록/수정 필드 보존; 보존기간 미정은 OQ-DATA-004 | REQ-005 |

### 키·CHECK·명시 인덱스 계약

아래는 실행 DDL이 아닌 설계 수준 키/제약 목록이다. 원본 명칭과 열 순서를 유지한다. 업무 ID는 PostgreSQL에서 따옴표 없이 사용하므로 물리 카탈로그 소문자 folding을 대문자 새 테이블 생성으로 오인하지 않는다(REQ-003, REQ-135).

| entity/table | PK·UK | CHECK | 명시 인덱스 | canonical_id |
| --- | --- | --- | --- | --- |

| TB_SY_USER | CONSTRAINT PK_TB_SY_USER PRIMARY KEY (USER_ID) | CONSTRAINT CK_TB_SY_USER_01 CHECK (TEMP_PWD_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_USER_02 CHECK (MFA_USE_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_ROLE | CONSTRAINT PK_TB_SY_ROLE PRIMARY KEY (ROLE_ID); CONSTRAINT UK_TB_SY_ROLE_01 UNIQUE (ROLE_NM) | CONSTRAINT CK_TB_SY_ROLE_01 CHECK (SNSTV_VIEW_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_ROLE_02 CHECK (SYS_ROLE_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_ROLE_03 CHECK (USE_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_USER_ROLE | CONSTRAINT PK_TB_SY_USER_ROLE PRIMARY KEY (USER_ID, ROLE_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SY_USER_ROLE_01(ROLE_ID) | REQ-003 |

| TB_SY_USER_SCOPE | CONSTRAINT PK_TB_SY_USER_SCOPE PRIMARY KEY (USER_ID, SCOPE_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SY_USER_SCOPE_01(PART_ID); IX_TB_SY_USER_SCOPE_02(PROJ_ID) | REQ-003 |

| TB_SY_MENU | CONSTRAINT PK_TB_SY_MENU PRIMARY KEY (MENU_ID) | CONSTRAINT CK_TB_SY_MENU_01 CHECK (USE_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_MENU_02 CHECK (PC_ONLY_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_MENU_03 CHECK (LOCK_YN IN ('Y', 'N')) | IX_TB_SY_MENU_01(UPPER_MENU_ID) | REQ-003 |

| TB_SY_ROLE_MENU_AUTH | CONSTRAINT PK_TB_SY_ROLE_MENU_AUTH PRIMARY KEY (ROLE_ID, MENU_ID) | CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_01 CHECK (INQ_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_02 CHECK (REG_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_03 CHECK (UPD_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_04 CHECK (DEL_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_05 CHECK (UPLD_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_06 CHECK (DNLD_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_ROLE_MENU_AUTH_07 CHECK (CNFM_YN IN ('Y', 'N')) | IX_TB_SY_ROLE_MENU_AUTH_01(MENU_ID) | REQ-003 |

| TB_SY_CODE_GRP | CONSTRAINT PK_TB_SY_CODE_GRP PRIMARY KEY (CODE_GRP_ID) | CONSTRAINT CK_TB_SY_CODE_GRP_01 CHECK (SYS_CODE_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_CODE_GRP_02 CHECK (VAL_CODE_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_CODE_GRP_03 CHECK (USE_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_CODE | CONSTRAINT PK_TB_SY_CODE PRIMARY KEY (CODE_GRP_ID, CODE) | CONSTRAINT CK_TB_SY_CODE_01 CHECK (USE_YN IN ('Y', 'N')); CONSTRAINT CK_TB_SY_CODE_02 CHECK (DEL_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_CODE_MAP | CONSTRAINT PK_TB_SY_CODE_MAP PRIMARY KEY (MAP_ID); CONSTRAINT UK_TB_SY_CODE_MAP_01 UNIQUE (SRC_TEXT, CODE_GRP_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SY_CODE_MAP_01(CODE_GRP_ID, CODE) | REQ-003 |

| TB_SY_OPER_STD | CONSTRAINT PK_TB_SY_OPER_STD PRIMARY KEY (STD_CD, APLY_BGN_YM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_USER_PREF | CONSTRAINT PK_TB_SY_USER_PREF PRIMARY KEY (USER_ID, PREF_TYPE_CD, PREF_KEY) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_NOTI | CONSTRAINT PK_TB_SY_NOTI PRIMARY KEY (NOTI_ID) | CONSTRAINT CK_TB_SY_NOTI_01 CHECK (READ_YN IN ('Y', 'N')) | IX_TB_SY_NOTI_01(RCVR_USER_ID); IX_TB_SY_NOTI_02(NOTI_DTTM) | REQ-003 |

| TB_SY_CHG_HIST | CONSTRAINT PK_TB_SY_CHG_HIST PRIMARY KEY (HIST_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SY_CHG_HIST_01(TRGT_TBL_NM, TRGT_KEY) | REQ-003 |

| TB_SY_ACCS_LOG | CONSTRAINT PK_TB_SY_ACCS_LOG PRIMARY KEY (LOG_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SY_ACCS_LOG_01(USER_ID); IX_TB_SY_ACCS_LOG_02(ACT_DTTM) | REQ-003 |

| TB_SY_UPLD | CONSTRAINT PK_TB_SY_UPLD PRIMARY KEY (UPLD_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_UPLD_ERR | CONSTRAINT PK_TB_SY_UPLD_ERR PRIMARY KEY (UPLD_ID, RSLT_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_AI_QNA | CONSTRAINT PK_TB_SY_AI_QNA PRIMARY KEY (QNA_ID) | CONSTRAINT CK_TB_SY_AI_QNA_01 CHECK (PRVS_YN IN ('Y', 'N')) | IX_TB_SY_AI_QNA_01(USER_ID) | REQ-003 |

| TB_SY_ADM_AREA | CONSTRAINT PK_TB_SY_ADM_AREA PRIMARY KEY (AREA_CD) | CONSTRAINT CK_TB_SY_ADM_AREA_01 CHECK (USE_YN IN ('Y', 'N')) | IX_TB_SY_ADM_AREA_01(AREA_LVL_CD); IX_TB_SY_ADM_AREA_02(SIDO_NM); IX_TB_SY_ADM_AREA_03(UPPER_AREA_CD) | REQ-003 |

| TB_SY_ADM_HDONG | CONSTRAINT PK_TB_SY_ADM_HDONG PRIMARY KEY (HDONG_CD, AREA_CD) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SY_ADM_HDONG_01(HDONG_NM); IX_TB_SY_ADM_HDONG_02(AREA_CD) | REQ-003 |

| TB_BS_CLNT | CONSTRAINT PK_TB_BS_CLNT PRIMARY KEY (CLNT_ID); CONSTRAINT UK_TB_BS_CLNT_01 UNIQUE (BIZ_REG_NO) | CONSTRAINT CK_TB_BS_CLNT_01 CHECK (USE_YN IN ('Y', 'N')) | IX_TB_BS_CLNT_01(CLNT_TYPE_CD) | REQ-003 |

| TB_BS_PART | CONSTRAINT PK_TB_BS_PART PRIMARY KEY (PART_ID); CONSTRAINT UK_TB_BS_PART_01 UNIQUE (PART_NM) | CONSTRAINT CK_TB_BS_PART_01 CHECK (HQ_YN IN ('Y', 'N')); CONSTRAINT CK_TB_BS_PART_02 CHECK (USE_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_BS_PART_STD | CONSTRAINT PK_TB_BS_PART_STD PRIMARY KEY (PART_ID, APLY_BGN_YM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_BS_PART_STD_01(PART_LDR_USER_ID) | REQ-003 |

| TB_HR_DEV | CONSTRAINT PK_TB_HR_DEV PRIMARY KEY (DEV_ID); CONSTRAINT UK_TB_HR_DEV_01 UNIQUE (MNG_NO) | CONSTRAINT CK_TB_HR_DEV_01 CHECK (USE_YN IN ('Y', 'N')) | IX_TB_HR_DEV_01(EMP_TYPE_CD); IX_TB_HR_DEV_02(JOB_CD); IX_TB_HR_DEV_03(WORK_STAT_CD); IX_TB_HR_DEV_04(BLNG_CLNT_ID); IX_TB_HR_DEV_05(PART_ID); IX_TB_HR_DEV_06(HOME_AREA_CD) | REQ-003 |

| TB_HR_DEV_SKILL | CONSTRAINT PK_TB_HR_DEV_SKILL PRIMARY KEY (DEV_ID, SKILL_NM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_HR_DEV_PRC | CONSTRAINT PK_TB_HR_DEV_PRC PRIMARY KEY (DEV_ID, APLY_BGN_YM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_HR_DEV_STAT | CONSTRAINT PK_TB_HR_DEV_STAT PRIMARY KEY (DEV_ID, STAT_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_HR_MON_PAY | CONSTRAINT PK_TB_HR_MON_PAY PRIMARY KEY (PAY_YM, DEV_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_HR_MON_PAY_01(DEV_ID); IX_TB_HR_MON_PAY_02(UPLD_ID) | REQ-003 |

| TB_HR_CAND | CONSTRAINT PK_TB_HR_CAND PRIMARY KEY (CAND_ID) | CONSTRAINT CK_TB_HR_CAND_01 CHECK (PRVC_AGRE_YN IN ('Y', 'N')) | IX_TB_HR_CAND_01(JOB_CD); IX_TB_HR_CAND_02(BLNG_CLNT_ID); IX_TB_HR_CAND_03(HOME_AREA_CD) | REQ-003 |

| TB_HR_CAND_SKILL | CONSTRAINT PK_TB_HR_CAND_SKILL PRIMARY KEY (CAND_ID, SKILL_NM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_HR_CAND_CNTC | CONSTRAINT PK_TB_HR_CAND_CNTC PRIMARY KEY (CAND_ID, CNTC_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_PJ_PROJ | CONSTRAINT PK_TB_PJ_PROJ PRIMARY KEY (PROJ_ID) | CONSTRAINT CK_TB_PJ_PROJ_01 CHECK (BDGT_MANUAL_YN IN ('Y', 'N')) | IX_TB_PJ_PROJ_01(CUST_CLNT_ID); IX_TB_PJ_PROJ_02(CTRT_TYPE_CD); IX_TB_PJ_PROJ_03(PROJ_STAT_CD); IX_TB_PJ_PROJ_04(OPP_ID) | REQ-003 |

| TB_PJ_PROJ_PART | CONSTRAINT PK_TB_PJ_PROJ_PART PRIMARY KEY (PROJ_ID, APLY_BGN_YM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_PJ_PROJ_PART_01(PART_ID) | REQ-003 |

| TB_PJ_PROJ_CHG | CONSTRAINT PK_TB_PJ_PROJ_CHG PRIMARY KEY (PROJ_ID, HIST_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_PJ_MNPW_PLAN | CONSTRAINT PK_TB_PJ_MNPW_PLAN PRIMARY KEY (PROJ_ID, JOB_CD) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_PJ_STEP | CONSTRAINT PK_TB_PJ_STEP PRIMARY KEY (PROJ_ID, STEP_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_PJ_WEEK_MEMO | CONSTRAINT PK_TB_PJ_WEEK_MEMO PRIMARY KEY (PROJ_ID, MEMO_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_PJ_WEEK_MEMO_01(WRT_DT) | REQ-003 |

| TB_PJ_ISSUE | CONSTRAINT PK_TB_PJ_ISSUE PRIMARY KEY (ISSUE_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_PJ_ISSUE_01(PROJ_ID); IX_TB_PJ_ISSUE_02(ISSUE_STAT_CD) | REQ-003 |

| TB_PJ_TK_DSTRB | CONSTRAINT PK_TB_PJ_TK_DSTRB PRIMARY KEY (PROJ_ID, DSTRB_STEP_CD, ROW_SN, DSTRB_YM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_PJ_TK_DSTRB_01(DEV_ID); IX_TB_PJ_TK_DSTRB_02(UPLD_ID) | REQ-003 |

| TB_PJ_DRCT_EXPS | CONSTRAINT PK_TB_PJ_DRCT_EXPS PRIMARY KEY (PROJ_ID, EXPS_YM, EXPS_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_PJ_ASGN | CONSTRAINT PK_TB_PJ_ASGN PRIMARY KEY (ASGN_ID) | CONSTRAINT CK_TB_PJ_ASGN_01 CHECK (EARLY_WTHDR_YN IN ('Y', 'N')) | IX_TB_PJ_ASGN_01(PROJ_ID); IX_TB_PJ_ASGN_02(DEV_ID, INPUT_BGN_DT, INPUT_END_DT); IX_TB_PJ_ASGN_03(CTRT_DEV_ID) | REQ-003 |

| TB_PJ_ASGN_HIST | CONSTRAINT PK_TB_PJ_ASGN_HIST PRIMARY KEY (ASGN_ID, HIST_SN) | CONSTRAINT CK_TB_PJ_ASGN_HIST_01 CHECK (RTRO_YN IN ('Y', 'N')); CONSTRAINT CK_TB_PJ_ASGN_HIST_02 CHECK (CLOSE_CROSS_YN IN ('Y', 'N')); CONSTRAINT CK_TB_PJ_ASGN_HIST_03 CHECK (CRCT_YN IN ('Y', 'N')) | IX_TB_PJ_ASGN_HIST_01(ASGN_CHG_TYPE_CD) | REQ-003 |

| TB_PJ_ASGN_MON | CONSTRAINT PK_TB_PJ_ASGN_MON PRIMARY KEY (ASGN_ID, INPUT_YM) | CONSTRAINT CK_TB_PJ_ASGN_MON_01 CHECK (FIX_YN IN ('Y', 'N')) | IX_TB_PJ_ASGN_MON_01(INPUT_YM); IX_TB_PJ_ASGN_MON_02(DEV_ID); IX_TB_PJ_ASGN_MON_03(PROJ_ID) | REQ-003 |

| TB_SL_OPP | CONSTRAINT PK_TB_SL_OPP PRIMARY KEY (OPP_ID) | CONSTRAINT CK_TB_SL_OPP_01 CHECK (PRBL_MANUAL_YN IN ('Y', 'N')) | IX_TB_SL_OPP_01(PART_ID); IX_TB_SL_OPP_02(OPP_STAT_CD); IX_TB_SL_OPP_03(CUST_CLNT_ID); IX_TB_SL_OPP_04(PROJ_ID) | REQ-003 |

| TB_SL_OPP_NEED | CONSTRAINT PK_TB_SL_OPP_NEED PRIMARY KEY (OPP_ID, NEED_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SL_OPP_NEED_01(JOB_CD) | REQ-003 |

| TB_SL_TENT_ASGN | CONSTRAINT PK_TB_SL_TENT_ASGN PRIMARY KEY (TENT_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SL_TENT_ASGN_01(OPP_ID, NEED_SN); IX_TB_SL_TENT_ASGN_02(DEV_ID) | REQ-003 |

| TB_SL_APRV_HIST | CONSTRAINT PK_TB_SL_APRV_HIST PRIMARY KEY (OPP_ID, HIST_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SL_RCMD_LOG | CONSTRAINT PK_TB_SL_RCMD_LOG PRIMARY KEY (RCMD_ID, RANK_ORD) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_SL_RCMD_LOG_01(OPP_ID); IX_TB_SL_RCMD_LOG_02(PROJ_ID); IX_TB_SL_RCMD_LOG_03(DEV_ID) | REQ-003 |

| TB_BL_BILL | CONSTRAINT PK_TB_BL_BILL PRIMARY KEY (BILL_ID) | CONSTRAINT CK_TB_BL_BILL_01 CHECK (CNCL_YN IN ('Y', 'N')) | IX_TB_BL_BILL_01(PROJ_ID); IX_TB_BL_BILL_02(BILL_YM); IX_TB_BL_BILL_03(PAY_DUE_DT); IX_TB_BL_BILL_04(CNCL_USER_ID) | REQ-003 |

| TB_BL_RCPT | CONSTRAINT PK_TB_BL_RCPT PRIMARY KEY (RCPT_ID) | CONSTRAINT CK_TB_BL_RCPT_01 CHECK (CNCL_YN IN ('Y', 'N')) | IX_TB_BL_RCPT_01(BILL_ID); IX_TB_BL_RCPT_02(CNCL_USER_ID) | REQ-003 |

| TB_BL_DUN_HIST | CONSTRAINT PK_TB_BL_DUN_HIST PRIMARY KEY (BILL_ID, DUN_SN) | CONSTRAINT CK_TB_BL_DUN_HIST_01 CHECK (RESEND_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_CO_MON_COST | CONSTRAINT PK_TB_CO_MON_COST PRIMARY KEY (COST_YM, DEV_ID, PROJ_ID, ASGN_ID) | CONSTRAINT CK_TB_CO_MON_COST_01 CHECK (FIX_YN IN ('Y', 'N')) | IX_TB_CO_MON_COST_01(DEV_ID); IX_TB_CO_MON_COST_02(PROJ_ID); IX_TB_CO_MON_COST_03(ASGN_ID); IX_TB_CO_MON_COST_04(PART_ID) | REQ-003 |

| TB_CO_MON_PL | CONSTRAINT PK_TB_CO_MON_PL PRIMARY KEY (PL_YM) | CONSTRAINT CK_TB_CO_MON_PL_01 CHECK (MIGR_YN IN ('Y', 'N')); CONSTRAINT CK_TB_CO_MON_PL_02 CHECK (FIX_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_CO_PREV_ADJ | CONSTRAINT PK_TB_CO_PREV_ADJ PRIMARY KEY (ADJ_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_CO_PREV_ADJ_01(APLY_YM) | REQ-003 |

| TB_CO_PART_SETL | CONSTRAINT PK_TB_CO_PART_SETL PRIMARY KEY (PART_ID, SETL_YM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_CO_PART_SETL_01(CNFM_USER_ID) | REQ-003 |

| TB_CO_PART_SETL_DTL | CONSTRAINT PK_TB_CO_PART_SETL_DTL PRIMARY KEY (PART_ID, SETL_YM, DEV_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_CO_PART_SETL_DTL_01(SETL_YM); IX_TB_CO_PART_SETL_DTL_02(DEV_ID) | REQ-003 |

| TB_FI_ITEM | CONSTRAINT PK_TB_FI_ITEM PRIMARY KEY (FIN_YM, ITEM_SN) | CONSTRAINT CK_TB_FI_ITEM_01 CHECK (RPT_YN IN ('Y', 'N')); CONSTRAINT CK_TB_FI_ITEM_02 CHECK (AUTO_FILL_YN IN ('Y', 'N')) | IX_TB_FI_ITEM_01(UPLD_ID) | REQ-003 |

| TB_FI_CORP_TAX | CONSTRAINT PK_TB_FI_CORP_TAX PRIMARY KEY (TAX_YR) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_FI_TAX_PAY | CONSTRAINT PK_TB_FI_TAX_PAY PRIMARY KEY (TAX_YR, PAY_TYPE_CD) | CONSTRAINT CK_TB_FI_TAX_PAY_01 CHECK (PAY_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_FI_LOAN | CONSTRAINT PK_TB_FI_LOAN PRIMARY KEY (LOAN_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_FI_LOAN_01(LOAN_DT) | REQ-003 |

| TB_FI_CARD | CONSTRAINT PK_TB_FI_CARD PRIMARY KEY (CARD_ID) | CONSTRAINT CK_TB_FI_CARD_01 CHECK (USE_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_FI_CARD_MON | CONSTRAINT PK_TB_FI_CARD_MON PRIMARY KEY (PAY_YM) | CONSTRAINT CK_TB_FI_CARD_MON_01 CHECK (EDIT_YN IN ('Y', 'N')) | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_FI_CARD_PAY | CONSTRAINT PK_TB_FI_CARD_PAY PRIMARY KEY (PAY_YM, CARD_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_FI_CARD_PAY_01(CARD_ID) | REQ-003 |

| TB_FI_XFER | CONSTRAINT PK_TB_FI_XFER PRIMARY KEY (XFER_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_FI_XFER_01(XFER_DT) | REQ-003 |

| TB_FI_BIZ_PLAN | CONSTRAINT PK_TB_FI_BIZ_PLAN PRIMARY KEY (PLAN_YM, PART_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_FI_BIZ_PLAN_01(PART_ID); IX_TB_FI_BIZ_PLAN_02(UPLD_ID) | REQ-003 |

| TB_CL_CLOSE | CONSTRAINT PK_TB_CL_CLOSE PRIMARY KEY (CLOSE_YM) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | IX_TB_CL_CLOSE_01(CLOSE_USER_ID) | REQ-003 |

| TB_CL_CLOSE_HIST | CONSTRAINT PK_TB_CL_CLOSE_HIST PRIMARY KEY (CLOSE_YM, HIST_SN) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_CL_CLOSE_CHK | CONSTRAINT PK_TB_CL_CLOSE_CHK PRIMARY KEY (CLOSE_YM, CHK_ITEM_CD) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

| TB_SY_CALC_RULE | CONSTRAINT PK_TB_SY_CALC_RULE PRIMARY KEY (CALC_ID) | 추가 CHECK 미선언; 업무 검증은 Validation Rule Matrix | 명시 추가 인덱스 없음; PK/UK 유지 | REQ-003 |

### 식별자 시퀀스

| sequence | 초기·증가 계약 | 사용 필드 | canonical_id |
| --- | --- | --- | --- |

| SQ_TB_SY_CODE_MAP | start=1, increment=1; 원본 유지 | TB_SY_CODE_MAP.MAP_ID | REQ-003 |

| SQ_TB_SY_NOTI | start=1, increment=1; 원본 유지 | TB_SY_NOTI.NOTI_ID | REQ-003 |

| SQ_TB_SY_CHG_HIST | start=1, increment=1; 원본 유지 | TB_SY_CHG_HIST.HIST_ID | REQ-003 |

| SQ_TB_SY_ACCS_LOG | start=1, increment=1; 원본 유지 | TB_SY_ACCS_LOG.LOG_ID | REQ-003 |

| SQ_TB_SY_UPLD | start=1, increment=1; 원본 유지 | TB_SY_UPLD.UPLD_ID | REQ-003 |

| SQ_TB_SY_AI_QNA | start=1, increment=1; 원본 유지 | TB_SY_AI_QNA.QNA_ID | REQ-003 |

| SQ_TB_PJ_ISSUE | start=1, increment=1; 원본 유지 | TB_PJ_ISSUE.ISSUE_ID | REQ-003 |

| SQ_TB_SL_TENT_ASGN | start=1, increment=1; 원본 유지 | TB_SL_TENT_ASGN.TENT_ID | REQ-003 |

| SQ_TB_SL_RCMD_LOG | start=1, increment=1; 원본 유지 |  | REQ-003 |

| SQ_TB_BL_RCPT | start=1, increment=1; 원본 유지 | TB_BL_RCPT.RCPT_ID | REQ-003 |

| SQ_TB_CO_PREV_ADJ | start=1, increment=1; 원본 유지 | TB_CO_PREV_ADJ.ADJ_ID | REQ-003 |

| SQ_TB_FI_LOAN | start=1, increment=1; 원본 유지 | TB_FI_LOAN.LOAN_SN | REQ-003 |

| SQ_TB_FI_XFER | start=1, increment=1; 원본 유지 | TB_FI_XFER.XFER_SN | REQ-003 |

## Relationship Contract

부모→자식 방향으로 cardinality를 기록한다. 73개의 명시 FK 제약이며 복합 FK의 열별 투영은 76개다. data-model.json.relationships는 각 구성열을 별도 edge로 표현하지만 아래 동일 constraint의 열 묶음을 함께 적용해야 한다. 구성열 FK를 독립 FK 76개로 생성하면 안 된다. inferred는 모두 false이며 이름 규칙으로 FK를 추가하지 않는다. 원문 DDL에 ON DELETE/ON UPDATE 확장 구문이 없으므로 추가 CASCADE·orphanRemoval을 도입하지 않는다. 실제 FK의 참조 무결성을 보존하고 이번 데이터 삭제는 승인 정책으로만 수행한다. CSV·이력 문자열·메뉴 진단은 논리 검사이지 추가 FK나 가상 물리 테이블이 아니다.

| FK 제약 | 부모 식별자 | 자식 참조 | cardinality | delete/orphan 정책 | canonical_id |
| --- | --- | --- | --- | --- | --- |

| FK_TB_SY_USER_ROLE_01 | TB_SY_USER(USER_ID) | TB_SY_USER_ROLE(USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_USER_ROLE_02 | TB_SY_ROLE(ROLE_ID) | TB_SY_USER_ROLE(ROLE_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_USER_SCOPE_01 | TB_SY_USER(USER_ID) | TB_SY_USER_SCOPE(USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_USER_SCOPE_02 | TB_BS_PART(PART_ID) | TB_SY_USER_SCOPE(PART_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_USER_SCOPE_03 | TB_PJ_PROJ(PROJ_ID) | TB_SY_USER_SCOPE(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_MENU_01 | TB_SY_MENU(MENU_ID) | TB_SY_MENU(UPPER_MENU_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_ROLE_MENU_AUTH_01 | TB_SY_ROLE(ROLE_ID) | TB_SY_ROLE_MENU_AUTH(ROLE_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_ROLE_MENU_AUTH_02 | TB_SY_MENU(MENU_ID) | TB_SY_ROLE_MENU_AUTH(MENU_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_CODE_01 | TB_SY_CODE_GRP(CODE_GRP_ID) | TB_SY_CODE(CODE_GRP_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_CODE_MAP_01 | TB_SY_CODE(CODE_GRP_ID,CODE) | TB_SY_CODE_MAP(CODE_GRP_ID,CODE) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_USER_PREF_01 | TB_SY_USER(USER_ID) | TB_SY_USER_PREF(USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_NOTI_01 | TB_SY_USER(USER_ID) | TB_SY_NOTI(RCVR_USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_UPLD_ERR_01 | TB_SY_UPLD(UPLD_ID) | TB_SY_UPLD_ERR(UPLD_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_ADM_AREA_01 | TB_SY_ADM_AREA(AREA_CD) | TB_SY_ADM_AREA(UPPER_AREA_CD) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SY_ADM_HDONG_01 | TB_SY_ADM_AREA(AREA_CD) | TB_SY_ADM_HDONG(AREA_CD) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_BS_PART_STD_01 | TB_BS_PART(PART_ID) | TB_BS_PART_STD(PART_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_BS_PART_STD_02 | TB_SY_USER(USER_ID) | TB_BS_PART_STD(PART_LDR_USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_DEV_01 | TB_BS_CLNT(CLNT_ID) | TB_HR_DEV(BLNG_CLNT_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_DEV_02 | TB_BS_PART(PART_ID) | TB_HR_DEV(PART_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_DEV_03 | TB_SY_ADM_AREA(AREA_CD) | TB_HR_DEV(HOME_AREA_CD) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_DEV_SKILL_01 | TB_HR_DEV(DEV_ID) | TB_HR_DEV_SKILL(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_DEV_PRC_01 | TB_HR_DEV(DEV_ID) | TB_HR_DEV_PRC(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_DEV_STAT_01 | TB_HR_DEV(DEV_ID) | TB_HR_DEV_STAT(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_MON_PAY_01 | TB_HR_DEV(DEV_ID) | TB_HR_MON_PAY(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_MON_PAY_02 | TB_SY_UPLD(UPLD_ID) | TB_HR_MON_PAY(UPLD_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_CAND_01 | TB_BS_CLNT(CLNT_ID) | TB_HR_CAND(BLNG_CLNT_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_CAND_02 | TB_SY_ADM_AREA(AREA_CD) | TB_HR_CAND(HOME_AREA_CD) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_CAND_SKILL_01 | TB_HR_CAND(CAND_ID) | TB_HR_CAND_SKILL(CAND_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_HR_CAND_CNTC_01 | TB_HR_CAND(CAND_ID) | TB_HR_CAND_CNTC(CAND_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_PROJ_01 | TB_BS_CLNT(CLNT_ID) | TB_PJ_PROJ(CUST_CLNT_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_PROJ_02 | TB_SL_OPP(OPP_ID) | TB_PJ_PROJ(OPP_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_PROJ_PART_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_PROJ_PART(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_PROJ_PART_02 | TB_BS_PART(PART_ID) | TB_PJ_PROJ_PART(PART_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_PROJ_CHG_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_PROJ_CHG(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_MNPW_PLAN_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_MNPW_PLAN(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_STEP_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_STEP(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_WEEK_MEMO_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_WEEK_MEMO(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_ISSUE_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_ISSUE(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_TK_DSTRB_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_TK_DSTRB(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_TK_DSTRB_02 | TB_HR_DEV(DEV_ID) | TB_PJ_TK_DSTRB(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_TK_DSTRB_03 | TB_SY_UPLD(UPLD_ID) | TB_PJ_TK_DSTRB(UPLD_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_DRCT_EXPS_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_DRCT_EXPS(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_ASGN_01 | TB_PJ_PROJ(PROJ_ID) | TB_PJ_ASGN(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_ASGN_02 | TB_HR_DEV(DEV_ID) | TB_PJ_ASGN(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_ASGN_03 | TB_HR_DEV(DEV_ID) | TB_PJ_ASGN(CTRT_DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_ASGN_HIST_01 | TB_PJ_ASGN(ASGN_ID) | TB_PJ_ASGN_HIST(ASGN_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_PJ_ASGN_MON_01 | TB_PJ_ASGN(ASGN_ID) | TB_PJ_ASGN_MON(ASGN_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_OPP_01 | TB_BS_CLNT(CLNT_ID) | TB_SL_OPP(CUST_CLNT_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_OPP_02 | TB_BS_PART(PART_ID) | TB_SL_OPP(PART_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_OPP_03 | TB_PJ_PROJ(PROJ_ID) | TB_SL_OPP(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_OPP_NEED_01 | TB_SL_OPP(OPP_ID) | TB_SL_OPP_NEED(OPP_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_TENT_ASGN_01 | TB_SL_OPP_NEED(OPP_ID,NEED_SN) | TB_SL_TENT_ASGN(OPP_ID,NEED_SN) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_TENT_ASGN_02 | TB_HR_DEV(DEV_ID) | TB_SL_TENT_ASGN(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_APRV_HIST_01 | TB_SL_OPP(OPP_ID) | TB_SL_APRV_HIST(OPP_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_RCMD_LOG_01 | TB_SL_OPP(OPP_ID) | TB_SL_RCMD_LOG(OPP_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_RCMD_LOG_02 | TB_PJ_PROJ(PROJ_ID) | TB_SL_RCMD_LOG(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_SL_RCMD_LOG_03 | TB_HR_DEV(DEV_ID) | TB_SL_RCMD_LOG(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_BL_BILL_01 | TB_PJ_PROJ(PROJ_ID) | TB_BL_BILL(PROJ_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_BL_BILL_02 | TB_SY_USER(USER_ID) | TB_BL_BILL(CNCL_USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_BL_RCPT_01 | TB_BL_BILL(BILL_ID) | TB_BL_RCPT(BILL_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_BL_RCPT_02 | TB_SY_USER(USER_ID) | TB_BL_RCPT(CNCL_USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_BL_DUN_HIST_01 | TB_BL_BILL(BILL_ID) | TB_BL_DUN_HIST(BILL_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_CO_PART_SETL_01 | TB_BS_PART(PART_ID) | TB_CO_PART_SETL(PART_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_CO_PART_SETL_02 | TB_SY_USER(USER_ID) | TB_CO_PART_SETL(CNFM_USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_CO_PART_SETL_DTL_01 | TB_CO_PART_SETL(PART_ID,SETL_YM) | TB_CO_PART_SETL_DTL(PART_ID,SETL_YM) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_CO_PART_SETL_DTL_02 | TB_HR_DEV(DEV_ID) | TB_CO_PART_SETL_DTL(DEV_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_FI_ITEM_01 | TB_SY_UPLD(UPLD_ID) | TB_FI_ITEM(UPLD_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_FI_CARD_PAY_01 | TB_FI_CARD_MON(PAY_YM) | TB_FI_CARD_PAY(PAY_YM) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_FI_CARD_PAY_02 | TB_FI_CARD(CARD_ID) | TB_FI_CARD_PAY(CARD_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_FI_BIZ_PLAN_01 | TB_SY_UPLD(UPLD_ID) | TB_FI_BIZ_PLAN(UPLD_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_CL_CLOSE_01 | TB_SY_USER(USER_ID) | TB_CL_CLOSE(CLOSE_USER_ID) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_CL_CLOSE_HIST_01 | TB_CL_CLOSE(CLOSE_YM) | TB_CL_CLOSE_HIST(CLOSE_YM) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

| FK_TB_CL_CLOSE_CHK_01 | TB_CL_CLOSE(CLOSE_YM) | TB_CL_CLOSE_CHK(CLOSE_YM) | 1:N | 명시 cascade 없음; FK 원문 유지, 고아 참조 허용 안 함 | REQ-003 |

### 삭제 참조 가드 — FK와 별도인 논리 참조

REQ-094/REQ-097/REQ-098의 고정 목록을 실제 참조 검사로 구현한다. 다음 열은 새 FK를 만들어 동적 그룹코드에 임의 연결하는 계약이 아니다. 취소/중지/마감자료도 검사하며 코드 자신의 NEW 이력은 사용으로 세지 않는다. 모든 그룹은 TB_SY_CODE_MAP(CODE_GRP_ID,CODE)를 추가 검사한다. 테이블명을 요청에서 받아 SQL을 조합하지 않는다.

| group | 실제 검사 대상 | 별도 조건 | canonical_id |
| --- | --- | --- | --- |
| JOB | TB_HR_DEV.JOB_CD/SUB_JOB_CD; TB_HR_CAND.JOB_CD/SUB_JOB_CD; TB_PJ_MNPW_PLAN.JOB_CD; TB_PJ_ASGN.JOB_CD; TB_SL_OPP_NEED.JOB_CD; TB_SL_RCMD_LOG.JOB_CD | 다른 미삭제 JOB/SKILL/RSM_JOB_KW의 JOB CSV; 자기행 제외 | REQ-097 |
| GRADE | TB_HR_DEV.GRADE_CD; TB_HR_CAND.GRADE_CD; TB_SL_OPP_NEED.GRADE_CD; TB_SL_RCMD_LOG.REQ_GRADE_CD | 취소·중지 자료 포함 | REQ-097 |
| JBPS | TB_HR_DEV.JBPS_CD | 공통 대응도 검사 | REQ-097 |
| EXT_PSBL | TB_PJ_ASGN.EXT_PSBL_CD; TB_PJ_ASGN_HIST.BFR_VAL/AFT_VAL | ASGN_CHG_TYPE_CD=EXTP인 이력의 코드/이름 | REQ-097 |
| SGA_ITEM | TB_FI_ITEM.SGA_ITEM_CD | 공통 대응 포함 | REQ-097 |
| DRCT_EXPS_ITEM | TB_PJ_DRCT_EXPS.DRCT_EXPS_ITEM_CD | 공통 대응 포함 | REQ-097 |
| CHG_ITEM | TB_PJ_PROJ_CHG.CHG_ITEM_CD | 공통 대응 포함 | REQ-097 |
| IMPR | TB_PJ_ISSUE.IMPR_CD | 공통 대응 포함 | REQ-097 |
| PROJ_STEP | TB_PJ_STEP.PROJ_STEP_CD | 공통 대응 포함 | REQ-097 |
| CNTC_STAT | TB_HR_CAND.CNTC_STAT_CD; TB_HR_CAND_CNTC.CNTC_STAT_CD | 공통 대응 포함 | REQ-097 |
| CAND_SRC | TB_HR_CAND.CAND_SRC_CD | 공통 대응 포함 | REQ-097 |
| ASGN_CHG_RSN | TB_PJ_ASGN_HIST.ASGN_CHG_RSN_CD | 공통 대응 포함 | REQ-097 |
| LOSE_RSN | TB_PJ_PROJ.LOSE_RSN_CD; TB_SL_OPP.LOSE_RSN_CD | 공통 대응 포함 | REQ-097 |
| RCMD_EXCL | TB_SL_RCMD_LOG.RCMD_EXCL_CD | 공통 대응 포함 | REQ-097 |
| BANK | TB_BS_CLNT.BANK_CD | 공통 대응 포함 | REQ-097 |
| INDUTY | TB_BS_CLNT.INDUTY_CD | 공통 대응 포함 | REQ-097 |
| SKILL | TB_HR_DEV_SKILL.SKILL_NM; TB_HR_CAND_SKILL.SKILL_NM | 현재 CODE_NM과 TB_SY_CHG_HIST의 CODE_NM 과거명 모두 | REQ-097, REQ-123 |
| RSM_LABEL/RSM_JOB_KW/신규업무그룹 | TB_SY_CODE_MAP.CODE_GRP_ID/CODE | 별도 업무 컬럼 참조 없음 | REQ-097 |
| ALWNC | 삭제 대상 아님 | VAL_CODE_YN 모드 보호; SYS와 겹치면 SYS 보호 우선 | REQ-089, REQ-098 |

삭제 직전 그룹/행 잠금 후 최신 참조를 재검사한다. 참조1건 이상이면409 CODE_IN_USE·참조 유형/건수와 사용중지 안내이며 데이터/이력은 불변이다. 미참조 일반코드만 DEL_YN=Y이고 물리행과 PK는 영구 재사용하지 않는다(REQ-079, REQ-094, REQ-095, REQ-096).

## Field Mutability Matrix

API의 wire 형식은 contracts/openapi.yaml만 정본이다. 아래는 DB와 공개 DTO 사이의 대응이며 별도 API schema를 정의하지 않는다. 모든 등록/수정자와 시각은 서버 소유이며 요청 필드가 아니다. client가 예상 revision 이외 서버 필드·SYS/VAL/DEL을 주입하면400, 명시된 모드 제한 위반은409다. SYS_CODE_YN=Y 규칙을 VAL_CODE_YN보다 먼저 적용하므로 시스템+값 그룹도 기존 이름만 허용한다. NULL 저장 허용과 UI 필수 검증은 다른 층이다.

| entity/table | field | source-of-truth | mutable states | readonly states | editable screen | operationId/path | validation | canonical_id |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

| TB_SY_CODE_GRP | CODE_GRP_ID | DB 정본; 요청 groupId | 신규만 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다. | REQ-014 |

| TB_SY_CODE_GRP | CODE_GRP_NM | DB 정본; 요청 name | 신규만 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다. | REQ-075 |

| TB_SY_CODE_GRP | SYS_CODE_YN | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-076 |

| TB_SY_CODE_GRP | VAL_CODE_YN | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-076 |

| TB_SY_CODE_GRP | SORT_ORD | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-076 |

| TB_SY_CODE_GRP | USE_YN | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-076 |

| TB_SY_CODE_GRP | REG_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE_GRP | REG_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE_GRP | MOD_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE_GRP | MOD_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeGroup / getCodeGroups | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE | CODE_GRP_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-071 |

| TB_SY_CODE | CODE | DB 정본; 요청 code | 신규만 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다. | REQ-015 |

| TB_SY_CODE | CODE_NM | DB 정본; 요청 name | 일반 기존/신규, SYS 기존 이름 허용 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다. | REQ-074 |

| TB_SY_CODE | SORT_ORD | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-071 |

| TB_SY_CODE | USE_YN | DB 정본; 요청 useYn | 일반 업무 코드만 N/Y | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다. | REQ-092 |

| TB_SY_CODE | DEL_YN | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-071 |

| TB_SY_CODE | CODE_VAL | DB 정본; 요청 codeVal | 일반 값그룹 기존/신규만; SYS 불가 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다. | REQ-089 |

| TB_SY_CODE | UNIT_NM | DB 정본; 요청 unitNm | 일반 값그룹 신규만 원/% | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다. | REQ-090 |

| TB_SY_CODE | ADD_VAL1 | DB 정본; 요청 addVal1 | 일반 비시스템 지정그룹만 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. | REQ-083 |

| TB_SY_CODE | ADD_VAL2 | DB 정본; 요청 addVal2 | 일반 비시스템 지정그룹만 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. | REQ-083 |

| TB_SY_CODE | ADD_VAL3 | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-071 |

| TB_SY_CODE | REG_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE | REG_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE | MOD_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE | MOD_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveGroupCodes / deleteGroupCode / getGroupCodes / getCodes | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE_MAP | MAP_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeMapping / deleteCodeMappings / getCodeMappings | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-117 |

| TB_SY_CODE_MAP | SRC_TEXT | DB 정본; 요청 sourceText | 신규만 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | createCodeMapping / deleteCodeMappings / getCodeMappings | 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. | REQ-116 |

| TB_SY_CODE_MAP | CODE_GRP_ID | DB 정본; 요청 groupId | 신규만 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | createCodeMapping / deleteCodeMappings / getCodeMappings | 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. | REQ-116 |

| TB_SY_CODE_MAP | CODE | DB 정본; 요청 code | 신규만 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | createCodeMapping / deleteCodeMappings / getCodeMappings | 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. | REQ-116 |

| TB_SY_CODE_MAP | REG_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeMapping / deleteCodeMappings / getCodeMappings | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE_MAP | REG_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeMapping / deleteCodeMappings / getCodeMappings | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE_MAP | MOD_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeMapping / deleteCodeMappings / getCodeMappings | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CODE_MAP | MOD_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | createCodeMapping / deleteCodeMappings / getCodeMappings | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_OPER_STD | STD_CD | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-102 |

| TB_SY_OPER_STD | APLY_BGN_YM | DB 정본; 요청 effectiveMonth | 일반 기준 신규 적용행 생성만; CASH 기존값 보존 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다. | REQ-111 |

| TB_SY_OPER_STD | STD_NM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-102 |

| TB_SY_OPER_STD | STD_VAL | DB 정본; 요청 value | 일반 기준은 허용 적용월; CASH는 기존시작월 미마감 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다. | REQ-101 |

| TB_SY_OPER_STD | UNIT_NM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-102 |

| TB_SY_OPER_STD | STD_DESC | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-102 |

| TB_SY_OPER_STD | REG_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_OPER_STD | REG_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_OPER_STD | MOD_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_OPER_STD | MOD_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | UI-27 /UI-27 | saveOperatingStandards / getOperatingStandards / getOperatingStandardHistory / getParameter | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_USER_PREF | USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | 사용자 메뉴 화면 설정 | putPreference / deletePreference / getPreferences | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-048 |

| TB_SY_USER_PREF | PREF_TYPE_CD | DB 정본; 요청 type | 신규 식별; 기존 불변 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | 사용자 메뉴 화면 설정 | putPreference / deletePreference / getPreferences | 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. | REQ-048 |

| TB_SY_USER_PREF | PREF_KEY | DB 정본; 요청 key | 신규 식별; 기존 불변 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | 사용자 메뉴 화면 설정 | putPreference / deletePreference / getPreferences | 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. | REQ-048 |

| TB_SY_USER_PREF | PREF_VAL | DB 정본; 요청 value | 유효 type/key 명시저장 | 기존 PK 및 해당 모드 금지 상태; 기준 메타 불변 | 사용자 메뉴 화면 설정 | putPreference / deletePreference / getPreferences | 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. | REQ-048 |

| TB_SY_USER_PREF | REG_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | 사용자 메뉴 화면 설정 | putPreference / deletePreference / getPreferences | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_USER_PREF | REG_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | 사용자 메뉴 화면 설정 | putPreference / deletePreference / getPreferences | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_USER_PREF | MOD_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | 사용자 메뉴 화면 설정 | putPreference / deletePreference / getPreferences | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_USER_PREF | MOD_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | 사용자 메뉴 화면 설정 | putPreference / deletePreference / getPreferences | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CHG_HIST | HIST_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-125 |

| TB_SY_CHG_HIST | TRGT_TBL_NM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-125 |

| TB_SY_CHG_HIST | TRGT_KEY | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-125 |

| TB_SY_CHG_HIST | CHG_TYPE_CD | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-125 |

| TB_SY_CHG_HIST | CHG_ITEM_NM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-125 |

| TB_SY_CHG_HIST | BFR_VAL | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-125 |

| TB_SY_CHG_HIST | AFT_VAL | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-125 |

| TB_SY_CHG_HIST | CHG_RSN | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 읽기/서버 생성만; 입력 계약에 없는 필드400 | REQ-125 |

| TB_SY_CHG_HIST | REG_USER_ID | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

| TB_SY_CHG_HIST | REG_DTTM | 원본 DDL·DB 및 서버 계산 | 사용자 직접 수정 없음; 승인 본체 연산이 서버 관리 | 모든 클라이언트 상태 | N/A — 서버 전용 이력 기록 | getCommonCodeHistory; 본체 연산의 서버 append | 처리자 admin·시각 서버 기록, client 주입400 | REQ-011 |

### 조회 전용 필드의 공개 경계

| DB 원천 | 공개 DTO 바인딩 | 불변/미노출 경계 | API operationId | canonical_id |
| --- | --- | --- | --- | --- |
| TB_SY_USER.USER_ID/USER_NM 및 역할 연결 | CurrentUser.admin/name/roleIds/developmentMode | ADMIN·임시 관리자·developmentMode=true를 중앙 공급; EMAIL_ADDR/PWD_HASH/MFA_* 노출 금지 | getCurrentUser | REQ-025, REQ-026, REQ-027, REQ-130 |
| TB_SY_MENU.MENU_ID/UPPER_MENU_ID/MENU_NM/SCRN_ID/SORT_ORD/ICON_NM/PC_ONLY_YN | MenuNode.menuId/parentId/name/screenId/sortOrder/icon/pcOnly/children | USE_YN=Y 및 정상 트리만; pcOnly는 Y/N DB에서 JSON boolean으로 투영; 메뉴 쓰기 없음 | getMenus | REQ-035, REQ-037 |
| TB_CL_CLOSE.CLOSE_YM/CLOSE_STAT_CD, TB_SY_OPER_STD의 CASH_BASE_YM | MonthClose와 PeriodContext의 월/상태·현재일/연월/시간대 | 시작월 전 이관·결산완료; CLOSE=마감; WORK/행없음=작업중; 마감행 생성 없음 | getMonthClose / getPeriodContext | REQ-063, REQ-064, REQ-066 |
| TB_SY_CODE_GRP 전용 공개필드 | CodeGroup.groupId/name/sysCodeYn/valCodeYn/useYn/sortOrder/codeCount | codeCount는 미삭제 코드 수의 조회 집계, 새 컬럼 아님 | getCodeGroups | REQ-069 |
| TB_SY_CODE.CODE/CODE_NM/ADD_VAL1/2/3/CODE_VAL/UNIT_NM | CodeLookup.code/name/addVal1/addVal2/addVal3/codeVal/unitNm | 미삭제 active 기본; includeInactive로 기존 label 구분, 신규 선택에 섞지 않음 | getCodes | REQ-126 |
| TB_SY_CODE_MAP.MAP_ID/SRC_TEXT/CODE_GRP_ID/CODE 및 TB_SY_CODE.CODE_NM | CodeMapping.mapId/sourceText/groupId/code/codeName/revision | codeName/revision은 조회 투영; 실제 테이블에 새 컬럼 없음 | getCodeMappings | REQ-115, REQ-118 |
| TB_SY_OPER_STD.STD_CD/STD_NM/STD_VAL/UNIT_NM/APLY_BGN_YM/STD_DESC | OperatingStandard.key/name/value/unitNm/effectiveMonth/description/keyRevision | 일반 값 십진문자열; CASH_BASE_YM value만 정수 DB→6자리 YYYYMM 문자열; meta readonly | getOperatingStandards / getParameter / getOperatingStandardHistory | REQ-099, REQ-100, REQ-109, REQ-127 |
| TB_SY_USER_PREF.PREF_TYPE_CD/PREF_KEY/PREF_VAL 및 감사필드 | Preference.type/key/value/revision/stored | JSON parse 손상은 해당 항목만 오류·revision, 자동삭제 없음; DB2000자는 전체 JSON 직렬화 길이 | getPreferences | REQ-047, REQ-049 |
| TB_SY_CHG_HIST 전수 공개 이력 필드 | CommonCodeHistory.occurredAt/target/groupId/entityKey/type/item/before/after/actor | target/groupId/표시명은 실제 테이블/키로 투영; 새 저장열 없음 | getCommonCodeHistory | REQ-122, REQ-124, REQ-125 |

## State Transition Contract

아래는 원문에 있는 허용/거부만 기술한다. 정의되지 않은 상태를 NONE/비활성으로 확정하거나 향후 workflow를 생성하지 않는다. persisted enum과 화면 계산 상태는 구분한다.

| 대상 | 선행 조건·전이 | 관찰 가능한 저장/거부 증거 | canonical_id |
| --- | --- | --- | --- |
| 일반 업무 그룹 | 신규 → SYS=N/VAL=N/USE=Y, SORT=max+1 | createCodeGroup201 후 getCodeGroups에서 재조회·NEW 이력 | REQ-076 |
| 일반 코드 신규 | 신규 → USE_YN=Y/DEL_YN=N | 하단 saveGroupCodes 성공 후만 DB행 존재; 모달 추가만으로0건 | REQ-078, REQ-080 |
| 일반 코드 중지·재개 | USE_YN Y→N / N→Y | STOP/RESUME 이력, getCodes의 active 제외/복원; includeInactive는 기존 이름 반환 | REQ-092, REQ-093 |
| 일반 코드 삭제 | 미참조 DEL_YN N→Y | 물리행 유지·DEL 이력, PK 재등록409 DUPLICATE_CODE | REQ-079, REQ-095, REQ-096 |
| 시스템 코드 | 기존 이름만 변경 | 값/사용/신규/삭제 혼합은409 SYSTEM_CODE_LOCKED, 이름도 롤백 | REQ-074, REQ-089 |
| 일반 값코드 | 기존 codeVal만 변경, 원/단위 변경 불가 | 다른 기존필드/삭제409 VALUE_CODE_FIELD_LOCKED | REQ-089 |
| 일반 운영기준 | 허용월에 기존 적용행 UPDATE 또는 새 적용행 INSERT | 다른월 유지; 현재/예정/지난은 현재월 비교로 계산하는 조회상태, 별도 state열 없음 | REQ-100, REQ-101, REQ-102 |
| CASH 두 키 | 기존 시작월 미마감일 때 현재 1행 값 UPDATE | 기존 시작월 CLOSE면 새월 요청도409; APLY_BGN_YM 및 마감자료 불변 | REQ-111, REQ-112, REQ-113, REQ-114 |
| 대응 | 행 없음→즉시 INSERT / 존재→확인 후 물리DELETE | NEW의 AFT JSON/DEL의 BFR JSON, 삭제 후에도 그룹별 이력 조회 | REQ-117, REQ-118, REQ-119 |
| 사용자 설정 | 미저장→명시저장; 저장→초기화 | 기본값과 같아도 최초 INSERT·stored=true; 삭제 후 stored=false·기본값, 타키 유지 | REQ-047, REQ-048, REQ-050 |
| 월 마감 | 전이 없음 — 실제 CLOSE/WORK를 조회만 | 작업중/마감/이관 표시; 월마감 쓰기 endpoint0개 | REQ-066, REQ-148 |

## Transaction·Revision·잠금 계약

코드 한 그룹 저장, 기준 한 요청, 대응 추가/다중 삭제는 각각 단일 트랜잭션이다. 한 항목 실패 시 데이터와 이력이 함께0건 변경이다. 키 오름차순 잠금, 그룹 저장은 대상 그룹과 소속 코드, 기준은 변경키 전체 적용이력 및 묶음 미변경키까지 포함한다(REQ-028, REQ-110). 그룹 생성은 그룹 테이블 직렬화 잠금으로 trim 후 동명이름/채번 경합을 보호한다. DB에 CODE_GRP_NM UK를 임의 추가해 원본 구조를 바꾸지 않는다(REQ-003, REQ-075).

불투명 revision은 기존 식별자·저장값·REG_DTTM/MOD_DTTM으로 계산한다. 그룹은 그룹+전체 코드, 기준키는 모든 적용월 이력을 포함한다. 양NULL시각이어도 값이 바뀌면 다른 revision, 한쪽NULL시각은 다르다. 잠금 후 최신 상태와 expectedRevision을 비교하며 후행은409 STALE_WRITE·DB/이력0건이다. 스키마 version열은 만들지 않는다(REQ-029, REQ-030).

revision 일치 후 실제 차이0이면200 changed=false·“바뀐 내용이 없습니다.”, 수정시각·이력0건이다. 변경은 commit 후 changed=true와 재조회 결과, 서버 캐시는 commit 후 무효화한다. 저장 응답 후2초 내 현재 브라우저 관련 값·선택목록·설정이 갱신되어야 한다. commit 후 재조회 실패는 재저장을 하지 않고 저장 성공/조회 실패를 구분한다(REQ-031, REQ-032, REQ-033, REQ-034).

설정 최초 expectedRevision=null이고 기존은 조회 토큰이다. 최초 기본값 명시저장도 INSERT하며 동시 최초 PK충돌은409 STALE_WRITE다. 설정 초기화 revision 전송 위치와 없는행 초기화 정책은 OQ-004의 미승인 API 제안이지 데이터 정책 확정이 아니다(REQ-048, REQ-050).

DB는 blocking transaction 경계이며 API는 동기 결과만 계약한다. 202/polling/callback/worker/R2DBC를 추가하지 않는다. 매핑 방식·lock SQL/annotation 등 프레임워크별 상세는 OQ-007 승인 전 확정하지 않는다(REQ-135, REQ-144).

## Validation Rule Matrix

문자/숫자는 정규화 후 검사하되 과길이 문자열/이력값을 자르거나 소수를 반올림/버림하지 않는다. DB 수치 type과 wire 십진문자열을 분리하고 Java BigDecimal로 비교한다. 아래 기준 위반 시 개별 필드 사유와 requestId를 반환한다.

| rule | field/operation | 조건 → 관찰 결과·필수 시험 | canonical_id |
| --- | --- | --- | --- |

| VR-013 문자열 입력 검증 | putPreference, createCodeGroup, saveGroupCodes, createCodeMapping, saveOperatingStandards | 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다. | REQ-013 |

| VR-014 그룹ID 정규화 | createCodeGroup | 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다. | REQ-014 |

| VR-015 코드 정규화 | saveGroupCodes | 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다. | REQ-015 |

| VR-016 명칭 경계와 식별자 불변 | createCodeGroup, saveGroupCodes | 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다. | REQ-016 |

| VR-017 정밀 십진값 범위 | saveGroupCodes, saveOperatingStandards | 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다. | REQ-017 |

| VR-018 숫자 입력 정리와 금지값 | saveGroupCodes, saveOperatingStandards | 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다. | REQ-018 |

| VR-019 소수 원값 보존 | saveGroupCodes, saveOperatingStandards | 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다. | REQ-019 |

| VR-020 JOB 월단가 정수 | saveGroupCodes | 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다. | REQ-020 |

| VR-021 실제 연월 검증 | getMonthClose, putPreference, saveOperatingStandards, getParameter | 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다. | REQ-021 |

| VR-022 Y/N·JSON 타입 | putPreference, saveGroupCodes | true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다. | REQ-022 |

| VR-023 빈 수당·기준값 구분 | saveGroupCodes, saveOperatingStandards | 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다. | REQ-023 |

| VR-026 처리자 주입 거부 | putPreference, deletePreference, createCodeGroup, saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards | 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다. | REQ-026 |

| VR-028 원자적 업무 변경 | createCodeGroup, saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards | 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다. | REQ-028 |

| VR-029 불투명 revision | putPreference, deletePreference, getGroupCodes, saveGroupCodes, deleteGroupCode, getCodeMappings, deleteCodeMappings, getOperatingStandards, saveOperatingStandards, getOperatingStandardHistory | 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다. | REQ-029 |

| VR-030 잠금 후 동시 수정 검출 | putPreference, deletePreference, saveGroupCodes, deleteGroupCode, deleteCodeMappings, saveOperatingStandards | 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다. | REQ-030 |

| VR-031 실제 변경 없는 저장 | putPreference, saveGroupCodes, saveOperatingStandards | 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다. | REQ-031 |

| VR-048 설정 명시 저장 | putPreference | 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. | REQ-048 |

| VR-049 손상 설정 격리 | getPreferences, putPreference | 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다. | REQ-049 |

| VR-050 개별 설정 초기화 | deletePreference | 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다. | REQ-050 |

| VR-051 즐겨찾기 편집 | getPreferences, putPreference | 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다. | REQ-051 |

| VR-052 최근 화면 자동 갱신 | getPreferences, putPreference | 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. | REQ-052 |

| VR-053 메뉴 접힘 기억 | getPreferences, putPreference | PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다. | REQ-053 |

| VR-054 목록 크기 설정 | getPreferences, putPreference | 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다. | REQ-054 |

| VR-055 밀도 설정 동기화 | getPreferences, putPreference | 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다. | REQ-055 |

| VR-056 표 열 설정 저장 | getPreferences, putPreference | 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다. | REQ-056 |

| VR-057 금액 단위 선택 기억 | getPreferences, putPreference | 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다. | REQ-057 |

| VR-058 설정 화면ID·표시 보호 | getPreferences, putPreference | 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다. | REQ-058 |

| VR-059 UI-27 열 정의 제한 | getPreferences, putPreference | codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다. | REQ-059 |

| VR-061 기간 설정 타입·범위 검사 | getPreferences, putPreference | NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다. | REQ-061 |

| VR-064 기간 범위와 운영 전 상태 | getPeriodContext | YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다. | REQ-064 |

| VR-066 실제 월 마감 조회 | getMonthClose | CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다. | REQ-066 |

| VR-068 표 페이지 계약 | getGroupCodes, getCodeMappings, getOperatingStandards, getCommonCodeHistory | page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다. | REQ-068 |

| VR-073 부분 변경·중복 코드 검사 | saveGroupCodes | 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다. | REQ-073 |

| VR-074 시스템 코드 변경 제한 | saveGroupCodes, deleteGroupCode | CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다. | REQ-074 |

| VR-075 그룹 중복과 채번 경합 | createCodeGroup | 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다. | REQ-075 |

| VR-079 그룹 내 코드 PK 재사용 금지 | saveGroupCodes, deleteGroupCode | DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다. | REQ-079 |

| VR-080 신규 코드 기본값·정렬 채번 | saveGroupCodes | 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다. | REQ-080 |

| VR-081 정렬 순번 한계 | saveGroupCodes | max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다. | REQ-081 |

| VR-082 코드명 중복 허용 | saveGroupCodes | 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다. | REQ-082 |

| VR-083 그룹별 추가값 필수·길이 | saveGroupCodes | 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. | REQ-083 |

| VR-084 PROJ_STEP 진척 정수 | saveGroupCodes | 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다. | REQ-084 |

| VR-085 BANK 코드와 RSM 라벨 | saveGroupCodes | 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다. | REQ-085 |

| VR-086 RSM_LABEL 허용값 | saveGroupCodes | name 입력은 NAME으로 저장; 목록 외 문자열은400이다. | REQ-086 |

| VR-087 CSV 정규화·참조 | saveGroupCodes | 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다. | REQ-087 |

| VR-089 값 코드 잠금 | saveGroupCodes, deleteGroupCode | 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다. | REQ-089 |

| VR-090 수당 신규 코드 저장 | saveGroupCodes | 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다. | REQ-090 |

| VR-094 삭제 참조 차단 | deleteGroupCode | 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다. | REQ-094 |

| VR-095 일반 코드 논리 삭제 | deleteGroupCode | 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다. | REQ-095 |

| VR-097 고정 참조 가드 | deleteGroupCode | 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다. | REQ-097 |

| VR-098 삭제 직전 재검사 | deleteGroupCode | 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다. | REQ-098 |

| VR-101 일반 기준 적용 최소월 | getOperatingStandards, saveOperatingStandards | 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다. | REQ-101 |

| VR-102 기준 월별 추가·덮어쓰기 | saveOperatingStandards | 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다. | REQ-102 |

| VR-103 기준 변경 키 중복 거부 | saveOperatingStandards | 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다. | REQ-103 |

| VR-105 DUN 순서 검증 | saveOperatingStandards | D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다. | REQ-105 |

| VR-106 확률 범위 검증 | saveOperatingStandards | 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다. | REQ-106 |

| VR-107 AI 점수 합계 검증 | saveOperatingStandards | 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다. | REQ-107 |

| VR-108 철수 통보일 정수 | saveOperatingStandards | 1과99,999,999,999 허용; 0 또는 소수는400이다. | REQ-108 |

| VR-109 자금 시작 연월 표현 | getOperatingStandards, saveOperatingStandards | 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다. | REQ-109 |

| VR-110 기준 전체 영향구간 검사 | saveOperatingStandards | 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다. | REQ-110 |

| VR-111 자금 두 키 즉시 갱신 | getOperatingStandards, saveOperatingStandards | 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다. | REQ-111 |

| VR-113 기존 시작월 마감 잠금 | getOperatingStandards, saveOperatingStandards | 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다. | REQ-113 |

| VR-114 자금 동시 잠금·후속 갱신 | saveOperatingStandards | 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다. | REQ-114 |

| VR-116 대응 입력·중복 검사 | createCodeMapping | 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. | REQ-116 |

| VR-118 대응 등록·삭제 이력 | createCodeMapping, deleteCodeMappings, getCommonCodeHistory | NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다. | REQ-118 |

| VR-119 대응 다중 물리 삭제 | deleteCodeMappings | 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다. | REQ-119 |

| VR-120 대응 삭제 충돌 | deleteCodeMappings | 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다. | REQ-120 |

| VR-123 변경 유형과 과거 이름 추적 | saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards, getCommonCodeHistory | 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다. | REQ-123 |

| VR-124 이력 필터 도메인 | getCommonCodeHistory | 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다. | REQ-124 |

| VR-125 이력 키·전후값 무손실 | saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards, getCommonCodeHistory | 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다. | REQ-125 |

| VR-128 기준 미존재 거부 | getParameter | 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다. | REQ-128 |

| VR-129 API 필드와 서버 필드 보호 | getHealth, getCurrentUser, getMenus, getPeriodContext, getMonthClose, getPreferences, putPreference, deletePreference, getCodeGroups, createCodeGroup, getGroupCodes, saveGroupCodes, deleteGroupCode, getCodes, getCodeMappings, createCodeMapping, deleteCodeMappings, getOperatingStandards, saveOperatingStandards, getOperatingStandardHistory, getParameter, getCommonCodeHistory | SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다. | REQ-129 |

| VR-130 공통 오류 반환과 비밀 보호 | getHealth, getCurrentUser, getMenus, getPeriodContext, getMonthClose, getPreferences, putPreference, deletePreference, getCodeGroups, createCodeGroup, getGroupCodes, saveGroupCodes, deleteGroupCode, getCodes, getCodeMappings, createCodeMapping, deleteCodeMappings, getOperatingStandards, saveOperatingStandards, getOperatingStandardHistory, getParameter, getCommonCodeHistory | 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다. | REQ-130 |

| VR-131 변경 이력 무기한 보존 | getCommonCodeHistory | 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. | REQ-131 |

### 그룹별 추가값·정밀도 상세

| 그룹/필드 | 원문 검사·저장 | canonical_id |
| --- | --- | --- |
| JOB.ADD_VAL1 | 관련 JOB 코드 CSV; JOB 자기포함 허용/필수 아님; 같은 저장 신규 JOB의 최종상태 포함 | REQ-087 |
| JOB.ADD_VAL2 | 공백·쉼표·끝 원 정리 후 0~999999999999999 정수문자열; 음수/소수/상한 초과400 | REQ-020 |
| SKILL.ADD_VAL1 | 필요한 JOB 코드 CSV | REQ-083, REQ-087 |
| PROJ_STEP.ADD_VAL1/ADD_VAL2 | ADD_VAL1은0~100 정수; ADD_VAL2는 CTRT_TYPE 코드 CSV | REQ-084 |
| BANK.ADD_VAL1 | 정확한 숫자3자리; 앞0 보존 | REQ-085 |
| RSM_LABEL.ADD_VAL1 | NAME/TEL/MAIL/ADDR/SKILL/WANT/FROM/VNDR 중1개; 대문자 정규화 | REQ-086 |
| RSM_JOB_KW.ADD_VAL1 | JOB 코드 CSV | REQ-083, REQ-087 |
| 다른 그룹 ADD_VAL 및 ADD_VAL3 | 다른 그룹 추가값 NULL, ADD_VAL3 이번 미사용; 알려진 다른 그룹 필드 주입400 | REQ-083 |

모든 CSV는 쉼표분리→trim/대문자→빈항목 거부→첫 등장 순서 중복제거→쉼표결합이며 최소1개·정규화 후100자 이하다. 미존재/삭제 코드 참조 금지, 신규 참조는 active만, 기존 중지참조 유지 허용이다. CODE_VAL/STD_VAL은0~99999999999.9999·정수11자리/소수4자리이며 숫자문자열/BigDecimal, 임의 %100 상한은 추가하지 않는다. 수당 신규/빈값0 허용·일반 기준 빈값400이다(REQ-017, REQ-018, REQ-019, REQ-023, REQ-083, REQ-087).

### 기준 묶음·적용월 상세

DUN_D1/2/3는 D1≥1·D1<D2<D3, EXT_PRBL_H/M/L/NULL 및 STEP_PRBL_1/2/3/4는 각0~100, AI_SCORE_JOB/SKILL/CUST/GRADE/PERF/COST/AVAIL/LOC는 각0~100·합계정확히100이다. WTHDR_NOTI_D는1~99999999999 정수다. BILL_MISS_RT/MIN은 묶음표시이지만 원문에 없는 추가 상한/상호규칙을 만들지 않는다(REQ-104, REQ-105, REQ-106, REQ-107, REQ-108).

일반 기준 최소월=max(CASH_BASE_YM, 마지막 CLOSE월+1), CLOSE가 없으면 CASH_BASE_YM이다. 기본월=max(현재월,최소월)이며 전월 저장409 CLOSED_MONTH다. 동일 적용월은 덮어쓸 월/전후 확인 후 UPDATE, 없으면 INSERT, 다른월 보존이다. 변경키 중복400, 미변경칸 시각/이력 갱신0건이다. 요청 적용월뿐 아니라 영향을 받는 구간과 이후 예정기준의 모든 경계월을 미변경키와 합쳐 검사하고 어느 경계라도 합/순서/범위 위반이면400·전체롤백이다(REQ-101, REQ-102, REQ-103, REQ-110).

CASH_BASE_YM/CASH_BASE_AMT는 각1행, 일반 이력 요청과 혼합400, effectiveMonth 없이 별도 요청의 UPDATE다. 두행을 함께 잠그고 변경 전 CASH_BASE_YM이 CLOSE인지 재검사한다. 마감이면 두키 모두409·새 시작월로 잠금 우회 불가, 미마감이면 원래 APLY_BGN_YM을 보존하고 실제 차이만 UPD 이력·period-context/기준조회 갱신이다. 마감/프로젝트/정산자료 수정과 자금계산은0건이다(REQ-109, REQ-111, REQ-112, REQ-113, REQ-114).

### PREF_VAL JSON 데이터 계약

물리열은 VARCHAR(2000)의 직렬화 JSON이며 jsonb로 바꾸지 않는다. USER_ID=admin, PREF_KEY는 별표 설정 또는 등록 screenId다. 서버 반환 value는 parse된 JSON이고 revision/stored는 저장열을 추가하지 않는 투영이다. 설정 오류가 있으면 해당 항목 오류와 revision만 격리하고 기본값 임시 표시, 자동 DELETE 금지다(REQ-022, REQ-047, REQ-049).

| PREF_TYPE_CD | PREF_KEY | 값·기본값·허용 조건 | canonical_id |
| --- | --- | --- | --- |
| FAV | * | 등록 screenId 배열, 기본[], 중복없이 최대10, 순서저장·11번째 거부 | REQ-051, REQ-058 |
| RECENT | * | 등록 screenId 배열, 기본[], 활성화순 최대5·재방문 선두·중복제거·초과뒤제거 | REQ-052, REQ-058 |
| MENU_FOLD | * | JSON boolean만; 미저장 API value=null, PC=false/태블릿=true를 화면에서 적용, 모바일 슬라이드는 저장안함 | REQ-053 |
| PAGE_SIZE | * | JSON 정수20/50/100만, 기본50, 문자열50 거부 | REQ-054 |
| DENSE | * | 넓게/좁게, 기본넓게, 서버 성공 후 모든 열린 목록/사용자 메뉴 동일적용 | REQ-055, REQ-060 |
| COLS | screenId | viewId별 order 전체열 순열/hidden 선택열 부분집합; 기본전체/기본순서, 필수숨김·중복·미등록열 거부 | REQ-056, REQ-059 |
| AMT_UNIT | * | 원/천원/백만원, 기본천원; 선택저장복원만, 업무금액변환 제외 | REQ-057 |
| PERIOD | screenId | YEAR(type,value), YEAR_MONTH(type,value), YEAR_CUM(type,year,month); 실제 현재일/허용범위로 기본결정; NONE쓰기400 | REQ-061, REQ-063, REQ-064 |

COLS는 UI-27의 codes/valueCodes/mappings/standards/history만 현재 허용한다. codes order=code,name,addVal1,addVal2,sortOrder,useYn; valueCodes=code,name,codeVal,unitNm; mappings=sourceText,groupId,code; standards=key,name,value,unitNm,effectiveMonth,description; history=occurredAt,target,groupId,entityKey,type,item,before,after,actor다. 필수열은 codes/valueCodes의 code/name, mappings의 sourceText/groupId/code, standards의 key/name/value, history의 occurredAt/target이다. 선택 체크/작업열은 제외한다. codes 전체 order를 보존하되 현재 모드/그룹에 적용되는 열만 표시한다. 다른 화면은 표 정의 승인 후에만 COLS를 허용한다(REQ-056, REQ-059).

YEAR/YEAR_MONTH는 실제 0001-01~9999-12 기반 범위, 누계는 year와 month의 연도 일치 검증이다. YEAR 범위=시작연도~현재연도+1, YEAR_MONTH=시작월~현재월, 누계=시작연도~현재연도(UI-07/08/09만 시작연도-1부터), 누계 기준월은 해당 연도1~12월/현재연도 현재월까지다. 범위역전은 운영시작전·비활성·저장0회, 저장기간이 새 범위밖이면 안내/기본표시만 하고 DB를 조용히 바꾸지 않는다(REQ-021, REQ-061, REQ-064).

## 감사·삭제·보존·보안 분류

| 분류 | 원문 근거·설계 계약 | canonical_id |
| --- | --- | --- |
| 처리자·시각 | REG_USER_ID VARCHAR(30) NOT NULL=admin; REG_DTTM DEFAULT CURRENT_TIMESTAMP, MOD_USER_ID/MOD_DTTM nullable 서버기록; Asia/Seoul 일관성 | REQ-011, REQ-026, REQ-134 |
| 일반코드 상태 | USE_YN 변경 STOP/RESUME, DEL_YN N→Y DEL; 다른 업무 저장코드 변경0건 | REQ-092, REQ-093, REQ-095, REQ-096 |
| 대상키 | TRGT_TBL_NM 실제명, 그룹 groupId / 코드 groupId:code / 대응 groupId:mapId / 기준 key:YYYYMM; 정확한 접두 구분자로 groupId 필터 | REQ-125 |
| 변경전후 | UPD 컬럼별 전후, codeName은 CHG_ITEM_NM=CODE_NM; NEW 항목 등록·BFR=NULL/AFT=식별자·이름·값 | REQ-123, REQ-125 |
| 대응 JSON | NEW는 sourceText/groupId/code/codeName JSON을 AFT_VAL, DEL은 BFR_VAL; 반대NULL;500자 초과 무절단 거부 | REQ-118, REQ-125 |
| 보존 | TB_SY_CHG_HIST 무기한 유지, 수정/삭제 API 및 기간삭제 작업0개; 그 밖 테이블 보존기간은 OQ-DATA-004 | REQ-131, REQ-155 |
| 비밀정보 | 초기 admin 비밀번호/메일을 프론트에 노출하지 않음; DB 접속값 환경설정; SQL/스택/비밀값을 오류·로그로 노출하지 않음; 이 문서에 비밀 seed값 기록안함 | REQ-130, REQ-133 |
| 민감칸 | 이번 실제권한/역할별 가림0건; 이를 공개배포 허가로 해석하지 않음. 추가 보안등급/마스킹/암호화 정책은 OQ-DATA-004 | REQ-027, REQ-143 |
| 물리 민감열 | PWD_HASH/MFA_CODE_HASH/EMAIL_ADDR 및 *_ENC 열의 정의만 보존; Common DTO에 storage열 복사금지; 이 단계에 해당 비밀 입력/사용자 관리기능 만들지 않음 | REQ-003, REQ-025, REQ-130 |

임시 관리자 환경은 로컬/접근 제한 사설 개발·검증으로만 운영한다. 로그인·비밀번호 변경·메일확인·token/newPassword 입력 흐름은 이 요청 범위에 없으므로 API나 데이터 모델을 새로 만들지 않는다. 원본 DDL의 인증용 컬럼을 보존하는 것은 실제 인증 구현 허가가 아니다(REQ-027, REQ-143).

## 첨부·파일 메타데이터 경계

전체 DDL 중 TB_SY_UPLD.FILE_NM VARCHAR(200) NOT NULL과 FILE_SIZE NUMERIC(12) NOT NULL은 위 전수 필드 표에 그대로 보존한다. UPLD_ID 및 TB_SY_UPLD_ERR FK도 설치한다. 업로드 이력의 다른 열도 물리 보존하되 이번 요청은 엑셀 파서/파일 업로드/다운로드·근태 업로드를 제외하므로 해당 API·화면·파일 저장소·S3 경로·MIME/만료필드를 만들지 않는다. 파일 실체 보관·크기상한·콘텐츠 검사·보존기간은 원문 미정(OQ-DATA-003)이지 없음/무제한 승인이 아니다(REQ-003, REQ-149, REQ-150).

## Seed Data Contract

### 설치 seed — 제품 초기값과 별도 시험 fixture 구분

원천은 example/project_data.sql 전체(V2) 및 project_ddl.sql 마지막 HQ INSERT(V1)이다. 원문의 수량/알려진 값만 확정하고 비밀 credential·인물·가짜 업무금액을 합성하지 않는다. 아래는 초기 적재 기대 결과이며 실제 SQL data 실행·DB 관측 완료 표가 아니다. HQ를 V2에 중복 적재하지 않는다.

| seed ID | entity/table | 최소 식별·필드와 기대 수량 | 목적·소비 시험/작업 | canonical_id |
| --- | --- | --- | --- | --- |
| SEED-GROUPS | TB_SY_CODE_GRP | 전체64, SYS_CODE_YN=Y 44; ID/명칭/정렬은 SQL 원천 | UI-27 그룹 목록·T007/T033 | REQ-004, REQ-069 |
| SEED-CODES | TB_SY_CODE | 전체391; PK/명칭/수치는 SQL 그대로 | 그룹/공통코드 조회·T007/T033 | REQ-004, REQ-070 |
| SEED-ALWNC | TB_SY_CODE | ALWNC의 ALLOW_TEAM=200000원, ALLOW_GROUP=400000원, INS_RATE=9.4%, SEV_RATE=8.33%, TEAM_FEE/PEXP_FEE=50000원 | getCodes 정밀 문자열·수당회귀·T039/T041 | REQ-091 |
| SEED-STANDARDS | TB_SY_OPER_STD | 43키, 적용월/이름/값/단위/설명 SQL 원천 | 비페이지43키/현재값·T007/T045 | REQ-004, REQ-099 |
| SEED-CASH | TB_SY_OPER_STD | CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000, 각1행 | 기간범위·기초값·T007/T051 | REQ-005, REQ-109, REQ-111 |
| SEED-ROLES | TB_SY_ROLE | 5행; 역할목록을 추정 추가하지 않음 | 초기값 계수·T007 | REQ-004 |
| SEED-MENUS | TB_SY_MENU | 40행=10묶음+30화면, PC_ONLY_YN 지정; UI-20 없음 | DB메뉴/라우트·T021 | REQ-004, REQ-035 |
| SEED-ROLE-MENU | TB_SY_ROLE_MENU_AUTH | 150행, SQL 매핑 그대로 | 구조/초기값·T007; 실제 권한필터 아님 | REQ-004, REQ-027 |
| SEED-ADMIN | TB_SY_USER·TB_SY_USER_ROLE | admin 및 ADMIN 연결 각1행; 비밀번호/메일값 문서화·프론트노출 안함 | CurrentUserProvider·T010 | REQ-004, REQ-025 |
| SEED-HQ | TB_BS_PART | PART_ID=HQ 한행, V1 DDL에서만 적재 | 예약참조 무결성·T007 | REQ-004 |
| SEED-CALC | TB_SY_CALC_RULE | 94행·11컬럼; TEXT 그대로 | 전체 적재/엔진없음·T007 | REQ-003, REQ-005, REQ-152 |
| SEED-EMPTY-MAP | TB_SY_CODE_MAP | 0행 | 대응빈목록·T054 | REQ-004, REQ-115 |
| SEED-EMPTY-HIST | TB_SY_CHG_HIST | 0행 | 변경전 이력0·T007 | REQ-004 |
| SEED-EMPTY-PREF | TB_SY_USER_PREF | 0행; 응답기본값은 seed INSERT가 아님 | stored=false/PAGE_SIZE50/MENU_FOLD null·T024 | REQ-004, REQ-047 |
| SEED-EMPTY-AREA | TB_SY_ADM_AREA·TB_SY_ADM_HDONG | 0행, 구조/제약만 | 주소파일적재없음·T007 | REQ-002, REQ-151 |
| SEED-NO-CLOSE | TB_CL_CLOSE | 임의 월마감 생성0건 | 행없는월 작업중·T030 | REQ-005, REQ-066 |

### 독립 테스트 DB 전용 fixture

업무 seed로 적재하지 않는다. 구체 인물/업무금액을 UI 배열로 하드코딩하지 않으며 실제 fixture 작성은 승인된 SQL/테스트 runner 확인 후 tasks.md 작업에서 수행한다. 아래 경계는 source-backed 시험 조건이며 새 요구/영구 seed가 아니다.

| fixture ID | entity/table·최소 조건 | 실행·관찰 증거 | canonical_id |
| --- | --- | --- | --- |
| FX-GROUP-RACE | TB_SY_CODE_GRP 같은 trim 이름 동시요청 | createCodeGroup 선행 성공/후행409 DUPLICATE_GROUP_NAME, 중복0 | REQ-075 |
| FX-SYS | TB_SY_CODE_GRP SYS=Y 및 TB_SY_CODE CTRT_TYPE/TK | 이름만 저장 후 재조회; 이름+useYn 혼합409·데이터/이력0 | REQ-074 |
| FX-SYS-VALUE | SYS=Y/VAL=Y 그룹과 기존코드 | 기존 이름 허용/값409 SYSTEM_CODE_LOCKED; 일반 값그룹 단위409 VALUE_CODE_FIELD_LOCKED | REQ-089 |
| FX-PRECISION | TB_SY_CODE.CODE_VAL·TB_SY_OPER_STD.STD_VAL | 4소수 정확저장;5소수/정수12자리400·반올림0 | REQ-017, REQ-019 |
| FX-REVISION | 동일그룹/기준키의 두클라이언트, NULL시각/값변경 | 후행409·후행 본체/이력0; NULL시각 같아도 revision다름 | REQ-029, REQ-030 |
| FX-NO-CHANGE | 기존행·유효revision·동일값 | changed=false·시각/이력불변 | REQ-031 |
| FX-REFERENCES | 위 고정참조표의 각 업무열/CSV/과거명/공통대응 1건씩 | deleteGroupCode409 CODE_IN_USE·유형/건수; 자기NEW만이면 삭제허용 | REQ-094, REQ-097, REQ-098 |
| FX-DELETE | 미참조 일반코드 및 삭제PK | DEL_YN=Y·물리행·DEL 유지/PK재등록409 | REQ-079, REQ-095, REQ-096 |
| FX-MAP-RACE | 대응2행 중 하나 변경/누락 | deleteCodeMappings409·두행 보존·DEL0; 정상은 두행없음/이전JSON | REQ-118, REQ-119, REQ-120 |
| FX-STD-BOUNDARY | 기준43키의 과거/현재/예정, AI8합99/100/101·DUN역전 | saveOperatingStandards 합100성공/99·101·예정경계위반400·전체불변 | REQ-105, REQ-107, REQ-110 |
| FX-CASH-CLOSE | 기존 CASH_BASE_YM을 CLOSE로 설정한 시험 DB | 새 시작월로도 두키409·원래행/적용월유지, 미마감동일행UPDATE | REQ-111, REQ-113, REQ-114 |
| FX-PREF | 설정0행·기존행·손상JSON·동시최초요청 | 기본값명시INSERT/stored=true, 후행409, 손상행격리/다른키보존 | REQ-048, REQ-049, REQ-050 |
| FX-MENU | DB메뉴 자기참조/순환/중지부모/깊이불일치/중복화면 | getMenus 정상부분·진단, 자손제외·DB수리0 | REQ-037 |
| FX-PERIOD | 시작월/서버현재일·WORK/CLOSE/행없음·역전 | 실제상태·유형범위, NONE쓰기400·역전저장0 | REQ-061, REQ-064, REQ-066 |

## Screen/API Reference

이 배치에는 ui-design.md를 만들지 않는다. 상류의 실제 UI-27와 공통 설정/기간/메뉴만 참조하며 API wire schema를 복제하지 않는다. 화면 없는 설치테이블에 가짜 screenId를 부여하지 않는다.

| primary entity/table | 화면·route | 조회 operationId | 변경 operationId | seed·규칙·세로 작업 | canonical_id |
| --- | --- | --- | --- | --- | --- |
| TB_SY_MENU | 공통 Sidebar·등록 /{screenId}, 기본 /UI-16 | getMenus | N/A — 메뉴쓰기없음 | SEED-MENUS/FX-MENU, T021~T023 | REQ-035, REQ-037 |
| TB_SY_USER | 공통 임시관리자 표시, 별도 화면없음 | getCurrentUser | N/A — 사용자쓰기없음 | SEED-ADMIN, T010 | REQ-025, REQ-027 |
| TB_SY_USER_PREF | 사용자메뉴 화면설정·TitleBar/Sidebar·각 화면기간, 독립route없음 | getPreferences | putPreference/deletePreference | SEED-EMPTY-PREF/FX-PREF, VR-048/VR-061, T024~T032 | REQ-047, REQ-048, REQ-050, REQ-051, REQ-061 |
| TB_SY_CODE_GRP | UI-27 /UI-27 코드 | getCodeGroups | createCodeGroup | SEED-GROUPS/FX-GROUP-RACE, VR-075, T033~T038 | REQ-069, REQ-075, REQ-076 |
| TB_SY_CODE | UI-27 /UI-27 코드·값코드 | getGroupCodes/getCodes | saveGroupCodes/deleteGroupCode | SEED-CODES/ALWNC/FX-SYS/FX-REFERENCES, VR-074/VR-089, T033~T044 | REQ-070, REQ-071, REQ-089, REQ-095, REQ-126 |
| TB_SY_OPER_STD | UI-27 /UI-27 운영기준·적용이력·공통 기간 | getOperatingStandards/getOperatingStandardHistory/getParameter/getPeriodContext | saveOperatingStandards | SEED-STANDARDS/CASH/FX-STD-BOUNDARY/FX-CASH-CLOSE, VR-110/VR-113, T045~T053/T066 | REQ-099, REQ-100, REQ-102, REQ-111, REQ-127 |
| TB_CL_CLOSE | 공통 ScreenPeriodBar·/{screenId} 기간유형 지원화면 | getMonthClose/getPeriodContext | N/A — 마감쓰기제외 | SEED-NO-CLOSE/FX-PERIOD, T030~T032 | REQ-063, REQ-066, REQ-148 |
| TB_SY_CODE_MAP | UI-27 /UI-27 엑셀표기대응 | getCodeMappings | createCodeMapping/deleteCodeMappings | SEED-EMPTY-MAP/FX-MAP-RACE, VR-116/VR-120, T054~T062 | REQ-115, REQ-117, REQ-119, REQ-120 |
| TB_SY_CHG_HIST | UI-27 /UI-27 변경이력 | getCommonCodeHistory | N/A — 본체연산의 서버append만 | SEED-EMPTY-HIST, VR-123/VR-125/VR-131, T063~T065 | REQ-122, REQ-123, REQ-125, REQ-131, REQ-155 |

T번호 범위는 상류 작업묶음의 문서 참조일 뿐 요구번호 범위를 대신하지 않는다. 이 문서와 OpenAPI의 canonical_id는 각각 원장행의 고정 의미로 연결한다.

## Persistence Assumptions·설치 경계

V1__full_schema.sql에 전체 DDL/HQ 예약행, V2__base_data.sql에 전체 초기값을 지정 backend/src/main/resources/db/migration/ 순서로 적용한다. 적용된 파일 수정/재실행 금지·재시작 중복0건이다. data의 최상위 BEGIN2/COMMIT1만 실행사본에서 제거하고 계산규칙 TEXT는 치환하지 않는다. 원본/실행본 diff·사용한 revision/해시를 기록하며 이 문서는 실행 SQL을 제공하지 않는다(REQ-001, REQ-006, REQ-007, REQ-136).

실패는 해당 트랜잭션 롤백·지정 개발 DB에서만 수정검증한다. 임의 DROP/TRUNCATE·자동 baseline·사용자 다른 DB 초기화 금지다. 설치 이후 업무 쓰기는 위6테이블뿐이고 수당/기준/대응 변경으로 프로젝트팀비/경비/정산·마감자료를 바꾸지 않는다(REQ-008, REQ-009, REQ-010, REQ-091, REQ-114, REQ-121).

### 미결·인계 금지 항목

| ID | 미확인 사실 | 금지되는 추론·해소 전 Code Agent 행동 |
| --- | --- | --- |
| OQ-001 | Java/Boot 본문버전과 고정BOM 충돌 | 버전을 조용히 선택하지 말고 상류 승인 후 의존성 확정 |
| OQ-002 | DDL 읽기대조는 이번 완료, data 전체revision/실행본diff 및 DB실측 미완료 | 원본헤더 수량을 실제카탈로그로 오인하지 말고 T002/T007로 전수 대조 |
| OQ-003 | 오류봉투 내부 위치의 상류 승인 없음 | OpenAPI의 루트배치 제안은 제안으로 유지, 직렬화확정 완료선언 금지 |
| OQ-004 | DELETE revision 전달위치·없는설정초기화·저장후재조회범위 제안 미승인 | bodyless204 등의 API제안을 원문 확정정책으로 승격하지 않음 |
| OQ-006 | 실제 current request_key 없음·질문전달 불가 | meta.request_key=null은 차단표시; BASIC-129/디렉터리명 추정 금지, 호스트값 수신 후 투영 갱신 |
| OQ-007 | 원장/승인spec JPA와 plan exact mybatis 상충 | 원장 의미 불변, plan 정본블록 유지, 매핑/의존성/validate 승인 전 구현보류 |
| OQ-DATA-003 | 파일실체보관·콘텐츠검사·용량상한·보존기간 미정 | 이번 업로드/다운로드 제외를 넘어 파일API/저장열/정책 만들지 않음 |
| OQ-DATA-004 | 변경이력 이외 보존기간·추가 보안등급/암호화/마스킹 정책 미정 | 없음/무기한/자동삭제/비활성으로 단정하지 말고 설치열보존·비밀비노출·사설환경 제약만 적용 |

## 공통 계약 참조

아래는 승인 plan.md의 technology_stack/version_bom/required_outputs 값과 동일하다. 독립적인 실행·영속성 정본을 만들지 않으며 plan.md의 exact marker JSON을 참조한다. REQ-138/REQ-139/REQ-140의 고정값과 REQ-132/REQ-135/REQ-144의 미해결충돌은 동시에 가시적으로 유지한다.

```json
{
  "technology_stack": {
    "Frontend": "React + TypeScript + Vite",
    "Backend": "Java 17 + Spring Boot",
    "Database": "PostgreSQL",
    "Infrastructure": {
      "runtime": "docker",
      "healthcheck_required": true
    }
  },
  "version_bom": {
    "spring_boot": "3.3.x",
    "node": "20.x",
    "vite": "5.x",
    "react": "18.x",
    "postgres": "16.x"
  },
  "required_outputs": {
    "backend_dir": "backend",
    "frontend_dir": "frontend",
    "compose_file": "infra/docker-compose.yml",
    "health_endpoint": "/api/health"
  }
}
```

실행·영속성 의미상 정본: plan.md의 aiops:execution-persistence-contract marker 블록. resolved/imperative/mybatis/blocking/explicit/conflicts=[]라는 블록값을 문서 전체 build-ready로 해석하지 않는다. REQ-144의 JPA 의미를 새 원장/부록으로 MyBatis 의미로 바꾸지 않는다. 차단 해소는 권한 있는 상류 정합화 승인으로만 가능하다.

## 요구 회계

covered는 이 문서 안의 실제 물리테이블/필드/불변성/검증 계약을 지정한 상태이지 실행 합격이 아니다. 화면·환경·범위제외 요구도 개별행으로 책임과 검증위치를 보존한다. 모든156개를 한행씩 기록한다.

| canonical_id | status | evidence |
| --- | --- | --- |

| REQ-001 | covered | Entity Registry·전수필드표·68테이블 — 전체 SQL 원천 보존; HQ 예약 파트를 포함한 DDL 전체 및 data 전체가 실행본과 대조된다. |

| REQ-002 | covered | TB_SY_ADM_AREA·TB_SY_ADM_HDONG — 행정구역 빈 테이블; 설치 후 행정구역 테이블 데이터 건수 0행이다. |

| REQ-003 | covered | Entity Registry·전수필드표·FK/키/시퀀스목록 — 전체 DB 구조 보존; 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK 및 TB_SY_CALC_RULE 11컬럼이 일치한다. |

| REQ-004 | covered | Seed Data Contract·TB_SY_CODE_GRP/TB_SY_CODE/TB_SY_OPER_STD — 기본 초기값 적재; 그룹64·시스템44·코드391·운영기준43·역할5·메뉴40·역할메뉴150·관리자/연결 각1·계산규칙94·HQ1행이며 세 빈 테이블은 0행이다. |

| REQ-005 | covered | TB_SY_CALC_RULE·TB_SY_OPER_STD — 초기 자금 기준과 계산규칙; CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 임의 월 마감 기록 0행이다. |

| REQ-006 | data-constraint | Persistence Assumptions·REQ-006 — SQL 실행사본 래퍼만 제거; 실제 revision/diff 검증은 research/T002·T006 인계 |

| REQ-007 | covered | Persistence Assumptions·V1/V2 — 마이그레이션 불변·멱등성; 재시작 후 초기값 중복 0건, 미적용 파일은 원본 대조 결과를 갖는다. |

| REQ-008 | covered | Persistence Assumptions·설치롤백 — 설치 실패와 구조 은폐 금지; 실패 fixture는 해당 트랜잭션을 롤백하며 사용자 보유 DB 초기화 0건이다. |

| REQ-009 | covered | Entity/Table Contract·구조차이 — 구조 차이 보고; 이름/타입 불일치 fixture에서 임의 스키마 수정 없이 불일치 목록이 보고된다. |

| REQ-010 | covered | Entity Registry·설치후6쓰기 — 설치 후 쓰기 경계; 수당/기준 변경 후 프로젝트 팀비·경비·정산금액 변경 0건이다. |

| REQ-011 | covered | TB_SY_CHG_HIST·핵심6테이블감사열 — 서버 감사 필드와 연월 저장; REG_USER_ID=admin, REG_DTTM 기록; 화면/API YYYY-MM이 DB YYYYMM으로 저장된다. |

| REQ-012 | screen-only | 상류 소유 global-ui — 공통 컴포넌트 실제 재사용; 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-013 | data-constraint | Validation Rule Matrix·문자열 입력 검증 — 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-014 | data-constraint | Validation Rule Matrix·그룹ID 정규화 — 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-015 | data-constraint | Validation Rule Matrix·코드 정규화 — 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-016 | data-constraint | Validation Rule Matrix·명칭 경계와 식별자 불변 — 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-017 | covered | TB_SY_CODE.CODE_VAL·TB_SY_OPER_STD.STD_VAL — 정밀 십진값 범위; 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다. |

| REQ-018 | data-constraint | Validation Rule Matrix·숫자 입력 정리와 금지값 — 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-019 | covered | TB_SY_CODE.CODE_VAL·TB_SY_OPER_STD.STD_VAL — 소수 원값 보존; 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다. |

| REQ-020 | covered | TB_SY_CODE.ADD_VAL2 — JOB 월단가 정수; 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다. |

| REQ-021 | data-constraint | Validation Rule Matrix·실제 연월 검증 — 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-022 | covered | TB_SY_USER_PREF.PREF_VAL — Y/N·JSON 타입; true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다. |

| REQ-023 | data-constraint | Validation Rule Matrix·빈 수당·기준값 구분 — 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-024 | screen-only | 상류 소유 global-ui — 공통 표시 함수; 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’; 표시 전후 저장값은 같다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-025 | screen-only | 상류 소유 current-user — 임시 사용자 컨텍스트; GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-026 | covered | 핵심6테이블.REG_USER_ID/MOD_USER_ID — 처리자 주입 거부; 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다. |

| REQ-027 | screen-only | 상류 소유 current-user — 인증·권한 유예; /UI-27 직접 조회/저장 시 인증 리다이렉트·401·역할별403은 0건이다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-028 | covered | Transaction·Revision·잠금 계약·핵심6테이블 — 원자적 업무 변경; 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다. |

| REQ-029 | covered | TB_SY_CODE·TB_SY_OPER_STD revision 투영 — 불투명 revision; 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다. |

| REQ-030 | covered | TB_SY_CODE·TB_SY_OPER_STD 잠금/revision — 잠금 후 동시 수정 검출; 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다. |

| REQ-031 | covered | TB_SY_CODE·TB_SY_OPER_STD no-op — 실제 변경 없는 저장; 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다. |

| REQ-032 | screen-only | 상류 소유 cache-service — commit 후 캐시 최신화; 저장 브라우저 2초 내 최신값; 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-033 | screen-only | 상류 소유 global-ui — 한국어 오류와 재조회 실패 구분; 500/연결 실패는 오류 띠·재시도; commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-034 | screen-only | 상류 소유 global-ui — 중복 클릭·자동 재저장 금지; 처리 중 반복 클릭의 추가 요청0건; 실패 후 입력 유지·자동 재저장0회다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-035 | screen-only | 상류 소유 US1 — DB 메뉴 구성·정렬; 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-036 | screen-only | 상류 소유 US1 — 메뉴 펼침·접힘 탐색; 부모 이름은 화면 이동, 펼침 아이콘은 토글; 접힌 메뉴 키보드 focus에서 하위 목록이 보인다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-037 | screen-only | 상류 소유 US1 — 잘못된 메뉴 트리 진단; 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외; DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-038 | screen-only | 상류 소유 US1 — 공식 라우트와 복구; /UI-27 새로고침 및 #UI-27 replace 성공; 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-039 | screen-only | 상류 소유 global-ui — PC 전용 화면 보호; 767px UI-27 본문 대신 안내·이동 수단; 폭 확대 시 미저장 편집이 복원된다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-040 | screen-only | 상류 소유 global-ui — 반응형 공통 레이아웃; 1280px 메뉴240/64px·여백24px; 1024px 기본접힘·16px, 360px 전체 가로넘침0이다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-041 | screen-only | 상류 소유 global-ui — 헤더의 실제 제공 항목; AI/알림 클릭은 준비 중이고 가짜 배지/대화0개; 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-042 | screen-only | 상류 소유 global-ui — 화면 제목·기간 배치; UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-043 | screen-only | 상류 소유 global-ui — 화면 탭과 LRU; 대시보드 닫기 불가; 9번째 취소 시 새 탭0개·기존8개 유지다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-044 | screen-only | 상류 소유 global-ui — 편집 보존과 이동 경고; 계속 작성 시 입력/필터/기간/스크롤 유지; 새로고침/닫기는 beforeunload·dirty 탭은 ●다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-045 | screen-only | 상류 소유 global-ui — 접근 가능한 모달·입력; 모달 내 Tab 순환·닫기 후 원래 포커스; 잘못된 입력 제출 시 첫 오류 필드로 이동한다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-046 | screen-only | 상류 소유 global-ui — 새로고침 탭 초기화; 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-047 | screen-only | 상류 소유 US2 — 설정 조회와 기본값; 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-048 | covered | TB_SY_USER_PREF — 설정 명시 저장; 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. |

| REQ-049 | covered | TB_SY_USER_PREF.PREF_VAL — 손상 설정 격리; 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다. |

| REQ-050 | covered | TB_SY_USER_PREF 개별삭제 — 개별 설정 초기화; 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다. |

| REQ-051 | covered | TB_SY_USER_PREF FAV — 즐겨찾기 편집; 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다. |

| REQ-052 | covered | TB_SY_USER_PREF RECENT — 최근 화면 자동 갱신; 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. |

| REQ-053 | covered | TB_SY_USER_PREF MENU_FOLD — 메뉴 접힘 기억; PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다. |

| REQ-054 | covered | TB_SY_USER_PREF PAGE_SIZE — 목록 크기 설정; 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다. |

| REQ-055 | covered | TB_SY_USER_PREF DENSE — 밀도 설정 동기화; 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다. |

| REQ-056 | covered | TB_SY_USER_PREF COLS — 표 열 설정 저장; 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다. |

| REQ-057 | covered | TB_SY_USER_PREF AMT_UNIT — 금액 단위 선택 기억; 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다. |

| REQ-058 | covered | TB_SY_USER_PREF screenId검증 — 설정 화면ID·표시 보호; 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다. |

| REQ-059 | covered | TB_SY_USER_PREF COLS열정의 — UI-27 열 정의 제한; codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다. |

| REQ-060 | screen-only | 상류 소유 US2 — 설정 적용과 실패 보존; 설정 실패 시 이전 밀도 유지·오류 안내; 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-061 | covered | TB_SY_USER_PREF PERIOD — 기간 설정 타입·범위 검사; NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다. |

| REQ-062 | screen-only | 상류 소유 global-ui — controlled 기간 부품; YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회; 범위 밖/disabled 이벤트0회·내부 DB호출0회다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-063 | screen-only | 상류 소유 US4 — 화면 기간 선택·기억; 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-064 | covered | TB_SY_OPER_STD CASH_BASE_YM — 기간 범위와 운영 전 상태; YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다. |

| REQ-065 | screen-only | 상류 소유 US4 — 연도 배지; 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-066 | covered | TB_CL_CLOSE — 실제 월 마감 조회; CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다. |

| REQ-067 | screen-only | 상류 소유 global-ui — UI-27 네 탭과 조회 상태; 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-068 | data-constraint | Validation Rule Matrix·표 페이지 계약 — page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-069 | covered | TB_SY_CODE_GRP — 그룹 목록과 선택; 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다. |

| REQ-070 | covered | TB_SY_CODE — 일반 코드 조회; GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다. |

| REQ-071 | covered | TB_SY_CODE — 일반 코드 일괄 변경 저장; 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다. |

| REQ-072 | screen-only | 상류 소유 US5 — 편집 취소 복원; 신규·기존 편집 후 취소하면 신규 행 제거·기존값 복원·DB 변경0건이다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-073 | covered | TB_SY_CODE 변경분 — 부분 변경·중복 코드 검사; 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다. |

| REQ-074 | covered | TB_SY_CODE 시스템모드 — 시스템 코드 변경 제한; CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다. |

| REQ-075 | covered | TB_SY_CODE_GRP 직렬화잠금 — 그룹 중복과 채번 경합; 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다. |

| REQ-076 | covered | TB_SY_CODE_GRP — 업무 그룹 생성; POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다. |

| REQ-077 | screen-only | 상류 소유 US6 — 그룹 추가 전 편집 처리; 계속 작성이면 그룹 추가0건·편집 유지; 저장/취소 처리 후에만 그룹 추가가 진행된다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-078 | screen-only | 상류 소유 US5 — 일반 신규 코드 편집 행; 창 추가 뒤 DB0건; 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-079 | covered | TB_SY_CODE 복합PK — 그룹 내 코드 PK 재사용 금지; DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다. |

| REQ-080 | covered | TB_SY_CODE.SORT_ORD — 신규 코드 기본값·정렬 채번; 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다. |

| REQ-081 | covered | TB_SY_CODE.SORT_ORD NUMERIC(5) — 정렬 순번 한계; max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다. |

| REQ-082 | covered | TB_SY_CODE.CODE_NM — 코드명 중복 허용; 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다. |

| REQ-083 | covered | TB_SY_CODE.ADD_VAL1/2/3 — 그룹별 추가값 필수·길이; 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. |

| REQ-084 | covered | TB_SY_CODE.PROJ_STEP 추가값 — PROJ_STEP 진척 정수; 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다. |

| REQ-085 | covered | TB_SY_CODE.BANK 추가값 — BANK 코드와 RSM 라벨; 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다. |

| REQ-086 | covered | TB_SY_CODE.RSM_LABEL 추가값 — RSM_LABEL 허용값; name 입력은 NAME으로 저장; 목록 외 문자열은400이다. |

| REQ-087 | covered | TB_SY_CODE CSV 논리참조 — CSV 정규화·참조; 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다. |

| REQ-088 | screen-only | 상류 소유 US7 — 수당 코드 조회·편집 형태; 일반 값 그룹에서 code/name/unitNm은 읽기 전용이고 값만 입력 가능하다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-089 | covered | TB_SY_CODE 값/SYS모드 — 값 코드 잠금; 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다. |

| REQ-090 | covered | TB_SY_CODE 값신규 — 수당 신규 코드 저장; 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다. |

| REQ-091 | covered | TB_SY_CODE ALWNC — 수당 값 변경과 업무 불변; ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치; 값 변경 후 프로젝트 팀비/경비 변경0건이다. |

| REQ-092 | covered | TB_SY_CODE.USE_YN — 일반 코드 중지·재개; 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다. |

| REQ-093 | covered | TB_SY_CHG_HIST.CHG_TYPE_CD — 사용 상태 변경 이력; USE_YN N 저장 후 STOP·Y 복원 후 RESUME가 이력 GET에서 조회된다. |

| REQ-094 | covered | TB_SY_CODE·고정참조가드 — 삭제 참조 차단; 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다. |

| REQ-095 | covered | TB_SY_CODE.DEL_YN — 일반 코드 논리 삭제; 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다. |

| REQ-096 | covered | TB_SY_CHG_HIST DEL — 코드 삭제 이력; 미참조 삭제 후 이력에 DEL_YN의 N→Y가 조회되고 실제 코드 행은 보존된다. |

| REQ-097 | covered | 삭제참조가드·고정매핑표 — 고정 참조 가드; 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다. |

| REQ-098 | covered | TB_SY_CODE 그룹/행잠금 — 삭제 직전 재검사; 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다. |

| REQ-099 | covered | TB_SY_OPER_STD — 운영 기준 전체 조회; 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다. |

| REQ-100 | covered | TB_SY_OPER_STD 적용월 — 기준 현재·과거·미래 이력; 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다. |

| REQ-101 | covered | TB_SY_OPER_STD·TB_CL_CLOSE — 일반 기준 적용 최소월; 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다. |

| REQ-102 | covered | TB_SY_OPER_STD 복합PK — 기준 월별 추가·덮어쓰기; 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다. |

| REQ-103 | covered | TB_SY_OPER_STD 변경키 — 기준 변경 키 중복 거부; 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다. |

| REQ-104 | screen-only | 상류 소유 US9 — 묶음 기준 표시; AI8개는2줄이며 변경 칸만 같은 적용월 요청에 들어가고 DB는 각 STD_CD행이다.; DB컬럼 추가없이 UI/서비스에서 검증 |

| REQ-105 | covered | TB_SY_OPER_STD DUN — DUN 순서 검증; D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다. |

| REQ-106 | covered | TB_SY_OPER_STD EXT_PRBL/STEP_PRBL — 확률 범위 검증; 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다. |

| REQ-107 | covered | TB_SY_OPER_STD AI_SCORE8 — AI 점수 합계 검증; 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다. |

| REQ-108 | covered | TB_SY_OPER_STD WTHDR_NOTI_D — 철수 통보일 정수; 1과99,999,999,999 허용; 0 또는 소수는400이다. |

| REQ-109 | covered | TB_SY_OPER_STD CASH_BASE_YM — 자금 시작 연월 표현; 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다. |

| REQ-110 | covered | TB_SY_OPER_STD 묶음경계 — 기준 전체 영향구간 검사; 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다. |

| REQ-111 | covered | TB_SY_OPER_STD CASH두행 — 자금 두 키 즉시 갱신; 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다. |

| REQ-112 | covered | TB_SY_CHG_HIST CASH UPD — 자금 기준 변경 이력; 성공한 실제 값 변경은 UPD 전후값으로 조회; 미변경값은 이력0건이다. |

| REQ-113 | covered | TB_CL_CLOSE·TB_SY_OPER_STD 기존시작월 — 기존 시작월 마감 잠금; 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다. |

| REQ-114 | covered | TB_SY_OPER_STD 두행잠금 — 자금 동시 잠금·후속 갱신; 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다. |

| REQ-115 | covered | TB_SY_CODE_MAP — 표기 대응 목록·검색; SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다. |

| REQ-116 | covered | TB_SY_CODE_MAP 복합UK — 대응 입력·중복 검사; 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. |

| REQ-117 | covered | TB_SY_CODE_MAP — 대응 즉시 등록; POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다. |

| REQ-118 | covered | TB_SY_CHG_HIST 대응JSON — 대응 등록·삭제 이력; NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다. |

| REQ-119 | covered | TB_SY_CODE_MAP 물리삭제 — 대응 다중 물리 삭제; 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다. |

| REQ-120 | covered | TB_SY_CODE_MAP 원자삭제 — 대응 삭제 충돌; 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다. |

| REQ-121 | covered | TB_SY_CODE_MAP 쓰기경계 — 대응 변경의 업무 불변; 대응 추가/삭제 전후 기존 업무 테이블 값과 행수가 같다. |

| REQ-122 | covered | TB_SY_CHG_HIST — 변경 이력 조회; REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다. |

| REQ-123 | covered | TB_SY_CHG_HIST.CHG_ITEM_NM — 변경 유형과 과거 이름 추적; 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다. |

| REQ-124 | covered | TB_SY_CHG_HIST 대상필터 — 이력 필터 도메인; 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다. |

| REQ-125 | covered | TB_SY_CHG_HIST 대상키/전후값 — 이력 키·전후값 무손실; 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다. |

| REQ-126 | covered | TB_SY_CODE 조회투영 — 공통 active 코드 조회; GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다. |

| REQ-127 | covered | TB_SY_OPER_STD 유효행 — 월별 단일 기준 조회; GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다. |

| REQ-128 | covered | TB_SY_OPER_STD 없음검사 — 기준 미존재 거부; 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다. |

| REQ-129 | data-constraint | Validation Rule Matrix·API 필드와 서버 필드 보호 — SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-130 | data-constraint | Validation Rule Matrix·공통 오류 반환과 비밀 보호 — 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다.; HTTP상태/요청정본은 contracts/openapi.yaml |

| REQ-131 | covered | TB_SY_CHG_HIST 무기한보존 — 변경 이력 무기한 보존; 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. |

| REQ-132 | non-functional | 공통 계약 참조·Persistence Assumptions — 원문 구현 기술 요구; 고정 계약 Java17/3.3.x와 충돌한 두 버전은 OQ-001로 남고 임의 확정하지 않는다. |

| REQ-133 | non-functional | 공통 계약 참조·Persistence Assumptions — 3서비스 실행과 SPA; /UI-27 직접 새로고침 성공; compose3서비스 healthy·.env.example은 자리표시자/실행 안내만 포함한다. |

| REQ-134 | non-functional | 공통 계약 참조·Persistence Assumptions — 서울 시간대 일관성; 현재일 API와 DB 세션 및 화면 일시가 Asia/Seoul 기준으로 일치한다. |

| REQ-135 | non-functional | 공통 계약 참조·Persistence Assumptions — Flyway 전용 스키마·정밀도; OQ-007 승인 전 JPA/MyBatis 충돌 해소되지 않음 |

| REQ-136 | non-functional | 공통 계약 참조·Persistence Assumptions — 마이그레이션 파일 경로; backend/src/main/resources/db/migration/에서 V1 전체DDL 다음 V2 전체data가 적용된다. |

| REQ-137 | non-functional | 공통 계약 참조·Persistence Assumptions — 프론트 구조와 계층 책임; UI-27 실제 공통부품 사용·hook/client 통신·서버 검증이 있으며 HTML 전체 iframe0개다. |

| REQ-138 | non-functional | 공통 계약 참조·Persistence Assumptions — 고정 technology_stack; React+TypeScript+Vite, Java17+Spring Boot, PostgreSQL, docker·healthcheck_required=true가 유지된다. |

| REQ-139 | non-functional | 공통 계약 참조·Persistence Assumptions — 고정 Version BOM; BOM 표가 원문5개 값과 동일하며 충돌하는 Spring Boot3.5.x를 몰래 대입하지 않는다. |

| REQ-140 | non-functional | 공통 계약 참조·Persistence Assumptions — 고정 required_outputs; backend·frontend·infra/docker-compose.yml·/api/health 이름이 바뀌지 않는다. |

| REQ-141 | non-functional | 공통 계약 참조·Persistence Assumptions — 레퍼런스 시각 계약; sidebar/header/table/form 밀도·spacing/color/responsive 비교 증거가 있고 기능은 본문을 따른다. |

| REQ-142 | non-functional | 공통 계약 참조·Persistence Assumptions — Docker 빌드 컨텍스트 제외; 각 context의 .dockerignore가 node_modules/.git/dist/build/.next/coverage를 제외한다. |

| REQ-143 | non-functional | 공통 계약 참조·Persistence Assumptions — 격리 환경만 배포; 인터넷 공개 배포 경로가 없고 실제 인증/권한 검증 전 공개 배포는 차단된다. |

| REQ-144 | non-functional | 공통 계약 참조·Persistence Assumptions — 실행·영속성 결정 보존; OQ-007 승인 전 JPA/MyBatis 충돌 해소되지 않음 |

| REQ-145 | non-functional | 공통 계약 참조·Persistence Assumptions — 실제 백엔드 산출물; backend/ 변경과 실제 migration 증거 없이 UI만 있으면 완료 불가다. |

| REQ-146 | non-functional | 공통 계약 참조·Persistence Assumptions — 프론트·API 함께 검증; 실제 API 저장 후 브라우저 재조회 결과와 DB 값이 일치하며 미실행은 미검증이다. |

| REQ-147 | out-of-scope | 범위 경계 — UI-27 외 업무 화면은 제목·해당 기간 줄·다음 단계 안내만 제공한다. |

| REQ-148 | out-of-scope | 범위 경계 — 월 마감/해제/스냅샷 생성은 구현하지 않는다. |

| REQ-149 | out-of-scope | 범위 경계 — 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 생성하지 않는다. |

| REQ-150 | out-of-scope | 범위 경계 — UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. |

| REQ-151 | out-of-scope | 범위 경계 — 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건에서 제외한다. |

| REQ-152 | out-of-scope | 범위 경계 — 계산규칙 테이블/데이터는 적재하되 편집 화면과 계산 실행 엔진은 생성하지 않는다. |

| REQ-153 | out-of-scope | 범위 경계 — 그룹명/사용여부 변경과 그룹 삭제 기능은 제공하지 않는다. |

| REQ-154 | out-of-scope | 범위 경계 — 운영 기준의 키·이름·단위·설명은 읽기 전용이며 신규 키/삭제는 제공하지 않는다. |

| REQ-155 | out-of-scope | 범위 경계 — 변경 이력은 조회만 제공하고 수정·삭제 API는 생성하지 않는다. |

| REQ-156 | non-functional | 공통 계약 참조·Persistence Assumptions — 실제 DB health 조회; 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다. |
