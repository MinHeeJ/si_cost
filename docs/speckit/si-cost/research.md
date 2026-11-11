# 설계 조사·의사결정 — 원가관리시스템 기반 구축·공통코드

## 조사 범위와 근거 수준

이번 문서는 제공된 요구와 승인 상류·Data/API 배치의 설계 판단을 정리한다. 제품 구현, 원격 저장소 재조사, SQL 적재, 실제 API 호출, 브라우저 관찰을 수행한 결과가 아니다. 요구 원장 156개는 이미 존재하는 requirement-registry.json·requirement-registry.md를 문서 작성 전에 읽고 그대로 재사용했다. 각 원문 인용과 실제 시작행의 일치를 확인했으며 번호·title·statement·acceptance_hint·disposition은 바꾸지 않는다. Source Decomposition Ledger와 Capability-to-Story Ledger는 승인 spec.md의 해당 절을 참조한다. 이 배치는 research.md·quickstart.md만 작성하며 spec.md·plan.md·tasks.md·원장·Data/API 파일은 수정하지 않는다.

자료의 책임은 다음과 같다.

| 자료 | 이번 문서에서 쓰는 사실 | 증거 한계 |
| --- | --- | --- |
| SPEC-REQUEST.md L1~476 | 구현·제외 범위, 정밀도·잠금·삭제·초기값·수락 조건 및 명확화 | 상충하는 기술 문장을 임의 선택하지 않음 |
| spec.md / requirement-registry.json / requirement-registry.md | 156개 canonical_id의 의미, US1~US15의 독립 완료 경계, 원문 인용 | 원장 REQ-144의 jpa와 최종 원문 mybatis의 의미 충돌은 그대로 드러냄 |
| plan.md | exact marker JSON의 실행·영속성 정본, 고정 계약, 아키텍처·착수 차단 | resolved는 상류 정합화 완료나 제품 실행 성공을 뜻하지 않음 |
| tasks.md | T001 승인 게이트부터 T073 실제 검증 보고까지의 작업 연결 | 미완료 체크리스트이며 구현되어 있다는 증거가 아님 |
| data-model.md / data-model.json | 실제 물리명·키·필드, 6테이블 쓰기 경계, Seed Data Contract·fixture | DDL 파싱 결과와 DB 실측을 구분; request_key=null은 미확정 입력 |
| contracts/openapi.yaml | 기존 17경로·22연산의 operationId, 공개 DTO·오류·쓰기 시험 | x-contract-status는 clarification-required/build_ready=false; OQ-003/OQ-004 제안의 승인으로 확대하지 않음 |

상류의 “이번 호출에는 후속 파일이 없다”는 문장은 각 파일 작성 시점의 기록이다. 현재는 Data/API 파일이 존재한다. 과거 기록을 덮어쓰거나 이를 현재 파일 부재로 해석하지 않는다. 제품 소스 backend/·frontend/·infra/는 현재 문서 작업 폴더에 없다. Git 메타데이터도 없으므로 현재 제품 revision·빌드 도구·실행 포트를 검증했다고 주장하지 않는다.

## 의사결정 기록

각 결정은 원문에 근거한 유지 사항, 선택하지 않은 대안, 대가 및 확인할 증거를 분리한다. 외부 제품의 최신 지원 기간·벤치마크·호환성을 조사한 것처럼 기술하지 않는다.

### D01 — 버전·실행·영속성은 승인 게이트를 먼저 통과한다

근거: SPEC-REQUEST.md L30/L33, L370~402, L419, plan.md의 실행·영속성 정본 및 OQ-001/OQ-007. 관련: REQ-132, REQ-135, REQ-138, REQ-139, REQ-140, REQ-144, REQ-145.

유지: 고정 계약 Java 17/Boot 3.3.x와 원문 Java21/Boot3.5.x를 둘 다 표시한다. plan.md exact JSON은 mybatis이고 승인 원장·spec.md의 REQ-144는 jpa다. 원장의 REQ-144를 MyBatis 채택 요구로 재정의하지 않는다. 공통 부분은 Spring MVC의 imperative/blocking 요청 처리다. 영속성 의존성·매핑·JPA validate 실행의 최종 선택은 T001에서 권한 있는 정합화 승인이 나기 전 보류한다.

대안: 원문 후반의 고정 BOM만으로 충돌이 자동 해소되었다고 판단하거나, MyBatis/JPA를 동시에 넣거나, WebFlux/R2DBC로 우회하는 안은 채택하지 않는다. 대가: 문서 연결과 비변경 검증을 진행할 수 있지만 build-ready와 실제 설치 합격을 아직 선언할 수 없다. 후속: 정확한 승인 버전·persistence 선택과 REQ-135 검증 의무의 정합화 증거를 같은 승인 단위로 확보한다. 별도 열 번째 실행·영속성 명세는 만들지 않는다.

### D02 — 전체 DDL과 초기값을 보존하되 실행 사본의 래퍼만 정리한다

근거: L17, L24, L35~47. 관련: REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-136.

결정: V1__full_schema.sql은 전체 DDL·HQ 예약 파트를, V2__base_data.sql은 전체 data를 보존한다. 원본 SQL은 변경하지 않는다. data 최상위 BEGIN 2개/COMMIT 1개만 실행용 사본에서 제거한다. 문자열 전역 치환은 계산규칙 TEXT를 손상시킬 수 있으므로 금지한다. 실제 파일의 SQL 문맥을 검토한 diff와 원본/실행본 각각의 해시를 남긴다. 적용 migration 재작성·자동 baseline·임의 DROP/TRUNCATE·다른 DB 초기화는 대안이 아니다.

Data 배치가 기록한 읽기 전용 조사 결과: DDL revision d1e18c27afb077bfa5b1d92b982af194676ebd91, UTF-8 BOM 제거 후 텍스트 SHA-256 96729f305751ab102c430451b99c7edb22e983bd4c234ec6bbfea6d2bcc1bb55. 이는 raw bytes 해시가 아니며 이번 배치가 다시 다운로드·검증한 해시도 아니다. DDL 상단 설명 67테이블/825컬럼과 달리 전체 CREATE 파싱은 68테이블/836컬럼으로 기록되어 있다. 73 FK·78 명시 인덱스·13시퀀스·46 CHECK·5 UK와 TB_SY_CALC_RULE 11컬럼을 포함한다. 실제 설치 카탈로그 계수와 같은 증거가 아니다.

대가: 전체 68테이블이 설치되지만 이번 기능은 그중 일부만 쓴다. 테이블 존재를 업무 화면/CRUD 생성 승인으로 해석하지 않는다. 해결된 부분: OQ-002의 사용자·행정구역 물리명은 Data 배치에서 확보되었다(TB_SY_USER, TB_SY_ADM_AREA, TB_SY_ADM_HDONG). 남은 부분: project_data.sql 전체 대조·revision·raw hash·래퍼 diff 및 실제 DB 수량/재시작 중복0 검증. T002/T006/T007/T008이 후속 소유자다.

### D03 — 저장 허용 경계와 서버 소유 필드를 분리한다

근거: L49~62, L101~108, L295/L322/L324. 관련: REQ-010, REQ-011, REQ-025, REQ-026, REQ-027, REQ-129, REQ-130, REQ-143.

결정: 설치 후 쓰기 허용은 TB_SY_CODE_GRP·TB_SY_CODE·TB_SY_CODE_MAP·TB_SY_OPER_STD·TB_SY_USER_PREF·TB_SY_CHG_HIST뿐이다. TB_SY_USER·역할·메뉴·TB_CL_CLOSE·프로젝트는 조회 경계다. CurrentUserProvider 한곳이 admin/ADMIN/임시 관리자/developmentMode=true를 제공하고 REG_USER_ID·MOD_USER_ID·시각은 서버가 쓴다. 요청 처리자/SYS/VAL/DEL/등록시각 주입은400, 알려진 모드 금지 필드는409로 구분한다.

대안: HTTP에 노출할 수 있다는 이유로 모든 저장열을 DTO에 복제하거나, 업무마다 admin을 하드코딩하거나, 임시 관리자에 가짜 역할403을 추가하지 않는다. 대가: 인증이 없으므로 로컬/접근 제한 사설 검증 환경만 허용한다. security=[]는 공개 배포 허가가 아니다. 후속 증거: getCurrentUser·saveGroupCodes·putPreference의 처리자 주입 거부, 비밀번호/메일/접속비밀 비노출 및 업무 테이블 불변(T010/T068).

### D04 — 잠금·전체 집합 revision·이력을 하나의 저장 경계로 묶는다

근거: L103~108, L193~196, L248, L263, L269, L277. 관련: REQ-028, REQ-029, REQ-030, REQ-031, REQ-032, REQ-075, REQ-081, REQ-102, REQ-110, REQ-114, REQ-120.

결정: 그룹 저장·기준 한 요청·대응 추가/다중 삭제는 단일 트랜잭션이다. 키 오름차순 잠금 뒤 최신 revision을 비교한다. 그룹 전체 코드와 기준키 전체 적용월을 포함하며 식별자/값/REG_DTTM/MOD_DTTM을 비교한다. 양NULL 시각도 값이 다르면 revision이 다르다. version 컬럼을 추가하지 않는다. 동일 revision 경합은 선행1건 성공/후행409 STALE_WRITE·후행 데이터/이력0건이다. 동일값은200 changed=false·시각/이력0건이다.

대안: 마지막 요청 승리·시각만 비교·브라우저 배열 기반 revision·부분 성공 저장은 거부한다. 대가: 그룹/기준키 전체 집합 잠금과 그룹 생성의 직렬화 잠금 경합을 관리해야 한다. 승인된 제한 이상의 성능 수치·timeout·자동 재시도 정책은 추가하지 않는다. commit 후 서버 캐시 무효화, 성공 응답 후2초 이내 현재 브라우저 갱신을 별도로 시험한다. saveGroupCodes/saveOperatingStandards/deleteCodeMappings와 FX-REVISION/FX-NO-CHANGE/FX-MAP-RACE가 관측 경계다.

### D05 — 숫자는 문자열·BigDecimal, 표시는 비파괴 변환이다

근거: L82~97, L201~218, L251~263. 관련: REQ-013, REQ-014, REQ-015, REQ-016, REQ-017, REQ-018, REQ-019, REQ-020, REQ-021, REQ-022, REQ-023, REQ-024, REQ-083, REQ-084, REQ-085, REQ-086, REQ-087, REQ-108, REQ-109, REQ-135.

결정: codeVal/value는 십진 문자열이며 float/double로 변환하지 않는다. CODE_VAL·STD_VAL은0~99,999,999,999.9999·최대4소수, JOB 월단가는0~999,999,999,999,999 정수 문자열이다. 자동 반올림/버림은0회다. 쉼표·공백·끝의 원/%만 정리한다. 음수·지수·NaN·Infinity·소수5자리·정수12자리는400이다. 수당 빈값은0, 일반 기준값 빈칸은400이다. 일반 %라고100 상한을 추가하지 않는다.

대안: Number 변환·화면 표시값을 원값으로 저장·조용한 절단·은행 앞0 제거는 거부한다. 대가: 입력 문자열·정규화값·표시값을 명시적으로 분리한다. BANK는3자리 문자열, RSM_LABEL은 원문8값, CSV는 최초 순서 중복 제거·최종상태 active 참조/기존중지 유지 규칙을 따른다. 연월은 YYYY-MM↔YYYYMM, CASH_BASE_YM API value만 YYYYMM6자리라는 예외를 보존한다. 구체 시험은 T011/T012/T033/T039·FX-PRECISION이다.

### D06 — 삭제는 대상별로 다르며 참조 가드는 고정 매핑이다

근거: L220~239, L276~286, L451~462, L475~476. 관련: REQ-050, REQ-074, REQ-079, REQ-089, REQ-094, REQ-095, REQ-096, REQ-097, REQ-098, REQ-118, REQ-119, REQ-120, REQ-123, REQ-125, REQ-131, REQ-153, REQ-154, REQ-155.

결정: 일반 업무 코드는 미참조일 때만 DEL_YN=Y·DEL 이력·물리행/PK 보존이다. 참조1건이라도 CODE_IN_USE409·유형/건수를 반환한다. 대응은 물리삭제·DEL 이전 JSON, 설정 초기화는 해당 행만 물리삭제·UI-27 이력0건이다. 시스템·값코드·그룹·기준키·변경이력 삭제 기능은 없다. SYS=Y/VAL=Y 중첩은 시스템 우선·이름만 변경, 값 변경409다. 이력은 무기한 보존·기간 삭제 없음으로 명확화되었다.

대안: DELETE를 일괄 물리삭제로 해석하거나 활성 FK만 검사하지 않는다. CodeReferenceGuard는 L226~239의 모든 고정 컬럼/CSV·SKILL 현재/과거명·EXT_PSBL EXTP 전후 코드/이름·공통 대응을 검사하며 취소/중지/마감도 포함한다. 자기 NEW 이력만 사용으로 세지 않는다. 요청의 테이블명으로 SQL을 만들지 않는다. 대가: FK 외의 논리 참조까지 fixture가 필요하다. data-model.md의 삭제 참조 가드·FX-REFERENCES 및 deleteGroupCode/getCommonCodeHistory가 검증 경계다. 이력 외 데이터의 보존 기한·암호화/마스킹 추가정책은 미정이며 “없음”으로 확정하지 않는다.

### D07 — 운영 기준은 적용월 경계, 자금 두 키는 기존 시작월 잠금이다

근거: L243~269, L291. 관련: REQ-099, REQ-100, REQ-101, REQ-102, REQ-103, REQ-104, REQ-105, REQ-106, REQ-107, REQ-108, REQ-109, REQ-110, REQ-111, REQ-112, REQ-113, REQ-114, REQ-127, REQ-128.

결정:43키는 STD_CD순 비페이지·메타 읽기 전용이다. 일반 기준은 같은 적용월 UPDATE/없으면 INSERT·다른월 유지, 최소월=max(CASH_BASE_YM,마지막 CLOSE+1)이다. DUN 순서·확률0~100·AI8개 BigDecimal 합100은 미변경키와 미래 예정 경계까지 검사한다. 현재월만 유효하다고 승인하지 않는다. 이력은 모든 적용월 DESC·현재/예정/지난, 현재값은 요청월 이하 최신행이며 부재는 목록 “기준값 없음”/getParameter404 STANDARD_NOT_FOUND다.

CASH_BASE_YM/CASH_BASE_AMT는 effectiveMonth 없는 별도요청·키당1행 UPDATE·원래 APLY_BGN_YM 보존이다. 두 행을 함께 잠그고 기존 시작월 CLOSE를 재검사한다. 새 시작월로 우회할 수 없으며 마감이면 두 키409다. 저장 후 기간/기준 조회만 갱신하고 계산/마감 자료는 불변이다.

대안: 미래값을 현재로 대체·기준 부재를0으로 대체·두 CASH 키를 일반 적용이력과 혼합·합계 오차 허용은 거부한다. 대가: 영향구간의 경계 검사와 여러 키 잠금이 필요하다. saveOperatingStandards/getOperatingStandardHistory/getParameter 및 FX-STD-BOUNDARY/FX-CASH-CLOSE로 각 허용·거부를 검증한다.

### D08 — DB 메뉴·탭 상태와 서버 설정은 서로 다른 정본이다

근거: L114~162. 관련: REQ-035, REQ-036, REQ-037, REQ-038, REQ-039, REQ-040, REQ-041, REQ-042, REQ-043, REQ-044, REQ-045, REQ-046, REQ-047, REQ-048, REQ-049, REQ-050, REQ-051, REQ-052, REQ-053, REQ-054, REQ-055, REQ-056, REQ-057, REQ-058, REQ-059, REQ-060.

결정: 메뉴 이름/아이콘/pcOnly·정렬은 TB_SY_MENU 조회 원천이다. 잘못된 노드·자손·중복 화면 탈락 트리를 진단/제외하고 DB를 자동수리하지 않는다. 등록표는 screenId/컴포넌트/기간유형만 소유한다. /{screenId}·root→/UI-16·hash replace, 최대8탭/LRU9번째 확인·dirty 보존·새로고침 URL+대시보드를 유지한다. 열린 탭 목록은 서버 복원하지 않는다.

TB_SY_USER_PREF는 설정 정본이며 localStorage만으로 합격하지 않는다. 최초 명시 저장은 기본값과 같아도 INSERT, expectedRevision=null, 기존은 토큰이다. 손상 JSON은 항목 오류/revision·기본값 임시 표시이며 자동삭제하지 않는다. FAV10/RECENT5·PAGE_SIZE 정수20/50/100·DENSE·COLS 필수열·AMT_UNIT 선택만·PC 항목 표시만 숨김을 보존한다. 대가: 편집 상태와 설정 변경을 분리하고 저장 실패 시 이전 적용값을 유지해야 한다. getMenus/getPreferences/putPreference/deletePreference와 FX-MENU/FX-PREF가 증거다.

### D09 — 기간 부품은 controlled이고 마감은 조회만 한다

근거: L164~179. 관련: REQ-061, REQ-062, REQ-063, REQ-064, REQ-065, REQ-066, REQ-148.

결정: PeriodSelector는 type/value/min/max/disabled와 onChange만 사용하고 DB/설정 호출은 부모 ScreenPeriodBar가 한다. YEAR/YEAR_MONTH 두 부모로 실제 재사용을 시험한다. 서버 현재일·CASH_BASE_YM·실제 TB_CL_CLOSE로 범위/배지를 판단하며 시연 날짜·가상9개월 마감을 쓰지 않는다. 누계 연도 변경 시 월을 새 범위로 제한한다. 범위 역전은 “운영 시작 전”·비활성·PERIOD 저장0회, NONE 쓰기는400이다. UI-27은 NONE이며 업무 기간 줄 없이 자체 적용월만 쓴다.

대안: 부품 내부에서 설정을 자동 저장하거나 프론트 현재일만으로 마감을 조작하거나 마감 쓰기 API를 추가하지 않는다. 대가: 부모의 현재일/설정/범위 동기화와 범위 밖 저장값의 안내가 필요하다. getPeriodContext/getMonthClose/putPreference·FX-PERIOD가 검증 경계다.

### D10 — 공통 부품은 실제 사용으로, UX는 기능 범위 안에서 검증한다

근거: L18~21, L68~80, L97/L108, L134~141, L357~367/L411~412. 관련: REQ-012, REQ-024, REQ-032, REQ-033, REQ-034, REQ-045, REQ-067, REQ-072, REQ-077, REQ-078, REQ-088, REQ-090, REQ-137, REQ-141, REQ-147, REQ-149.

결정: UI-27 코드·대응·기준·이력이 DataTable<T>를 실제 공유하고 columns/renderer/row action은 화면이 전달한다. 추가창의 일반/수당 코드는 로컬 편집행이며 하단 저장 전 DB0건이다. 그룹/대응 추가는 즉시 저장과 구분한다. 취소·단순탭전환·미저장 폐기 확인·focus trap·입력 보존·2초 갱신을 독립 시험한다. commit 후 재조회 실패는 “저장됐지만 다시 불러오지 못했습니다.”이며 자동 재저장/재시도는0회다.

대안: HTML 전체 iframe/DOM 문자열 복제·가짜 수치·무동작 저장·“토스트가 떴다”만으로 영속성 합격은 거부한다. 레퍼런스의 실제 색/글꼴/간격/아이콘 관찰은 이번 배치에서 하지 않았다. 후속 ui-design.md/T019에서 지정 HTML을 관찰해 근거를 남긴다. dashboard card 계약이 있어도 UI-16 업무카드/가짜 숫자를 생성하지 않는다. 대가: 브라우저 네트워크·실제 DB·이력·새로고침의 교차 증거가 필요하다.

### D11 — 공유 API와 봉투는 Data/API 계약을 참조하고 미결을 숨기지 않는다

근거: L293~324, 승인 spec.md의 API 동작 계약, contracts/openapi.yaml의 x-open-questions. 관련: REQ-068, REQ-069, REQ-070, REQ-071, REQ-076, REQ-095, REQ-115, REQ-117, REQ-119, REQ-122, REQ-124, REQ-126, REQ-127, REQ-129, REQ-130, REQ-156.

결정: 기존17자원 경로/22연산을 보존하고 하나의 saveGroupCodes/saveOperatingStandards가 여러 canonical_id를 담당한다. REQ 번호 endpoint를 만들지 않는다. ApiResponse/ApiError의 required success/meta·true/false, 약속된 data의 연산별 allOf, 십진 문자열·목록 items/total/page/size를 참조한다. 코드편집/대응/이력만 페이지이고 그룹/43기준/공통코드/적용이력은 임의 페이지화하지 않는다. 실제DB getHealth는200/503이다.

미결: OQ-003의 오류 루트 배치/meta/details 구조, OQ-004의201 본문 없음·삭제204·DELETE revision query·없는 설정 초기화·저장후 재조회 배열범위는 Data/API 설계 제안이지 확정 정책이 아니다. quickstart.md는 이 차이를 표시하고 미승인 상태를 조건부로 검사한다. 이번 문서가 새 응답 shape를 만들거나 제안을 원문 확정값으로 바꾸지 않는다.

대안: 모든 연산의 빈 data 객체·무관한 결과 union·ApiError 전체 중첩·금지 역할403·시험 통과용 오류 발명은 거부한다. 쓰기 x-required-tests는 happy/auth/validation/business/side-effect 각각 별도 조건→관찰 결과를 유지한다. 임시관리자 auth는401/403 거부가 아니라 인증차단0건과 처리자 주입400의 원문 예외다.

### D12 — 설치·시험·운영 데이터와 제외 기능을 분리한다

근거: L21~24, L43~47, L218, L278, L326~355, L465~476. 관련: REQ-002, REQ-004, REQ-005, REQ-091, REQ-121, REQ-131, REQ-133, REQ-134, REQ-136, REQ-140, REQ-142, REQ-143, REQ-145, REQ-146, REQ-147, REQ-148, REQ-149, REQ-150, REQ-151, REQ-152, REQ-153, REQ-154, REQ-155, REQ-156.

결정: 설치 seed는 SQL 그대로이며 시험 fixture는 독립 PostgreSQL에만 만든다. 행정구역0·설정0·대응0·이력0으로 시작한다. UI-27 필요 외 테스트용 업무화면·주소적재·계산엔진·UI-20·일/시간 단가·근태·마감쓰기·엑셀파서/입출력·메일/AI·인쇄/다크모드는 만들지 않는다. 이력 무기한 보존만 확정이며 다른 정책의 미정은 유지한다.

3서비스 compose·nginx /api 프록시와 SPA fallback·Asia/Seoul·실제 DB health·각 build context의 .dockerignore·환경변수와 자리표시자 .env.example은 유지한다. 포트·서비스명·볼륨명·테스트 runner는 현재 없으므로 quickstart에서 실제 구성 확인 후 변수로 받는다. 대안: 예시 frontend 배열/계산값을 seed로 승격·API mock으로 DB 저장 합격·volume 삭제로 재시작 중복 검사를 우회하지 않는다. 대가: 정적 문서 검증과 실행 합격을 분리한 보고가 필요하다.

## API 의존·검증 참조

다음 표는 contracts/openapi.yaml의 연산을 참조할 뿐 schema를 재정의하지 않는다. 대표 요구 외 공통 규칙은 해당 operation의 x-related-requirements 전체를 따른다.

| operationId | METHOD path | 설계 결정·독립 검증 | 대표 canonical_id |
| --- | --- | --- | --- |
| getHealth | GET /api/health | 실제 DB health 조회: 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다. | REQ-156 |
| getCurrentUser | GET /api/current-user | 임시 사용자 컨텍스트: GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다. | REQ-025 |
| getMenus | GET /api/menus | DB 메뉴 구성·정렬: 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다. | REQ-035 |
| getPeriodContext | GET /api/period-context | 화면 기간 선택·기억: 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다. | REQ-063 |
| getMonthClose | GET /api/month-close | 실제 월 마감 조회: CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다. | REQ-066 |
| getPreferences | GET /api/me/preferences | 설정 조회와 기본값: 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다. | REQ-047 |
| putPreference | PUT /api/me/preferences/{type}/{key} | 설정 명시 저장: 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. | REQ-048 |
| deletePreference | DELETE /api/me/preferences/{type}/{key} | 개별 설정 초기화: 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다. | REQ-050 |
| getCodeGroups | GET /api/code-groups | 그룹 목록과 선택: 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다. | REQ-069 |
| createCodeGroup | POST /api/code-groups | 업무 그룹 생성: POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다. | REQ-076 |
| getGroupCodes | GET /api/code-groups/{group}/codes | 일반 코드 조회: GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다. | REQ-070 |
| saveGroupCodes | PUT /api/code-groups/{group}/codes | 일반 코드 일괄 변경 저장: 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다. | REQ-071 |
| deleteGroupCode | DELETE /api/code-groups/{group}/codes/{code} | 일반 코드 논리 삭제: 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다. | REQ-095 |
| getCodes | GET /api/codes/{group} | 공통 active 코드 조회: GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다. | REQ-126 |
| getCodeMappings | GET /api/code-mappings | 표기 대응 목록·검색: SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다. | REQ-115 |
| createCodeMapping | POST /api/code-mappings | 대응 즉시 등록: POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다. | REQ-117 |
| deleteCodeMappings | POST /api/code-mappings/delete | 대응 다중 물리 삭제: 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다. | REQ-119 |
| getOperatingStandards | GET /api/operating-standards | 운영 기준 전체 조회: 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다. | REQ-099 |
| saveOperatingStandards | PUT /api/operating-standards | 기준 월별 추가·덮어쓰기: 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다. | REQ-102 |
| getOperatingStandardHistory | GET /api/operating-standards/{key}/history | 기준 현재·과거·미래 이력: 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다. | REQ-100 |
| getParameter | GET /api/params/{key} | 월별 단일 기준 조회: GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다. | REQ-127 |
| getCommonCodeHistory | GET /api/common-code-history | 변경 이력 조회: REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다. | REQ-122 |

## 해결된 질문·미결·후속 결정

| 항목 | 현재 사실과 상태 | 해소·후속 결정/소유 작업 |
| --- | --- | --- |
| 삭제 정책 | 명확화 L451~456으로 일반 논리삭제/대응 물리삭제/설정 개별초기화 분리 | D06·T042/T060/T024의 대상별 DB·이력 증거 |
| SYS/VAL 중첩 | L457~462 시스템 우선·기존 이름 허용/값 금지 확정 | D06·FX-SYS-VALUE·T039 |
| 환경·이력 | L465~476 사설/로컬만·이력 기한 없이 유지 확정 | D03/D12·T005/T015/T068 |
| OQ-001 | Java/Boot 두 버전 충돌, 미해소 | T001 승인 전 의존성·build-ready 확정 금지 |
| OQ-002 | Data 배치에서 DDL 물리명/수량 파싱 확인; data 전체와 DB 실측은 미검증 | T002/T006/T007, raw/text 해시와 실제 DB 계수 분리 |
| OQ-003 | API 오류 위치·meta/details 내부 모양은 제안 상태 | T016 직렬화 완료 전 승인; 현재 OpenAPI를 수정하지 않음 |
| OQ-004 | 추가 상한/정책 미정; Data/API 결과/DELETE 전달위치도 미승인 | 원문에 없는 입력·결과·없는 설정 삭제 동작을 추정하지 않음 |
| OQ-005 |17경로/22 operationId 연결은 존재 | 후속 UI/architecture에서 실제 operationId만 참조, 화면/엔터티 추가 금지 |
| OQ-006 | data-model.json.meta.request_key=null | 호스트 실제 값 수신 후 소유 배치에서 갱신; BASIC-129로 대체 금지 |
| OQ-007 | plan mybatis와 원장/spec jpa·JPA validate 충돌 | T001 권한 있는 정합화 승인; REQ-144 번호/의미 불변 |
| OQ-DATA-003 | 파일 저장/검사/용량/보존 상세 미정·이번 파일 입출력 제외 | 정책이 없다고 단정하지 말고 파일 API/필드 생성 금지 |
| OQ-DATA-004 | 이력 외 보존·추가 보안등급/암호화/마스킹 미정 | 확정된 비밀 비노출·사설 환경만 적용하고 추가정책은 승인 대기 |
| 실행 세부 | 실제 compose 서비스명/포트/env 키·manifest·테스트 runner 미확보 | T002/T005/T071; quickstart 변수와 명령에 실제 값 연결 |
| 시각 관찰 | 이번 배치에서 레퍼런스 HTML을 관찰하지 않음 | 후속 ui-design.md/T019/T069에서 시각 근거·비교 기록 |

## 가정·위험과 인계 기준

가정은 승인 사실이 아니다. 실행 walkthrough는 구현 저장소가 제공되고 T001 기술 충돌 해소 및 Data/API 미결 승인이 이루어진 뒤 사용하는 조건부 절차다. estimated:true나 “추정 — 사용자 미확인”을 승인으로 취급하지 않는다.

| 위험 | 관측·차단 기준 | 후속 대응 |
| --- | --- | --- |
| 상류 충돌을 묵인한 구현 | JPA/MyBatis 중 하나를 독자 선택, REQ-144 의미 변경 | 작업 중단·T001 정합화 승인 요청 |
| SQL 래퍼 전역 치환 | 계산규칙 TEXT까지 diff, 원본 변경 | 실행본 수정·전체 diff 재검토; 적용 migration은 재작성 금지 |
| 쓰기 범위 확장 | 수당/기준/대응 변경이 프로젝트·정산·마감값 변경 | T068 실패 처리·6테이블 경계 복구 |
| revision 누락·시각만 비교 | 동시 저장2건 성공 또는 NULL시각 값변경 미검출 | FX-REVISION 실패·그룹/키 집합 비교 복구 |
| 미승인 API shape 고정 |201 반환 id/204/삭제 전달위치를 원문 확정값으로 사용 | OQ-003/OQ-004 승인 전 client/server 완료 금지 |
| 실측 없는 합격 | DDL 파싱/문서 자체검사를 DB·API·브라우저 성공으로 보고 | 미검증 표기·실제 명령/종료코드/DB·이력·UI 증거 확보 |

완료 인계에는 승인된 기술 결정, SQL revision·원본/실행본 해시와 diff, 실제 DB 설치/초기값/재시작 검증,22연산 계약·오류·경합 시험, 브라우저15여정과 범위 회귀, 공통 Props 실제 사용처,2초 갱신·실패 구분, 실행/종료 방법과 미검증을 포함한다. 현재는 문서 작성 단계이며 제품 합격을 주장하지 않는다(REQ-145, REQ-146).

## 공통 계약 참조

승인 plan.md의 technology_stack·version_bom·required_outputs 값을 그대로 보존한다(REQ-138, REQ-139, REQ-140). 아래 표기는 OQ-001의 버전 충돌을 해소한 승인이 아니다.

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

실행·영속성 의미상 정본은 plan.md의 exact marker JSON이다. 이 문서에 별도 정본을 만들지 않는다. 블록 값 resolved/imperative/mybatis/blocking/explicit/conflicts=[]와 승인 원장 REQ-144·spec.md의 jpa 및 REQ-135의 JPA ddl-auto=validate가 충돌한다. OQ-007 해소 전 구현·영속성 시험을 완료로 처리하지 않는다. imperative/blocking 요청 흐름을 유지하고 R2DBC·WebFlux·worker·202/polling/callback을 임의 도입하지 않는다.

## 요구 회계

각 행은 이 문서의 설계 판단/검증 절차에 대한 연결이지 구현·실행 합격이 아니다. covered는 아래 지정한 연산 또는 기존 화면/테이블의 문서 내 증거를 의미한다. 실제 제품 결과는 미검증이며 미결은 별도 승인 전 충족으로 판단하지 않는다. 승인 원장 156개를 각1행으로 보존한다.

| canonical_id | status | evidence |
| --- | --- | --- |
| REQ-001 | data-constraint | D02·전체 SQL 원천 보존: HQ 예약 파트를 포함한 DDL 전체 및 data 전체가 실행본과 대조된다. |
| REQ-002 | data-constraint | D02·행정구역 빈 테이블: 설치 후 행정구역 테이블 데이터 건수 0행이다. |
| REQ-003 | data-constraint | D02·전체 DB 구조 보존: 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK 및 TB_SY_CALC_RULE 11컬럼이 일치한다. |
| REQ-004 | data-constraint | D02·기본 초기값 적재: 그룹64·시스템44·코드391·운영기준43·역할5·메뉴40·역할메뉴150·관리자/연결 각1·계산규칙94·HQ1행이며 세 빈 테이블은 0행이다. |
| REQ-005 | data-constraint | D02·초기 자금 기준과 계산규칙: CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 임의 월 마감 기록 0행이다. |
| REQ-006 | data-constraint | D02·Flyway 실행본 래퍼 정리: 실행본 diff에는 최상위 래퍼 제거만 있으며 revision/해시가 기록된다. |
| REQ-007 | data-constraint | D02·마이그레이션 불변·멱등성: 재시작 후 초기값 중복 0건, 미적용 파일은 원본 대조 결과를 갖는다. |
| REQ-008 | data-constraint | D02·설치 실패와 구조 은폐 금지: 실패 fixture는 해당 트랜잭션을 롤백하며 사용자 보유 DB 초기화 0건이다. |
| REQ-009 | data-constraint | D02·구조 차이 보고: 이름/타입 불일치 fixture에서 임의 스키마 수정 없이 불일치 목록이 보고된다. |
| REQ-010 | data-constraint | D03·설치 후 쓰기 경계: 수당/기준 변경 후 프로젝트 팀비·경비·정산금액 변경 0건이다. |
| REQ-011 | data-constraint | D03·서버 감사 필드와 연월 저장: REG_USER_ID=admin, REG_DTTM 기록; 화면/API YYYY-MM이 DB YYYYMM으로 저장된다. |
| REQ-012 | screen-only | D10·공통 틀/UI-27의 공통 컴포넌트 실제 재사용: 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다. |
| REQ-013 | covered | D05 — putPreference/createCodeGroup/saveGroupCodes: 문자열 입력 검증 — 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다. |
| REQ-014 | covered | D05 — createCodeGroup: 그룹ID 정규화 — 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다. |
| REQ-015 | covered | D05 — saveGroupCodes: 코드 정규화 — 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다. |
| REQ-016 | covered | D05 — createCodeGroup/saveGroupCodes: 명칭 경계와 식별자 불변 — 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다. |
| REQ-017 | covered | D05 — saveGroupCodes/saveOperatingStandards: 정밀 십진값 범위 — 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다. |
| REQ-018 | covered | D05 — saveGroupCodes/saveOperatingStandards: 숫자 입력 정리와 금지값 — 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다. |
| REQ-019 | covered | D05 — saveGroupCodes/saveOperatingStandards: 소수 원값 보존 — 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다. |
| REQ-020 | covered | D05 — saveGroupCodes: JOB 월단가 정수 — 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다. |
| REQ-021 | covered | D05 — getMonthClose/putPreference/saveOperatingStandards: 실제 연월 검증 — 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다. |
| REQ-022 | covered | D05 — putPreference/saveGroupCodes: Y/N·JSON 타입 — true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다. |
| REQ-023 | covered | D05 — saveGroupCodes/saveOperatingStandards: 빈 수당·기준값 구분 — 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다. |
| REQ-024 | screen-only | D05·공통 틀/UI-27의 공통 표시 함수: 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’; 표시 전후 저장값은 같다. |
| REQ-025 | covered | D03 — getCurrentUser: 임시 사용자 컨텍스트 — GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다. |
| REQ-026 | covered | D03 — putPreference/deletePreference/createCodeGroup: 처리자 주입 거부 — 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다. |
| REQ-027 | covered | D03 — getCurrentUser/putPreference/deletePreference: 인증·권한 유예 — /UI-27 직접 조회/저장 시 인증 리다이렉트·401·역할별403은 0건이다. |
| REQ-028 | covered | D04 — createCodeGroup/saveGroupCodes/deleteGroupCode: 원자적 업무 변경 — 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다. |
| REQ-029 | covered | D04 — putPreference/deletePreference/getGroupCodes: 불투명 revision — 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다. |
| REQ-030 | covered | D04 — putPreference/deletePreference/saveGroupCodes: 잠금 후 동시 수정 검출 — 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다. |
| REQ-031 | covered | D04 — putPreference/saveGroupCodes/saveOperatingStandards: 실제 변경 없는 저장 — 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다. |
| REQ-032 | covered | D04 — putPreference/deletePreference/createCodeGroup: commit 후 캐시 최신화 — 저장 브라우저 2초 내 최신값; 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다. |
| REQ-033 | screen-only | D10·공통 틀/UI-27의 한국어 오류와 재조회 실패 구분: 500/연결 실패는 오류 띠·재시도; commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다. |
| REQ-034 | screen-only | D10·공통 틀/UI-27의 중복 클릭·자동 재저장 금지: 처리 중 반복 클릭의 추가 요청0건; 실패 후 입력 유지·자동 재저장0회다. |
| REQ-035 | covered | D08 — getMenus: DB 메뉴 구성·정렬 — 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다. |
| REQ-036 | covered | D08 — UI-27: 메뉴 펼침·접힘 탐색 — 부모 이름은 화면 이동, 펼침 아이콘은 토글; 접힌 메뉴 키보드 focus에서 하위 목록이 보인다. |
| REQ-037 | covered | D08 — getMenus: 잘못된 메뉴 트리 진단 — 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외; DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다. |
| REQ-038 | covered | D08 — UI-27: 공식 라우트와 복구 — /UI-27 새로고침 및 #UI-27 replace 성공; 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다. |
| REQ-039 | screen-only | D08·공통 틀/UI-27의 PC 전용 화면 보호: 767px UI-27 본문 대신 안내·이동 수단; 폭 확대 시 미저장 편집이 복원된다. |
| REQ-040 | screen-only | D08·공통 틀/UI-27의 반응형 공통 레이아웃: 1280px 메뉴240/64px·여백24px; 1024px 기본접힘·16px, 360px 전체 가로넘침0이다. |
| REQ-041 | screen-only | D08·공통 틀/UI-27의 헤더의 실제 제공 항목: AI/알림 클릭은 준비 중이고 가짜 배지/대화0개; 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다. |
| REQ-042 | screen-only | D08·공통 틀/UI-27의 화면 제목·기간 배치: UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다. |
| REQ-043 | screen-only | D08·공통 틀/UI-27의 화면 탭과 LRU: 대시보드 닫기 불가; 9번째 취소 시 새 탭0개·기존8개 유지다. |
| REQ-044 | screen-only | D08·공통 틀/UI-27의 편집 보존과 이동 경고: 계속 작성 시 입력/필터/기간/스크롤 유지; 새로고침/닫기는 beforeunload·dirty 탭은 ●다. |
| REQ-045 | screen-only | D08·공통 틀/UI-27의 접근 가능한 모달·입력: 모달 내 Tab 순환·닫기 후 원래 포커스; 잘못된 입력 제출 시 첫 오류 필드로 이동한다. |
| REQ-046 | screen-only | D08·공통 틀/UI-27의 새로고침 탭 초기화: 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다. |
| REQ-047 | covered | D08 — getPreferences: 설정 조회와 기본값 — 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다. |
| REQ-048 | covered | D08 — putPreference: 설정 명시 저장 — 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. |
| REQ-049 | covered | D08 — getPreferences/putPreference: 손상 설정 격리 — 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다. |
| REQ-050 | covered | D06 — deletePreference: 개별 설정 초기화 — 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다. |
| REQ-051 | covered | D08 — getPreferences/putPreference: 즐겨찾기 편집 — 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다. |
| REQ-052 | screen-only | D08·공통 틀/UI-27의 최근 화면 자동 갱신: 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. |
| REQ-053 | covered | D08 — getPreferences/putPreference: 메뉴 접힘 기억 — PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다. |
| REQ-054 | covered | D08 — getPreferences/putPreference: 목록 크기 설정 — 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다. |
| REQ-055 | covered | D08 — getPreferences/putPreference: 밀도 설정 동기화 — 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다. |
| REQ-056 | covered | D08 — getPreferences/putPreference: 표 열 설정 저장 — 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다. |
| REQ-057 | covered | D08 — getPreferences/putPreference: 금액 단위 선택 기억 — 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다. |
| REQ-058 | covered | D08 — getPreferences/putPreference: 설정 화면ID·표시 보호 — 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다. |
| REQ-059 | covered | D08 — getPreferences/putPreference: UI-27 열 정의 제한 — codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다. |
| REQ-060 | covered | D08 — getPreferences/putPreference: 설정 적용과 실패 보존 — 설정 실패 시 이전 밀도 유지·오류 안내; 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다. |
| REQ-061 | covered | D09 — getPreferences/putPreference: 기간 설정 타입·범위 검사 — NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다. |
| REQ-062 | screen-only | D09·공통 틀/UI-27의 controlled 기간 부품: YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회; 범위 밖/disabled 이벤트0회·내부 DB호출0회다. |
| REQ-063 | covered | D09 — getPeriodContext: 화면 기간 선택·기억 — 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다. |
| REQ-064 | covered | D09 — getPeriodContext: 기간 범위와 운영 전 상태 — YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다. |
| REQ-065 | covered | D09 — getPeriodContext: 연도 배지 — 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다. |
| REQ-066 | covered | D09 — getMonthClose: 실제 월 마감 조회 — CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다. |
| REQ-067 | screen-only | D10·공통 틀/UI-27의 UI-27 네 탭과 조회 상태: 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다. |
| REQ-068 | covered | D11 — getGroupCodes/getCodeMappings/getOperatingStandards: 표 페이지 계약 — page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다. |
| REQ-069 | covered | D11 — getCodeGroups: 그룹 목록과 선택 — 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다. |
| REQ-070 | covered | D11 — getGroupCodes: 일반 코드 조회 — GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다. |
| REQ-071 | covered | D11 — saveGroupCodes: 일반 코드 일괄 변경 저장 — 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다. |
| REQ-072 | covered | D10 — UI-27: 편집 취소 복원 — 신규·기존 편집 후 취소하면 신규 행 제거·기존값 복원·DB 변경0건이다. |
| REQ-073 | covered | D11 — saveGroupCodes: 부분 변경·중복 코드 검사 — 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다. |
| REQ-074 | covered | D06 — saveGroupCodes/deleteGroupCode: 시스템 코드 변경 제한 — CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다. |
| REQ-075 | covered | D04 — createCodeGroup: 그룹 중복과 채번 경합 — 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다. |
| REQ-076 | covered | D11 — createCodeGroup: 업무 그룹 생성 — POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다. |
| REQ-077 | covered | D10 — UI-27: 그룹 추가 전 편집 처리 — 계속 작성이면 그룹 추가0건·편집 유지; 저장/취소 처리 후에만 그룹 추가가 진행된다. |
| REQ-078 | covered | D10 — saveGroupCodes: 일반 신규 코드 편집 행 — 창 추가 뒤 DB0건; 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다. |
| REQ-079 | covered | D06 — saveGroupCodes/deleteGroupCode: 그룹 내 코드 PK 재사용 금지 — DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다. |
| REQ-080 | covered | D11 — saveGroupCodes: 신규 코드 기본값·정렬 채번 — 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다. |
| REQ-081 | covered | D04 — saveGroupCodes: 정렬 순번 한계 — max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다. |
| REQ-082 | covered | D11 — saveGroupCodes: 코드명 중복 허용 — 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다. |
| REQ-083 | covered | D05 — saveGroupCodes: 그룹별 추가값 필수·길이 — 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. |
| REQ-084 | covered | D05 — saveGroupCodes: PROJ_STEP 진척 정수 — 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다. |
| REQ-085 | covered | D05 — saveGroupCodes: BANK 코드와 RSM 라벨 — 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다. |
| REQ-086 | covered | D05 — saveGroupCodes: RSM_LABEL 허용값 — name 입력은 NAME으로 저장; 목록 외 문자열은400이다. |
| REQ-087 | covered | D05 — saveGroupCodes: CSV 정규화·참조 — 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다. |
| REQ-088 | covered | D10 — getGroupCodes: 수당 코드 조회·편집 형태 — 일반 값 그룹에서 code/name/unitNm은 읽기 전용이고 값만 입력 가능하다. |
| REQ-089 | covered | D06 — saveGroupCodes/deleteGroupCode: 값 코드 잠금 — 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다. |
| REQ-090 | covered | D10 — saveGroupCodes: 수당 신규 코드 저장 — 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다. |
| REQ-091 | covered | D12 — saveGroupCodes/getCodes: 수당 값 변경과 업무 불변 — ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치; 값 변경 후 프로젝트 팀비/경비 변경0건이다. |
| REQ-092 | covered | D11 — saveGroupCodes/getCodes: 일반 코드 중지·재개 — 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다. |
| REQ-093 | covered | D11 — saveGroupCodes: 사용 상태 변경 이력 — USE_YN N 저장 후 STOP·Y 복원 후 RESUME가 이력 GET에서 조회된다. |
| REQ-094 | covered | D06 — deleteGroupCode: 삭제 참조 차단 — 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다. |
| REQ-095 | covered | D06 — deleteGroupCode: 일반 코드 논리 삭제 — 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다. |
| REQ-096 | covered | D06 — deleteGroupCode: 코드 삭제 이력 — 미참조 삭제 후 이력에 DEL_YN의 N→Y가 조회되고 실제 코드 행은 보존된다. |
| REQ-097 | covered | D06 — deleteGroupCode: 고정 참조 가드 — 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다. |
| REQ-098 | covered | D06 — deleteGroupCode: 삭제 직전 재검사 — 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다. |
| REQ-099 | covered | D07 — getOperatingStandards: 운영 기준 전체 조회 — 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다. |
| REQ-100 | covered | D07 — getOperatingStandardHistory: 기준 현재·과거·미래 이력 — 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다. |
| REQ-101 | covered | D07 — getOperatingStandards/saveOperatingStandards: 일반 기준 적용 최소월 — 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다. |
| REQ-102 | covered | D07 — saveOperatingStandards: 기준 월별 추가·덮어쓰기 — 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다. |
| REQ-103 | covered | D07 — saveOperatingStandards: 기준 변경 키 중복 거부 — 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다. |
| REQ-104 | covered | D07 — getOperatingStandards/saveOperatingStandards: 묶음 기준 표시 — AI8개는2줄이며 변경 칸만 같은 적용월 요청에 들어가고 DB는 각 STD_CD행이다. |
| REQ-105 | covered | D07 — saveOperatingStandards: DUN 순서 검증 — D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다. |
| REQ-106 | covered | D07 — saveOperatingStandards: 확률 범위 검증 — 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다. |
| REQ-107 | covered | D07 — saveOperatingStandards: AI 점수 합계 검증 — 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다. |
| REQ-108 | covered | D07 — saveOperatingStandards: 철수 통보일 정수 — 1과99,999,999,999 허용; 0 또는 소수는400이다. |
| REQ-109 | covered | D07 — getOperatingStandards/saveOperatingStandards: 자금 시작 연월 표현 — 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다. |
| REQ-110 | covered | D07 — saveOperatingStandards: 기준 전체 영향구간 검사 — 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다. |
| REQ-111 | covered | D07 — getOperatingStandards/saveOperatingStandards: 자금 두 키 즉시 갱신 — 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다. |
| REQ-112 | covered | D07 — saveOperatingStandards: 자금 기준 변경 이력 — 성공한 실제 값 변경은 UPD 전후값으로 조회; 미변경값은 이력0건이다. |
| REQ-113 | covered | D07 — getOperatingStandards/saveOperatingStandards: 기존 시작월 마감 잠금 — 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다. |
| REQ-114 | covered | D07 — saveOperatingStandards: 자금 동시 잠금·후속 갱신 — 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다. |
| REQ-115 | covered | D11 — getCodeMappings: 표기 대응 목록·검색 — SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다. |
| REQ-116 | covered | D11 — createCodeMapping: 대응 입력·중복 검사 — 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. |
| REQ-117 | covered | D11 — createCodeMapping: 대응 즉시 등록 — POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다. |
| REQ-118 | covered | D06 — createCodeMapping/deleteCodeMappings/getCommonCodeHistory: 대응 등록·삭제 이력 — NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다. |
| REQ-119 | covered | D06 — deleteCodeMappings: 대응 다중 물리 삭제 — 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다. |
| REQ-120 | covered | D04 — deleteCodeMappings: 대응 삭제 충돌 — 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다. |
| REQ-121 | covered | D12 — createCodeMapping/deleteCodeMappings: 대응 변경의 업무 불변 — 대응 추가/삭제 전후 기존 업무 테이블 값과 행수가 같다. |
| REQ-122 | covered | D11 — getCommonCodeHistory: 변경 이력 조회 — REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다. |
| REQ-123 | covered | D06 — saveGroupCodes/deleteGroupCode/createCodeMapping: 변경 유형과 과거 이름 추적 — 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다. |
| REQ-124 | covered | D11 — getCommonCodeHistory: 이력 필터 도메인 — 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다. |
| REQ-125 | covered | D06 — saveGroupCodes/deleteGroupCode/createCodeMapping: 이력 키·전후값 무손실 — 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다. |
| REQ-126 | covered | D11 — getCodes: 공통 active 코드 조회 — GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다. |
| REQ-127 | covered | D07 — getParameter: 월별 단일 기준 조회 — GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다. |
| REQ-128 | covered | D07 — getParameter: 기준 미존재 거부 — 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다. |
| REQ-129 | covered | D03 — getHealth/getCurrentUser/getMenus: API 필드와 서버 필드 보호 — SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다. |
| REQ-130 | covered | D03 — getHealth/getCurrentUser/getMenus: 공통 오류 반환과 비밀 보호 — 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다. |
| REQ-131 | data-constraint | D06·변경 이력 무기한 보존: 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. |
| REQ-132 | non-functional | D01·원문 구현 기술 요구: 고정 계약 Java17/3.3.x와 충돌한 두 버전은 OQ-001로 남고 임의 확정하지 않는다. |
| REQ-133 | non-functional | D12·3서비스 실행과 SPA: /UI-27 직접 새로고침 성공; compose3서비스 healthy·.env.example은 자리표시자/실행 안내만 포함한다. |
| REQ-134 | non-functional | D12·서울 시간대 일관성: 현재일 API와 DB 세션 및 화면 일시가 Asia/Seoul 기준으로 일치한다. |
| REQ-135 | non-functional | D05·Flyway 전용 생성/BigDecimal 유지; JPA ddl-auto=validate는 OQ-007 상류 정합화 승인 전 구현·시험 미확정 |
| REQ-136 | non-functional | D02·마이그레이션 파일 경로: backend/src/main/resources/db/migration/에서 V1 전체DDL 다음 V2 전체data가 적용된다. |
| REQ-137 | non-functional | D10·프론트 구조와 계층 책임: UI-27 실제 공통부품 사용·hook/client 통신·서버 검증이 있으며 HTML 전체 iframe0개다. |
| REQ-138 | non-functional | D01·고정 technology_stack: React+TypeScript+Vite, Java17+Spring Boot, PostgreSQL, docker·healthcheck_required=true가 유지된다. |
| REQ-139 | non-functional | D01·고정 Version BOM: BOM 표가 원문5개 값과 동일하며 충돌하는 Spring Boot3.5.x를 몰래 대입하지 않는다. |
| REQ-140 | non-functional | D01·고정 required_outputs: backend·frontend·infra/docker-compose.yml·/api/health 이름이 바뀌지 않는다. |
| REQ-141 | non-functional | D10·레퍼런스 시각 계약: sidebar/header/table/form 밀도·spacing/color/responsive 비교 증거가 있고 기능은 본문을 따른다. |
| REQ-142 | non-functional | D12·Docker 빌드 컨텍스트 제외: 각 context의 .dockerignore가 node_modules/.git/dist/build/.next/coverage를 제외한다. |
| REQ-143 | non-functional | D03·격리 환경만 배포: 인터넷 공개 배포 경로가 없고 실제 인증/권한 검증 전 공개 배포는 차단된다. |
| REQ-144 | non-functional | D01·plan.md exact mybatis 블록을 정본으로 참조하되 원장 REQ-144의 jpa 의미는 보존; OQ-007 충돌로 요구 충족·build-ready 미확정 |
| REQ-145 | non-functional | D01·실제 백엔드 산출물: backend/ 변경과 실제 migration 증거 없이 UI만 있으면 완료 불가다. |
| REQ-146 | non-functional | D12·프론트·API 함께 검증: 실제 API 저장 후 브라우저 재조회 결과와 DB 값이 일치하며 미실행은 미검증이다. |
| REQ-147 | out-of-scope | D10·후속 업무 화면 경계: UI-27 외 업무 화면은 제목·해당 기간 줄·다음 단계 안내만 제공한다. 확인: 다른 업무 화면의 가짜 수치·무동작 저장버튼0개, 계산/저장/업로드0개다. |
| REQ-148 | out-of-scope | D09·월 마감 쓰기 제외: 월 마감/해제/스냅샷 생성은 구현하지 않는다. 확인: 마감 상태 읽기는 존재하되 마감 변경 API/생성 동작0개다. |
| REQ-149 | out-of-scope | D10·외부·출력·외형 확장 제외: 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 생성하지 않는다. 확인: 헤더 준비 중 안내 외 AI/알림 업무0개·엑셀 파서/업로드/다운로드0개다. |
| REQ-150 | out-of-scope | D12·UI-20·단가·근태 제외: UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. 확인: 메뉴/UI등록표/API에 UI-20이 없고 일/시간 단가 및 근태 업로드 동작0개다. |
| REQ-151 | out-of-scope | D12·행정구역 파일 작업 제외: 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건에서 제외한다. 확인: 주소입력 기능/행정구역 적재작업 없이 DDL 제약과0행 상태로 검증한다. |
| REQ-152 | out-of-scope | D12·계산 엔진과 규칙 편집 제외: 계산규칙 테이블/데이터는 적재하되 편집 화면과 계산 실행 엔진은 생성하지 않는다. 확인: 94행 규칙 데이터가 있어도 규칙 편집 UI/계산 실행 API0개다. |
| REQ-153 | out-of-scope | D06·그룹 변경·삭제 제외: 그룹명/사용여부 변경과 그룹 삭제 기능은 제공하지 않는다. 확인: 그룹 추가/조회만 있고 그룹 수정/삭제 UI·API0개다. |
| REQ-154 | out-of-scope | D06·기준 키 CRUD 확장 제외: 운영 기준의 키·이름·단위·설명은 읽기 전용이며 신규 키/삭제는 제공하지 않는다. 확인: 값 변경 외 메타 수정/키 추가/키 삭제 API0개다. |
| REQ-155 | out-of-scope | D06·이력 변경·삭제 제외: 변경 이력은 조회만 제공하고 수정·삭제 API는 생성하지 않는다. 확인: 이력 GET만 존재하며 기존/오래된 이력 삭제·수정 경로0개다. |
| REQ-156 | covered | D11 — getHealth: 실제 DB health 조회 — 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다. |
