# 시스템 아키텍처 — 원가관리시스템 기반 구축·공통코드

## 시스템 context와 데이터 흐름

사용자는 접근 대상이 제한된 개발·검증 브라우저에서 공통 레이아웃과 `/UI-27`을 사용한다. 시스템 경계는 프론트/nginx, 단일 백엔드, PostgreSQL의 3서비스다. 브라우저가 DB에 직접 접근하지 않으며 외부 인증 서버·메일·AI·파일 처리·계산 엔진과의 연동은 이번 경계에 없다. SQL 저장소는 설치 원천이지 런타임 업무 조회 서비스가 아니다.

```text
[제한된 개발·검증 사용자 / 임시 관리자]
                 │ 화면 /{screenId}, /UI-27
                 ▼
[프론트/nginx: 정적 SPA + /api 프록시]
       │                         ▲
       │ 동기 HTTP               │ 저장 결과·조회·requestId
       ▼                         │
[controller → 공통 컨텍스트/검증 → 서비스 트랜잭션]
                 │ imperative / blocking
                 ▼
[영속성 adapter: 최종 매핑 기술은 OQ-007 승인 전 미확정]
                 │ 조회 / 잠금 / 본체+이력 원자 저장
                 ▼
[PostgreSQL: 설치 전체 68테이블 / 설치 후 업무 쓰기 6테이블]
                 ▲
[원본 전체 SQL → 실행 사본 → Flyway V1 → V2: 설치 경로만]
```

조회 흐름은 화면 → 부모 hook/client → nginx → controller → 서비스 → DB → 계약 응답 → 화면이다. 변경 흐름은 변경분·조회 revision 전달 → 입력 검사 → 잠금/최신 revision 비교 → 업무 보호·실제 차이 판정 → 본체와 이력 commit → 서버 캐시 무효화 → 현재 브라우저 관련 조회 갱신이다. 기간 부품과 공통 표는 직접 DB/저장 책임을 갖지 않는다. 상세 흐름의 실패 경계는 아래 트랜잭션·캐시 절에 명시한다.

이 문서는 SPEC-REQUEST.md 전체 476행과 승인 spec.md·plan.md·tasks.md·requirement-registry.json·data-model.md·contracts/openapi.yaml의 설계 투영이다. 승인 원장 156개와 Canonical Naming Ledger가 이미 존재하므로 새 원장 생성·재번호·의미 변경 없이 그대로 참조한다. `covered`는 설계 연결이며 제품 구현 완료가 아니다. 이번 산출물은 architecture.md뿐이고 실제 제품 코드·DB 설치·API·브라우저 실행 결과를 주장하지 않는다. 레퍼런스의 실제 관찰과 시각 상세는 부모 작업의 ui-design.md 책임이며 이 문서는 관찰 결과를 발명하지 않는다.

현재 판정은 **clarification required·구현 착수 차단·build-ready 아님**이다. OQ-001과 OQ-007은 이 문서로 해소되지 않는다. HTTP 제안의 OQ-003/OQ-004도 승인 전 확정 계약으로 승격하지 않는다.

## component 경계와 소유 책임

### 프론트·화면 경계

- AppLayout은 Header/Sidebar/ScreenTabs/TitleBar/본문을 조립한다. 메뉴 이름·아이콘·PC_ONLY·정렬은 getMenus의 DB 데이터, 등록표는 screenId/컴포넌트/기간 유형만 소유한다. 공식 `/{screenId}`, `/`→`/UI-16`, `#UI-xx` replace를 유지한다. UI-27의 네 탭은 하나의 `/UI-27`이며 자식 screenId/route를 만들지 않는다(REQ-035~REQ-038, REQ-067).
- 탭 store는 대시보드 포함 최대8개·대시보드 맨 앞/닫기 불가·9번째 닫을 수 있는 LRU 확인·dirty●·beforeunload를 소유한다. 일반 탭 전환은 편집/필터/기간/스크롤을 보존한다. 그룹/하위탭 변경·닫기·폐기 이동에서만 미저장 확인을 적용하며 취소는 상태를 보존한다. 새로고침은 URL 화면+고정 대시보드로 시작하고 열린 탭 목록 자체를 서버 복원하지 않는다(REQ-043~REQ-046).
- Header는 메뉴 토글·로고/대시보드 이동·AI/알림 준비 중 안내·임시 관리자/화면 설정만 제공한다. 가짜 배지·대화·비밀번호·로그아웃·타이머는 없다. TitleBar의 업무 기간은 제목 아래 본문에 두되 UI-27은 자체 적용월만 사용한다(REQ-041/REQ-042).
- 헤더56px, PC≥1280 메뉴240/64px·여백24px, 태블릿768~1279 기본접힘·여백16px, 모바일≤767 슬라이드 메뉴·화면탭 없음이다. 최소360px 전체 가로넘침0, 넓은 표 내부 스크롤이다. PC_ONLY 초기 UI-10/14/23/24/25/27은 768px 미만 메뉴/FAV/RECENT에서 표시만 숨기고 직접 접근은 PC 안내로 바꾼다. 이는 역할403이 아니며 미저장 상태를 유지하여 폭 확대 시 복원한다(REQ-039/REQ-040).
- DataTable<T>는 rows/columns/rowKey/loading/empty/page/size/total과 페이지·선택·행 작업·열 설정 이벤트를 받는다. 코드/대응/기준/이력에서 공유하며 화면이 renderer와 업무 분기를 소유한다. Text/Decimal/CodeSelect는 controlled 입력, Dialog/ConfirmDialog는 pending/focus trap/복원, ErrorBanner/Toast/StatusBadge는 부모 결과와 재시도 이벤트만 소유한다. label/Tab/첫 오류 포커스를 제공한다(REQ-012/REQ-045).
- `frontend/src/components/{layout,common}/`, `src/screens/common-codes/`, `src/hooks/`, `src/lib/format.ts` 경계를 유지한다. API 호출은 hook/client, 검증/조회/저장은 서버다. iframe·DOM 문자열 복제·시연 고정 날짜/인물/금액·브라우저 배열의 운영 저장소 사용은 금지한다(REQ-137/REQ-141).

### 백엔드 경계

controller는 OpenAPI의 입력·출력·오류 경계만 담당하고 도메인 보호를 화면에 위임하지 않는다. 서비스는 정규화·모드·참조·마감·묶음 검증과 트랜잭션을 소유한다. CurrentUserProvider는 처리자와 현재 권한 컨텍스트를 한곳에서 공급한다. CodeReferenceGuard는 고정 참조 매핑, CodeQueryService/useCodes는 active 목록과 기존 label 조회, OperatingStandardService.get(key,month)는 유효 기준 조회를 담당한다. 공통 revision/이력 저장기는 서비스 트랜잭션 안에서 동작한다. 이들은 단일 백엔드 내부 책임 분리이며 독립 마이크로서비스나 새 endpoint 명칭이 아니다.

영속성 adapter는 blocking 접근·잠금·DB 제약 오류 변환을 담당하되 MyBatis mapper/JPA entity·repository·잠금 annotation/SQL의 최종 선택은 OQ-007 해소 전 확정하지 않는다. 업무 SQL 식별자는 따옴표 없이 사용하고 값은 바인딩한다. 요청 테이블명으로 SQL을 조합하지 않는다. WebFlux/R2DBC·worker·메시지브로커·202 polling/callback을 추가하지 않는다.

## API boundaries와 데이터 연결

다음 operationId는 실제 contracts/openapi.yaml의 연산만 참조한다. 요청/응답 schema·enum·필드 required·응답 봉투는 그 파일이 소유하며 이 문서에서 재정의하지 않는다. 물리 필드·키·관계는 data-model.md를 참조한다. 아래 Data Entity는 새로운 엔터티 선언이 아니라 실제 table 참조다.

| Component | OpenAPI operationId 또는 path | Data Entity | canonical_id |
| --- | --- | --- | --- |
| DB 연결 점검 | getHealth | N/A — 실제 DB SELECT | REQ-156 |
| CurrentUserProvider | getCurrentUser | TB_SY_USER, TB_SY_ROLE, TB_SY_USER_ROLE | REQ-025, REQ-027 |
| DB 메뉴 조회·진단 | getMenus | TB_SY_MENU | REQ-035, REQ-037 |
| 기간 context 조회 | getPeriodContext | TB_SY_OPER_STD, TB_CL_CLOSE | REQ-063, REQ-064, REQ-065 |
| 마감 조회 전용 | getMonthClose | TB_CL_CLOSE, TB_SY_OPER_STD | REQ-021, REQ-066 |
| 설정 조회 | getPreferences | TB_SY_USER_PREF | REQ-047, REQ-049 |
| 설정 저장 | putPreference | TB_SY_USER_PREF | REQ-048, REQ-051, REQ-052, REQ-053, REQ-054, REQ-055, REQ-056, REQ-057, REQ-058, REQ-059, REQ-060, REQ-061 |
| 설정 개별 초기화 | deletePreference | TB_SY_USER_PREF | REQ-050 |
| 그룹 목록 | getCodeGroups | TB_SY_CODE_GRP, TB_SY_CODE | REQ-069 |
| 그룹 생성 | createCodeGroup | TB_SY_CODE_GRP, TB_SY_CHG_HIST | REQ-014, REQ-075, REQ-076 |
| 그룹 코드 조회 | getGroupCodes | TB_SY_CODE_GRP, TB_SY_CODE | REQ-029, REQ-068, REQ-070, REQ-088 |
| 그룹 코드 저장 | saveGroupCodes | TB_SY_CODE_GRP, TB_SY_CODE, TB_SY_CHG_HIST | REQ-071, REQ-073, REQ-074, REQ-079, REQ-080, REQ-081, REQ-082, REQ-083, REQ-084, REQ-085, REQ-086, REQ-087, REQ-089, REQ-090, REQ-091, REQ-092, REQ-093 |
| CodeReferenceGuard·논리 삭제 | deleteGroupCode | TB_SY_CODE, TB_SY_CODE_MAP, TB_SY_CHG_HIST; 고정 업무 참조 조회 | REQ-094, REQ-095, REQ-096, REQ-097, REQ-098 |
| CodeQueryService | getCodes | TB_SY_CODE | REQ-126 |
| 대응 검색 | getCodeMappings | TB_SY_CODE_MAP, TB_SY_CODE | REQ-115 |
| 대응 즉시 등록 | createCodeMapping | TB_SY_CODE_MAP, TB_SY_CHG_HIST | REQ-116, REQ-117, REQ-118, REQ-121 |
| 대응 다중 물리 삭제 | deleteCodeMappings | TB_SY_CODE_MAP, TB_SY_CHG_HIST | REQ-119, REQ-120 |
| 기준 전체 조회 | getOperatingStandards | TB_SY_OPER_STD | REQ-099, REQ-100, REQ-104 |
| 기준 원자 저장 | saveOperatingStandards | TB_SY_OPER_STD, TB_SY_CHG_HIST; TB_CL_CLOSE 조회 | REQ-101, REQ-102, REQ-103, REQ-105, REQ-106, REQ-107, REQ-108, REQ-109, REQ-110, REQ-111, REQ-112, REQ-113, REQ-114 |
| 기준 적용 이력 조회 | getOperatingStandardHistory | TB_SY_OPER_STD | REQ-100 |
| OperatingStandardService.get | getParameter | TB_SY_OPER_STD | REQ-127, REQ-128 |
| 변경 이력 조회 | getCommonCodeHistory | TB_SY_CHG_HIST, TB_SY_CODE_GRP | REQ-122, REQ-123, REQ-124, REQ-125, REQ-131 |

공통 `/api`와 JSON camelCase·십진 문자열·목록 page≥1/size20·50·100/기본50·total과 페이지 행수 구분을 유지한다. 운영 기준은 43키 전체 비페이지 조회다. 기존 값 변경의 expectedRevision, 미변경 필드 미전송, 서버 처리자/SYS/VAL/DEL/등록시각 주입400, 모드상 금지409를 controller와 서비스에서 분리한다(REQ-068/REQ-129).

400 VALIDATION_ERROR/INVALID_PERIOD/UNKNOWN_FIELD, 404 NOT_FOUND/STANDARD_NOT_FOUND, 업무409 STALE_WRITE/DUPLICATE_GROUP/DUPLICATE_GROUP_NAME/DUPLICATE_CODE/DUPLICATE_MAPPING/SYSTEM_CODE_LOCKED/VALUE_CODE_FIELD_LOCKED/CODE_IN_USE/CLOSED_MONTH/ORDER_LIMIT, 500 INTERNAL_ERROR, 503 DB_UNAVAILABLE를 계약에 따라 처리한다. 인증401·역할403을 추가해 업무409를 대체하지 않는다. health의 200/503 인프라 예외를 업무 GET의 검증400/미존재404에 확대하지 않는다(REQ-130/REQ-156).

OQ-003은 ApiResponse/ApiError의 success/meta와 원문 오류 필드 배치 승인 대기다. OpenAPI의 루트 오류 배치는 설계 제안이며 이 문서가 확정하지 않는다. OQ-004의 생성201 본문 없음·삭제204·DELETE revision query 위치·없는 설정 초기화·저장 후 재조회 범위도 제안 상태다. 생성의 새 revision을 응답 본문에서 반드시 얻는다고 가정하지 말고 해당 GET으로 다시 읽는다. 제안을 이유로 새로운 응답 schema나 상태코드를 만들지 않는다.

## blocking imperative persistence와 트랜잭션

### 공통 저장 순서와 충돌·no-op

1. 동기 요청의 허용 필드·타입·길이·정규화를 검사하고 처리자는 CurrentUserProvider에서 취득한다. 화면 값은 신뢰 경계 밖이다.
2. 요청이 소유하는 대상·연관 검증 범위를 정하고 키 오름차순으로 잠근다. 그룹 저장은 그룹+소속 코드 전체, 기준키는 전체 적용월 이력, 묶음은 미변경 키도 포함한다. 잠금 없는 사전 화면 검사만으로 저장을 허용하지 않는다.
3. 잠금 후 최신 식별자·저장값·REG_DTTM/MOD_DTTM으로 불투명 revision을 계산하고 expectedRevision을 비교한다. 그룹은 그룹+전체 코드, 기준은 키의 모든 적용월이 비교 범위다. 양쪽NULL은 시각만 같고 값이 달라지면 revision은 다르다. 한쪽NULL도 다르다. version 컬럼을 추가하지 않는다.
4. 불일치409 STALE_WRITE이면 쓰기 전 종료한다. 같은 토큰의 두 요청은 선행1건 성공·후행1건409, 후행 데이터/이력0건이다. 모드·참조·마감·묶음 경계 검사 실패도 본체와 이력 전체 롤백이다.
5. revision 일치 후 실제 변경0이면200 changed=false·“바뀐 내용이 없습니다.”, MOD_DTTM과 이력 갱신0건이다. 실제 변경만 본체·이력 함께 저장하여 commit 후 changed=true·조회 결과로 연결한다. 최초 설정의 명시 저장은 기본값과 같아도 INSERT하는 별도 규칙이다.
6. commit 이후에만 서버 캐시를 무효화한다. flush·SQL 일부 성공·HTTP 전송 전 상태를 commit 성공으로 오인하지 않는다. 이력 저장 실패도 같은 트랜잭션 전체를 롤백한다. 프레임워크별 구현 문법은 승인 후 선택하며 이 단계에서 annotation/mapper를 확정하지 않는다(REQ-028~REQ-032, T013/T014).

### 연산별 원자 단위

| 연산 경계 | 잠금·검증 범위 | 본체/이력 결과와 실패 경계 |
| --- | --- | --- |
| createCodeGroup | 그룹 테이블 직렬화 잠금·trim 동일명·ID·max+1 경합 검사 | SYS=N/VAL=N/USE=Y·NEW 함께 저장; DUPLICATE_GROUP/NAME409. 이름 UK를 임의로 추가하지 않음 |
| saveGroupCodes | 한 그룹·전체 소속 코드·같은 요청 신규 참조 최종 상태 | 변경분만 반영·다른 페이지 보존·동일 code 중복400; 신규 전달 순서 max+1·NUMERIC(5) 초과 ORDER_LIMIT409 전체 롤백 |
| deleteGroupCode | 그룹/대상 행 잠금 후 CodeReferenceGuard 재검사 | 미참조 일반 코드만 DEL_YN=Y·DEL; 물리행/PK 보존. 참조1건 이상409 CODE_IN_USE·유형/건수·사용중지 안내 |
| saveOperatingStandards 일반 | 변경키 전체 이력·묶음 미변경키·해당월/영향구간/이후 예정 경계 | 같은 적용월 UPDATE/없으면 INSERT, 다른월 유지·중복키400. 최소월 전409·묶음 위반400 모두 롤백 |
| saveOperatingStandards 자금 두 키 | CASH_BASE_YM/AMT 두행 함께 잠금·기존 시작월 CLOSE 재검사 | 키당1행 즉시 UPDATE·원래 APLY_BGN_YM 보존·실제 변경 UPD. effectiveMonth/일반키 혼합400·기존월 CLOSE409 |
| createCodeMapping | 실제 그룹·active 미삭제 코드·UK(sourceText,groupId) 검증 | 즉시 INSERT/NEW; 동일표기+동일그룹은 코드가 달라도 DUPLICATE_MAPPING409·다른그룹 허용 |
| deleteCodeMappings | mapId 키 순 잠금·모든 expectedRevision/대상존재 확인 | 다중 실제 DELETE/DEL 함께 commit; 한 행 누락/변경409이면 전체삭제/이력0건 |
| putPreference/deletePreference | admin/type/key 단일 설정·최초/기존 revision 검사 | 최초 null/기본값도 INSERT·동시최초 PK충돌409 STALE_WRITE. 초기화는 지정행만 DELETE·다른설정 보존·UI-27 이력0건 |

### 코드 모드·입력과 SQL integrity

SYS_CODE_YN=Y 규칙을 VAL_CODE_YN보다 먼저 적용한다. 시스템+값 그룹도 기존 이름만 변경 가능하고 값 변경은 SYSTEM_CODE_LOCKED409다. 일반 값코드는 기존 codeVal만 변경 가능·그 밖의 변경/삭제 VALUE_CODE_FIELD_LOCKED409다. 일반 코드는 이름/useYn/허용 addVal만 편집한다. 코드명 중복은 허용하되 삭제PK도 DUPLICATE_CODE409로 재사용하지 않는다. 신규 모달은 로컬 편집 행이며 하단 saveGroupCodes 전 DB0건이다. 일반 중지/재개는 STOP/RESUME, active 옵션에서 제외하되 includeInactive label 조회는 유지하고 타 업무 저장코드는 변경하지 않는다(REQ-071~REQ-093).

문자열 trim·그룹ID `^[A-Z][A-Z0-9_]{0,29}$`·코드 `^[A-Z0-9_]{1,20}$` 대문자 정규화·명칭50/100자·등록 PK 불변을 서버에서 검사한다. CODE_VAL/STD_VAL은 0~99,999,999,999.9999·정수11자리/소수4자리·BigDecimal과 숫자 문자열로 유지하며 float/double·반올림/버림은0회다. 공백/쉼표/끝 원·%만 정리하고 음수/지수/NaN/Infinity/다른 문자는400이다. 수당 기본/빈값0, 기준 빈칸400이며 단위%만으로100 상한을 만들지 않는다. JOB.ADD_VAL2는 최대999,999,999,999,999 정수 문자열이다. Y/N·JSON true/false·PREF_VAL2000자·연월0001-01~9999-12를 엄격히 검사한다. 초과값·이력값을 잘라 저장하지 않는다(REQ-013~REQ-023).

JOB/SKILL/RSM_JOB_KW의 CSV는 trim/대문자·빈항목거부·첫 등장 순 중복제거·실재 미삭제 참조 검사다. 신규 참조 active, 기존 중지 참조 유지, 같은 요청 신규 JOB 최종상태와 JOB 자기참조를 허용한다. PROJ_STEP은 진척0~100 정수/CTRT_TYPE CSV, BANK는 앞0 보존 정확히3숫자, RSM_LABEL은 NAME/TEL/MAIL/ADDR/SKILL/WANT/FROM/VNDR다. 추가값은 적용 그룹별 필수·100자 이하, 다른 그룹은 NULL·ADD_VAL3 미사용이며 다른 그룹 필드 주입400이다(REQ-083~REQ-087).

DB의 복합PK·FK·UK·CHECK·NOT NULL·NUMERIC·시퀀스를 애플리케이션 사전검사로 대체하지 않는다. 제약은 원본 그대로 유지하고 경합 후 DB 오류도 계약 코드로 변환한다. 이름 중복은 직렬화 잠금으로 검사하며 임의 CODE_GRP_NM UK를 추가하지 않는다. SQL 식별자·table/column은 data-model.md의 전체 필드/키·관계 계약을 사용하고 테이블/필드를 추정 생성하지 않는다.

CodeReferenceGuard는 data-model.md의 ‘삭제 참조 가드 — FK와 별도인 논리 참조’ 전수 매핑을 사용한다. JOB의 업무 JOB_CD/SUB_JOB_CD·다른 미삭제 코드 CSV, GRADE·JBPS·SGA_ITEM·DRCT_EXPS_ITEM·CHG_ITEM·IMPR·PROJ_STEP·CNTC_STAT·CAND_SRC·ASGN_CHG_RSN·LOSE_RSN·RCMD_EXCL·BANK·INDUTY의 고정 컬럼, EXT_PSBL의 EXTP BFR_VAL/AFT_VAL 코드/이름, SKILL의 현재명과 CODE_NM 과거명 참조를 빠뜨리지 않는다. 모든 그룹은 TB_SY_CODE_MAP도 확인하고 취소/중지/마감 자료도 포함한다. 자기 NEW만 참조 사용으로 세지 않으며 ALWNC는 삭제 대상이 아니다. 동적 요청 테이블 SQL은 금지한다(REQ-094~REQ-098/REQ-123, T042~T044).

### 운영 기준의 시간 경계

43키는 STD_CD순 비페이지·키/이름/단위/설명 읽기 전용이다. 현재값은 현재월 이하 최신행, 적용 이력은 전체 DESC·현재/예정/지난이며 미래값을 현재에 쓰지 않는다. 기준 없음을0/하드코딩으로 바꾸지 않고 getParameter는404 STANDARD_NOT_FOUND다. 일반 최소월=max(CASH_BASE_YM,마지막 CLOSE+1), 기본월=max(현재월,최소월)이다. 같은월 덮어쓰기의 월/전후 확인은 UI가 소유하고 서버는 expectedRevision으로 보호한다(REQ-099~REQ-103/REQ-127/REQ-128).

DUN_D1/2/3은 D1≥1·D1<D2<D3, EXT_PRBL/STEP_PRBL은 각0~100, AI_SCORE_JOB/SKILL/CUST/GRADE/PERF/COST/AVAIL/LOC는 각0~100·합 정확히100, WTHDR_NOTI_D는1~99,999,999,999 정수다. DUN/EXT/STEP/BILL_MISS/AI 묶음은 미변경 키도 잠그고 변경 적용월 이후 유효구간과 예정 경계마다 검증한다. AI8개2줄·줄당 최대4입력·변경칸만 같은월 전송은 화면 책임이며 DB는 각 STD_CD행이다. 미래 경계 하나라도 위반하면400 전체롤백이다(REQ-104~REQ-110).

CASH_BASE_YM은 화면 YYYY-MM/API value YYYYMM6자리/DB 정수다. CASH_BASE_AMT는 일반 정밀도다. 두행 잠금 후 **기존 CASH_BASE_YM**의 CLOSE를 다시 검사하여 새월로 잠금을 우회하지 못하게 한다. 성공 후 period-context·기준 조회만 갱신하고 자금계산·마감 자료는 변경하지 않는다(REQ-109/REQ-111~REQ-114).

## 데이터 설치·쓰기·보존 경계

### 설치 원천과 마이그레이션

example/project_ddl.sql 전체·마지막 HQ 예약 파트는 `backend/src/main/resources/db/migration/V1__full_schema.sql`, project_data.sql 전체는 같은 경로 `V2__base_data.sql`로 순서 보존한다. data 최상위 BEGIN2/COMMIT1만 실행 사본에서 제거하고 계산규칙 TEXT 안 문자열은 치환하지 않는다. 원본/실행본 diff·revision/해시를 기록한다. 적용 migration 수정·재실행 금지, 재시작 중복0건이며 기존 동일 역할은 재사용·완성한다. 설치 실패는 해당 트랜잭션 rollback·지정 독립 개발 DB에서만 수정/검증하고 사용자 다른 DB 초기화·임의 DROP/TRUNCATE·자동 baseline으로 불일치 은폐는 금지한다. 구조 차이는 이름/타입 추정 변경 대신 차이와 실패 항목을 보고한다(REQ-001/REQ-006~REQ-009/REQ-136).

data-model.md는 DDL revision `d1e18c27afb077bfa5b1d92b982af194676ebd91`, BOM 제거 텍스트 SHA-256 `96729f305751ab102c430451b99c7edb22e983bd4c234ec6bbfea6d2bcc1bb55`와 정의 파싱 결과를 기록한다. 이는 해당 문서의 읽기 대조 기록을 인용한 것이며 이번 호출의 원격 재확인·실제 PostgreSQL 설치 결과가 아니다. OQ-002는 DDL 물리명 확보 범위만 보완되었고 data 전체 revision/diff·seed·DB 실측은 미검증이다.

설치 기대치는 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK·TB_SY_CALC_RULE11컬럼이다. NULL/DEFAULT/PK/컬럼타입/코멘트도 대조한다. 초기 그룹64(시스템44)·코드391·기준43·역할5·메뉴40(10묶음+30화면)·역할메뉴150·admin/역할연결 각1·계산규칙94·HQ1이며 HQ는 DDL에서만 적재한다. TB_SY_ADM_AREA/TB_SY_ADM_HDONG은 제약만 생성·0행, TB_SY_CODE_MAP/TB_SY_CHG_HIST/TB_SY_USER_PREF도 빈 상태다. CASH_BASE_YM=202601/CASH_BASE_AMT=1150000000이며 가상 마감행을 만들지 않는다. 이는 T006~T008 후속 실측 기대값이다(REQ-002~REQ-005).

### 설치 이후 쓰기 allowlist

| 실제 table | 허용 쓰기·삭제 정책 | 금지되는 확장 |
| --- | --- | --- |
| TB_SY_CODE_GRP | 일반 업무 그룹 INSERT·NEW 연동 | 그룹명/사용 변경·삭제 없음 |
| TB_SY_CODE | 모드별 INSERT/UPDATE·일반 미참조 DEL_YN=Y | 물리 삭제·PK 재사용·타업무 코드 치환 없음 |
| TB_SY_CODE_MAP | 즉시 INSERT·선택 실제 DELETE·전후 이력 | 수정 API·업무자료 일괄 변경 없음 |
| TB_SY_OPER_STD | 일반 적용월 INSERT/UPDATE·자금 두키 동일행 UPDATE | 키 신규/삭제·메타 수정·계산 없음 |
| TB_SY_USER_PREF | admin 설정 upsert·해당키 실제 DELETE | 타사용자 지정·UI-27 감사이력 없음 |
| TB_SY_CHG_HIST | 위 도메인 실제 변경 시 INSERT만 | 수정·삭제·보존기간 만료 정리 없음 |

TB_SY_USER/ROLE/USER_ROLE·TB_SY_MENU·TB_CL_CLOSE·프로젝트 및 그 밖의 설치 테이블은 조회/설치 전용이다. 특히 TB_SY_ACCS_LOG 같은 추가 로그 테이블에도 런타임 DML을 발명하지 않는다. 수당/기준/대응 변경으로 프로젝트 팀비·경비·정산금액·마감 자료 변경은0건이다. DB 감사필드 REG_USER_ID=admin·REG_DTTM 서버기록, MOD_USER_ID/MOD_DTTM nullable·서버기록, 이력에는 DDL 등록필드만 사용한다. 화면/API 연월 YYYY-MM↔DB YYYYMM 변환에서 CASH value의 별도6자리 계약을 보존한다(REQ-010/REQ-011/REQ-026).

### 변경 이력 무기한 보존

TB_SY_CHG_HIST는 GROUP/CODE/VALUE_CODE/OPER_STD/CODE_MAP만 조회하며 NEW/UPD/STOP/RESUME/DEL을 기록한다. codeName 변경 항목은 CODE_NM으로 고정한다. TRGT_TBL_NM은 실제 table, TRGT_KEY는 그룹 groupId·코드 groupId:code·대응 groupId:mapId·기준 key:YYYYMM이다. UPD는 변경 컬럼별 전후값, STOP/RESUME는 USE_YN, 코드 DEL은 DEL_YN N→Y, NEW는 등록/이전NULL/이후 식별내용이다. 대응 NEW/DEL은 sourceText/groupId/code/codeName JSON을 각각 이후/이전에 보존하고 반대값NULL이다. 물리삭제 후에도 정확한 접두 구분자로 groupId 필터가 동작해야 한다. 500자 초과 조용한 절단은 금지한다(REQ-118/REQ-123/REQ-125).

getCommonCodeHistory는 targetType 생략전체·허용5값/잘못된값400, groupId는 그룹/코드/대응만·운영기준 제외, REG_DTTM DESC/HIST_ID DESC로 조회한다. 긴 값 펼침과 SYSTEM=시스템/admin=임시 관리자는 UI 책임이다. 보존 기한 없이 유지하고 기간 삭제 job·수정/삭제 API를 만들지 않는다. 이력 무기한 보존을 다른 설치 테이블의 보존정책으로 일반화하지 않는다(REQ-122/REQ-124/REQ-131/REQ-155).

## 설정·기간·캐시의 일관성

설정 정본은 TB_SY_USER_PREF이며 localStorage만으로 충족하지 않는다. GET의 행없음은 stored=false/기본값, MENU_FOLD만 null·PC false/태블릿 true다. 손상 JSON은 해당 항목 오류/revision+임시 기본값으로 격리하고 자동 삭제하지 않는다. 최초 expectedRevision=null·기존 토큰, 초기화는 해당행만 삭제하고 다른 설정 유지다(REQ-047~REQ-050).

FAV는 중복없는 등록 screenId 최대10·▲▼순서·11번째 거부, RECENT는 재방문 선두/중복제거/최대5/뒤제거다. PC 숨김은 원배열 삭제가 아니다. 접힘 메뉴는 두 목록을 숨기고 빈안내는 원문을 따른다. PAGE_SIZE 정수20/50/100 기본50·문자열50 거부, DENSE 넓게/좁게 기본넓게, AMT_UNIT 원/천원/백만원 기본천원은 저장/복원만·실제 금액 변환 없음이다. COLS는 codes/valueCodes/mappings/standards/history의 전체 order 순열·선택 hidden과 필수열을 data-model.md/원문대로 검증하고 체크/작업열·미등록열·표 없는 타화면은 제외한다. 필터/페이지/열/밀도 변경도 미저장 편집을 지우지 않는다(REQ-051~REQ-060).

PeriodSelector는 type YEAR/YEAR_MONTH·value·onChange·disabled/min/max/ariaLabel을 부모가 소유하는 controlled 부품이다. 내부 DB/설정 저장0회·범위밖/disabled 이벤트0회·유효선택 이벤트1회다. ScreenPeriodBar 부모는 getPeriodContext/getMonthClose와 PERIOD 설정을 연결한다. YEAR/YEAR_MONTH/YEAR_CUM/NONE 화면 등록 매핑은 승인 spec.md를 유지하며 UI-27은 NONE·PERIOD 쓰기400이다. 누계 year/month 일치·연도변경 후 월 제한·1~n월 누계를 유지한다(REQ-061~REQ-063).

YEAR는 시작연도~현재연도+1, YEAR_MONTH는 CASH_BASE_YM~현재월, 누계는 시작연도~현재연도(UI-07/08/09만 시작연도-1 허용), 현재연도 기준월은 현재월까지만이다. 기본기간은 현재일을 범위 내 제한, 역전은 운영 시작 전/선택비활성/PERIOD저장0이다. 저장기간이 새범위 밖이면 DB 자동수정 없이 안내/기본기간 임시표시 후 사용자가 다시 선택할 때 저장한다. 연도배지는 날짜비교, isClosed는 시작월 이전 이관·결산완료/CLOSE 마감/WORK·행없음 작업중이다. 현재일·DB 조회만 사용하고 업무계산·재계산·마감쓰기·가상 마감행은 없다(REQ-064~REQ-066).

정상 저장 **성공 응답 수신 후 2초 이내** 저장 브라우저의 관련 hook/client 캐시·열린 코드선택목록·설정을 최신화한다. 서버 캐시는 commit 이후 무효화하고 다른 브라우저는 다음 조회에서 최신 DB 값을 받는다. 코드/대응/기준/설정의 관련 조회만 갱신하며 CASH 변경은 기간 context도 포함한다. 별도 cache 서버·push 동기화·임의 TTL은 추가하지 않는다. 설정 실패는 이전 적용값·입력 보존, 통신 오류는 오래된 값의 성공 표시 금지다. commit 후 재조회만 실패하면 “저장됐지만 다시 불러오지 못했습니다.”로 구분하고 자동 재저장/재시도0회다. pending은 중복클릭을 차단하고 재조회 재시도는 사용자 명시 조작으로만 수행한다(REQ-032~REQ-034/REQ-060, T067).

## 배포·실행 환경

`infra/docker-compose.yml`은 프론트/nginx·백엔드·PostgreSQL16의 정확히3서비스로 구성하는 후속 구현 대상이다. nginx는 정적 SPA와 `/api` 프록시를 분리하여 `/UI-27` 직접 접근/새로고침에 SPA fallback을 제공하고 API 오류를 index HTML로 숨기지 않는다. DB 연결값은 환경변수, `.env.example`은 자리표시자·실행/종료 안내만 제공한다. 구체 포트·서비스명·network/volume명·Java package·runner는 실제 저장소 manifest 확인 전 추정하지 않는다(REQ-133/REQ-140).

각 실제 compose build context에는 `.dockerignore`를 두고 node_modules/.git/dist/build/.next/coverage를 제외한다. 서버/DB 세션·현재일/연월·화면 일시는 Asia/Seoul이다. 독립 개발 DB의 지속 데이터를 사용하고 `down` 종료에 볼륨 삭제를 기본으로 붙이지 않는다. 3서비스 healthcheck와 getHealth의 실제 DB SELECT를 함께 검증한다. 프로세스 생존만으로200을 반환하지 않고 연결 실패는503 DB_UNAVAILABLE다(REQ-134/REQ-142/REQ-156).

실행/종료 인계는 T071/T072의 실제 `docker compose -f infra/docker-compose.yml config`, `up --build -d`, `ps`, `curl -i "$BASE_URL/api/health"`, `down` 관측으로 확인한다. 이는 후속 명령 계약이지 이번 실행 결과가 아니다. BASE_URL은 실제 제한된 nginx 주소를 확인해 지정한다. 제품 manifest/Gradle wrapper·시험 runner를 확인한 후 빌드/시험 명령을 정하며 존재하지 않는 npm script를 발명하지 않는다.

## 보안·신뢰 경계

CurrentUserProvider는 DB admin·roleIds=[ADMIN]·표시명 임시 관리자·developmentMode=true·현재 허용/DataScope=ALL을 공급한다. 처리자/admin 설정 USER_ID는 요청에서 받지 않으며 각 업무 로직에 admin을 복제하지 않는다. 공급자 교체는 한곳에서 가능하게 둔다. 헤더에 개발용을 표시하고 초기 관리자 비밀번호/메일·DB 접속 비밀은 프론트로 내보내지 않는다(REQ-025~REQ-027).

이번에는 로그인·인증 필터·리다이렉트·인증401·역할403·민감칸 가림을 구현하지 않는다. OpenAPI security=[]와 비어 있는 securitySchemes를 임의로 인증 적용 상태로 바꾸지 않는다. 그러나 시스템 코드 보호·중복·참조·마감·revision 충돌은 실제 업무409로 독립 적용한다. 인증 유예는 도메인 검증/서버필드 주입 방지·SQL integrity 유예가 아니다.

임시 관리자로 누구나 변경 가능한 구조이므로 로컬 또는 접근 대상 제한 사설 개발·검증 환경만 허용한다. 공개 인터넷 배포는 실제 로그인/권한 적용과 검증 이후로 차단한다. DB/백엔드를 불필요하게 외부 공개하지 않고 환경 접속제한을 검증하되 새 제품 역할/토큰/인증 API를 만들지 않는다(REQ-143).

## 운영·observability·검증 인계

- 요청과 오류는 X-Request-Id/requestId로 화면·서버 로그를 연결한다. 한국어 사유/필드/참조유형·건수를 보존하되 SQL·스택·비밀번호·접속 비밀값을 응답/화면/로그에 노출하지 않는다. 별도 접근기록 테이블 쓰기나 외부 수집 서비스를 요구 충족용으로 추가하지 않는다(REQ-130).
- getMenus는 없는/중지 부모·자기참조·순환·MENU_LVL 깊이불일치·미등록 screenId의 노드/자손을 제외·진단한다. 중복 SCRN_ID는 정상계층→SORT_ORD→MENU_ID순 대표1개와 탈락 트리 제외다. DB DML로 자동복구하지 않고 정상부분만 표시하되 초기 오류가 남으면 불합격이다. 잘못된 주소는 대시보드→정상첫화면→메뉴오류 순으로 복구하고 loop0회다(REQ-035~REQ-038).
- health의 실제 DB200/단절503, 설치 SQL 전체 구조/초기값/재시작 중복, 잠금 후409/전체롤백, no-op 시각·이력0, 2초 갱신은 별도 관측값을 남긴다. 단순 HTTP200·토스트·스크린샷은 DB 저장/원자성/공통부품 실제 재사용의 증명이 아니다.

| 검증 책임 | 승인 tasks.md 연결 | 후속 관측 기준 |
| --- | --- | --- |
| 계약/원천 게이트 | T001~T003 | OQ-001/OQ-007 권한있는 승인·원본revision/해시/기존구조 확인 전 구현 금지 |
| 설치/SQL integrity | T006~T009, T068 | 실제 PostgreSQL 구조/seed/rollback/재시작·6쓰기표 밖 업무변경0건 |
| 입력/revision/이력 | T010~T016 | 경계값400·같은토큰 선행1성공/후행409·NULL시각값변경 감지·no-op·이력 동시 rollback |
| 메뉴·설정·기간 | T021~T032 | 실제 DB 메뉴 진단/경로·개별 설정복원/손상격리·controlled 이벤트0/1·마감조회만 |
| UI-27 기능 단위 | T033~T065 | 그룹/일반/값코드·논리삭제참조·기준경계/자금마감·대응물리삭제·무기한이력 각각 DB/API/UI 재조회 |
| 공통조회/캐시 | T066~T068 | 미존재기준404·DB정확값·응답후2초·타업무불변·commit후조회실패와 재저장 구분 |
| UI/범위·실행 | T069~T073 | 1280/1024/375/360px·참조비교·제외기능0·3서비스healthy·실제 빌드/시험 명령/종료코드·미검증 구분 |

추가 처리량/p95/동시 사용자·로그 보존기간·백업주기·외부 모니터링 제품은 승인값이 없으므로 수치/정책을 만들지 않는다. 변경이력 외 보존기간/암호화·마스킹 정책은 OQ-DATA-004, 제외된 파일실체/용량·보존정책은 OQ-DATA-003을 유지한다.

## 범위 경계·설계 결정과 미결

| 결정/미결 | 유지할 선택·차단 | 근거·금지되는 확대 |
| --- | --- | --- |
| 단일 동기 시스템 | SPA+단일 backend+DB·imperative/blocking | REQ-028/REQ-144; worker·202 비동기화로 원자성 우회 금지 |
| 정확한 스키마 보존 | Flyway만 생성·원본 SQL integrity·버전열 추가 없음 | REQ-003/REQ-030/REQ-135; ORM 자동 DDL로 구조 차이 은폐 금지 |
| OQ-001 | Java21/Boot3.5.x 대 고정 Java17/Boot3.3.x 충돌 | REQ-132/REQ-138/REQ-139; 승인 전 버전선택·빌드준비완료 금지 |
| OQ-007 | plan exact mybatis와 승인 spec/REQ-144 jpa·REQ-135 JPA validate 충돌 | plan 정본/원장의미 모두 보존·MyBatis/JPA 확정 또는 병렬도입 금지·T001 승인 전 매핑/의존성/검증 구현차단 |
| OQ-002 | DDL 읽기대조와 초기값/실DB 검증 구분 | data-model.md의 물리명 확보만 반영; data 미검증을 설치완료로 승격 금지 |
| OQ-003/OQ-004 | OpenAPI 봉투·일부 결과/전달 위치 미승인 | schema 복제/새 응답 발명 없이 contracts/openapi.yaml 제안 참조·직렬화 완료선언 금지 |
| OQ-005/OQ-006 | 공유명명 유지·실제 request_key 입력 대기 | 가짜 screen/table/API·BASIC-129로 request_key 대체 금지 |
| 코드/대응/설정 삭제 분리 | 코드 논리·대응 물리+이력·설정 해당행 물리/이력없음 | DELETE HTTP 동사로 저장정책 추정 금지 |
| REQ-147 후속 업무 | UI-27 외 제목·해당기간·“이 화면은 다음 단계에서 만듭니다.”만 | 가짜 업무수치·무동작 저장·업무계산/저장/업로드0 |
| REQ-148 마감 쓰기 | getMonthClose/getPeriodContext 조회만 | 마감/해제/스냅샷 API·생성동작0 |
| REQ-149 외부·출력 | AI/알림 준비 중 안내만 | 메일/AI/엑셀입출력/인쇄/다크모드·파서/파일API0 |
| REQ-150 지급·단가·근태 | UI-20 미등록 | 일/시간단가·근태업로드 생성0 |
| REQ-151 행정구역 | DDL/제약·0행만 | 주소입력·행정구역 파일 확보/복사/적재 작업 제외 |
| REQ-152 계산규칙 | 테이블/94행 설치 | 편집UI·계산엔진/실행API 없음 |
| REQ-153 그룹 제한 | 조회/일반그룹추가만 | 그룹명/사용변경·삭제UI/API 없음 |
| REQ-154 기준 제한 | 값/적용이력 변경만 | 키추가/삭제·이름/단위/설명수정 없음 |
| REQ-155 이력 제한 | getCommonCodeHistory 읽기·무기한보존 | 수정/삭제API·기간정리 없음 |

## 공통 계약 참조

업무 context/component/flow와 저장 경계를 먼저 정의하고 기술스택은 여기서 참조한다. 기능 의미는 승인 spec.md/기존 원장, 실행·영속성 exact block의 semantic SSOT는 plan.md, 실행 작업은 tasks.md, 물리/검증/seed는 data-model.md, HTTP wire 계약은 contracts/openapi.yaml이다. 이 문서의 JSON은 plan.md의 파생 복사본이며 독립 SSOT가 아니다.

React Router·Gradle·Spring MVC·Flyway·PostgreSQL·BigDecimal·Asia/Seoul 제약은 유지한다. Java/Boot 충돌 OQ-001과 영속성 OQ-007 승인 전 상세 의존성/매핑을 확정하지 않는다. 구체 patch·TypeScript·Router·Flyway·MyBatis 버전은 원문이 정하지 않았으므로 추정하지 않는다.

아래 technology_stack/version_bom/required_outputs는 plan.md JSON 그대로다.

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

아래 exact marker+JSON은 plan.md 정본의 문자 그대로인 투영이다. 블록 내부 resolved/mybatis/conflicts=[]를 임의 수정하지 않는다. 그러나 승인 spec.md·REQ-144의 resolved/imperative/jpa/blocking 및 REQ-135의 JPA ddl-auto=validate 의무와의 **문서 간 충돌 OQ-007**은 미해결이다. REQ-144는 결정 보존 요구이며 MyBatis 채택 요구로 재정의하지 않는다. resolved는 프로젝트 승인/build-ready를 의미하지 않는다. 권한 있는 상류 정합화 승인 전 MyBatis/JPA 어느 쪽도 최종 구현으로 확정하지 않고 영속성 구현·JPA validate 합격 선언을 차단한다.

<!-- aiops:execution-persistence-contract:start -->
```json
{
  "decision_status": "resolved",
  "execution_model": "imperative",
  "persistence_technology": "mybatis",
  "access_mode": "blocking",
  "selection_source": "explicit",
  "blocking_priority": [
    "mybatis",
    "jpa",
    "spring-jdbc"
  ],
  "conflicts": []
}
```
<!-- aiops:execution-persistence-contract:end -->

## 요구 회계

승인 원장 156개를 각각 한 줄로 연결한다. covered는 이 문서에 명시한 실제 operation/table/screen의 설계 책임 연결이며 제품 구현·시험 통과가 아니다. screen-only는 화면 소유 책임, data-constraint는 물리/seed/무결성, non-functional은 환경·계약·차단, out-of-scope는 요구별 제외를 뜻한다. 기존 원장은 이미 존재하므로 보존하며 생성 요구를 빌미로 재생성하지 않는다.

| canonical_id | status | evidence |
| --- | --- | --- |
| REQ-001 | data-constraint | 데이터 설치·쓰기·보존 경계 — 전체 SQL 원천 보존: HQ 예약 파트를 포함한 DDL 전체 및 data 전체가 실행본과 대조된다. |
| REQ-002 | data-constraint | 데이터 설치·쓰기·보존 경계 — 행정구역 빈 테이블: 설치 후 행정구역 테이블 데이터 건수 0행이다. |
| REQ-003 | data-constraint | 데이터 설치·쓰기·보존 경계 — 전체 DB 구조 보존: 68테이블/836컬럼/73 FK/78 명시 인덱스/13시퀀스/46 CHECK/5 UK 및 TB_SY_CALC_RULE 11컬럼이 일치한다. |
| REQ-004 | data-constraint | 데이터 설치·쓰기·보존 경계 — 기본 초기값 적재: 그룹64·시스템44·코드391·운영기준43·역할5·메뉴40·역할메뉴150·관리자/연결 각1·계산규칙94·HQ1행이며 세 빈 테이블은 0행이다. |
| REQ-005 | data-constraint | 데이터 설치·쓰기·보존 경계 — 초기 자금 기준과 계산규칙: CASH_BASE_YM=202601, CASH_BASE_AMT=1150000000이며 임의 월 마감 기록 0행이다. |
| REQ-006 | data-constraint | 데이터 설치·쓰기·보존 경계 — Flyway 실행본 래퍼 정리: 실행본 diff에는 최상위 래퍼 제거만 있으며 revision/해시가 기록된다. |
| REQ-007 | data-constraint | 데이터 설치·쓰기·보존 경계 — 마이그레이션 불변·멱등성: 재시작 후 초기값 중복 0건, 미적용 파일은 원본 대조 결과를 갖는다. |
| REQ-008 | data-constraint | 데이터 설치·쓰기·보존 경계 — 설치 실패와 구조 은폐 금지: 실패 fixture는 해당 트랜잭션을 롤백하며 사용자 보유 DB 초기화 0건이다. |
| REQ-009 | data-constraint | 데이터 설치·쓰기·보존 경계 — 구조 차이 보고: 이름/타입 불일치 fixture에서 임의 스키마 수정 없이 불일치 목록이 보고된다. |
| REQ-010 | data-constraint | 데이터 설치·쓰기·보존 경계 — 설치 후 쓰기 경계: 수당/기준 변경 후 프로젝트 팀비·경비·정산금액 변경 0건이다. |
| REQ-011 | data-constraint | 데이터 설치·쓰기·보존 경계 — 서버 감사 필드와 연월 저장: REG_USER_ID=admin, REG_DTTM 기록; 화면/API YYYY-MM이 DB YYYYMM으로 저장된다. |
| REQ-012 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 공통 컴포넌트 실제 재사용: 코드/대응/기준/이력이 DataTable<T>를 공유하고 화면이 columns/renderer/row action을 전달한다. |
| REQ-013 | covered | API boundaries 표의 putPreference, createCodeGroup, saveGroupCodes, createCodeMapping, saveOperatingStandards — 문자열 입력 검증: 필수 공백은 400; 최대 길이 초과는 원문 입력을 보존한 채 400이다. |
| REQ-014 | covered | API boundaries 표의 createCodeGroup — 그룹ID 정규화: 소문자 그룹ID는 대문자로 저장; 31자 또는 숫자 시작 ID는 400이다. |
| REQ-015 | covered | API boundaries 표의 saveGroupCodes — 코드 정규화: 20자 영숫자/_ 코드는 허용; 21자 또는 허용 외 문자는 400이다. |
| REQ-016 | covered | API boundaries 표의 createCodeGroup, saveGroupCodes — 명칭 경계와 식별자 불변: 그룹명50·코드명100자는 허용; 51·101자 및 등록 PK 변경은 거부된다. |
| REQ-017 | covered | API boundaries 표의 saveGroupCodes, saveOperatingStandards — 정밀 십진값 범위: 0과 99,999,999,999.9999 허용; 정수12자리 또는 소수5자리는 400이다. |
| REQ-018 | covered | API boundaries 표의 saveGroupCodes, saveOperatingStandards — 숫자 입력 정리와 금지값: 쉼표/원 기호를 제거한 값은 저장; 음수 또는 지수표기는 400이다. |
| REQ-019 | covered | API boundaries 표의 saveGroupCodes, saveOperatingStandards — 소수 원값 보존: 소수4자리 입력이 DB에서 정확히 재조회; 자동 반올림/버림 0회다. |
| REQ-020 | covered | API boundaries 표의 saveGroupCodes — JOB 월단가 정수: 0과 999,999,999,999,999 허용; 음수·소수·상한 초과는 400이다. |
| REQ-021 | covered | API boundaries 표의 getMonthClose, putPreference, saveOperatingStandards, getParameter — 실제 연월 검증: 0001-01·9999-12 허용; 월00/13·잘못된 형식은 400이다. |
| REQ-022 | covered | API boundaries 표의 putPreference, saveGroupCodes — Y/N·JSON 타입: true/false와 2000자 이내 JSON 허용; 문자열 boolean/2001자 JSON은 400이다. |
| REQ-023 | covered | API boundaries 표의 saveGroupCodes, saveOperatingStandards — 빈 수당·기준값 구분: 수당 빈값은 0으로 재조회; 기준값 빈칸은 400이며 별도 제한 없는 %에 임의 상한이 없다. |
| REQ-024 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 공통 표시 함수: 0은 ‘0’, NULL은 ‘–’, 음수 기호는 ‘−’; 표시 전후 저장값은 같다. |
| REQ-025 | covered | API boundaries 표의 getCurrentUser — 임시 사용자 컨텍스트: GET /api/current-user에 admin과 ADMIN·developmentMode=true가 있고 헤더에 개발용 표시가 보인다. |
| REQ-026 | covered | API boundaries 표의 putPreference, deletePreference, createCodeGroup, saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards — 처리자 주입 거부: 다른 USER_ID 주입은 저장되지 않고 실제 등록/수정 처리자는 admin이다. |
| REQ-027 | covered | API boundaries 표의 getCurrentUser, putPreference, deletePreference, createCodeGroup, saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards — 인증·권한 유예: /UI-27 직접 조회/저장 시 인증 리다이렉트·401·역할별403은 0건이다. |
| REQ-028 | covered | API boundaries 표의 createCodeGroup, saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards — 원자적 업무 변경: 하나 실패하면 데이터/이력 모두 0건 변경; 성공하면 대상과 이력이 함께 commit된다. |
| REQ-029 | covered | API boundaries 표의 putPreference, deletePreference, getGroupCodes, saveGroupCodes, deleteGroupCode, getCodeMappings, deleteCodeMappings, getOperatingStandards, saveOperatingStandards, getOperatingStandardHistory — 불투명 revision: 양쪽 시각 NULL이어도 값 변경 시 revision이 달라지고 한쪽 NULL은 동일 시각으로 판정하지 않는다. |
| REQ-030 | covered | API boundaries 표의 putPreference, deletePreference, saveGroupCodes, deleteGroupCode, deleteCodeMappings, saveOperatingStandards — 잠금 후 동시 수정 검출: 같은 revision의 두 저장 중 선행 1건 성공; 후행 1건409이며 후행 데이터/이력 변경0건이다. |
| REQ-031 | covered | API boundaries 표의 putPreference, saveGroupCodes, saveOperatingStandards — 실제 변경 없는 저장: 동일값 저장은 changed=false·이력0건; 실제 변경 commit 후 changed=true와 재조회 결과다. |
| REQ-032 | covered | API boundaries 표의 putPreference, deletePreference, createCodeGroup, saveGroupCodes, deleteGroupCode, getCodes, createCodeMapping, deleteCodeMappings, saveOperatingStandards — commit 후 캐시 최신화: 저장 브라우저 2초 내 최신값; 다른 브라우저 다음 조회는 최신 DB 값이며 통신 오류를 성공으로 표시하지 않는다. |
| REQ-033 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 한국어 오류와 재조회 실패 구분: 500/연결 실패는 오류 띠·재시도; commit 후 조회 실패는 ‘저장됐지만 다시 불러오지 못했습니다.’다. |
| REQ-034 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 중복 클릭·자동 재저장 금지: 처리 중 반복 클릭의 추가 요청0건; 실패 후 입력 유지·자동 재저장0회다. |
| REQ-035 | covered | API boundaries 표의 getMenus — DB 메뉴 구성·정렬: 초기 10묶음30화면이 DB와 일치하고 프론트 고정 배열이 메뉴 원천이 아니다. |
| REQ-036 | screen-only | component 경계・설정·기간·캐시의 일관성 — UI-16/Sidebar·공식 route의 메뉴 펼침·접힘 탐색: 부모 이름은 화면 이동, 펼침 아이콘은 토글; 접힌 메뉴 키보드 focus에서 하위 목록이 보인다. |
| REQ-037 | covered | API boundaries 표의 getMenus — 잘못된 메뉴 트리 진단: 자기참조/순환/깊이 오류 fixture는 하위 트리까지 제외; DB DML 자동 수정0건이며 초기 오류 잔존 시 불합격이다. |
| REQ-038 | screen-only | component 경계・설정·기간·캐시의 일관성 — UI-16/Sidebar·공식 route의 공식 라우트와 복구: /UI-27 새로고침 및 #UI-27 replace 성공; 정상 화면0개는 메뉴 오류이며 리다이렉트 반복0회다. |
| REQ-039 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 PC 전용 화면 보호: 767px UI-27 본문 대신 안내·이동 수단; 폭 확대 시 미저장 편집이 복원된다. |
| REQ-040 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 반응형 공통 레이아웃: 1280px 메뉴240/64px·여백24px; 1024px 기본접힘·16px, 360px 전체 가로넘침0이다. |
| REQ-041 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 헤더의 실제 제공 항목: AI/알림 클릭은 준비 중이고 가짜 배지/대화0개; 사용자 메뉴에 비밀번호/로그아웃/타이머가 없다. |
| REQ-042 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 화면 제목·기간 배치: UI-27에는 업무 기간 줄이 없고 자체 적용월 입력만 있으며 빈 업무 화면은 지정 기간 줄만 보인다. |
| REQ-043 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 화면 탭과 LRU: 대시보드 닫기 불가; 9번째 취소 시 새 탭0개·기존8개 유지다. |
| REQ-044 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 편집 보존과 이동 경고: 계속 작성 시 입력/필터/기간/스크롤 유지; 새로고침/닫기는 beforeunload·dirty 탭은 ●다. |
| REQ-045 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 접근 가능한 모달·입력: 모달 내 Tab 순환·닫기 후 원래 포커스; 잘못된 입력 제출 시 첫 오류 필드로 이동한다. |
| REQ-046 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 새로고침 탭 초기화: 여러 탭을 열고 새로고침하면 URL 화면+대시보드만 남고 탭 목록 복원 요청은 없다. |
| REQ-047 | covered | API boundaries 표의 getPreferences — 설정 조회와 기본값: 없는 설정은 stored=false; MENU_FOLD 미저장은 null로 조회되어 화면폭별 기본 접힘을 적용한다. |
| REQ-048 | covered | API boundaries 표의 putPreference — 설정 명시 저장: 기본값 최초 PUT 후 stored=true; 동시 최초 등록 후행은409 STALE_WRITE다. |
| REQ-049 | covered | API boundaries 표의 getPreferences, putPreference — 손상 설정 격리: 손상 항목 조회는 오류와 revision; 다른 설정값 유지·문제 행 자동 DELETE0건이다. |
| REQ-050 | covered | API boundaries 표의 deletePreference — 개별 설정 초기화: 초기화 후 해당 GET은 stored=false·기본값; 다른 설정 행 및 UI-27 이력은 그대로다. |
| REQ-051 | covered | API boundaries 표의 getPreferences, putPreference — 즐겨찾기 편집: 10개 저장 후 재조회 순서 일치; 11번째 추가는 거부되어 기존10개가 유지된다. |
| REQ-052 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 최근 화면 자동 갱신: 같은 화면 재방문 후 선두1개만 남고 6번째 화면 활성화는 마지막 항목을 제거한다. |
| REQ-053 | covered | API boundaries 표의 getPreferences, putPreference — 메뉴 접힘 기억: PC 접힘 저장 후 재조회 true; 모바일 열고 닫아도 MENU_FOLD 행이 변경되지 않는다. |
| REQ-054 | covered | API boundaries 표의 getPreferences, putPreference — 목록 크기 설정: 정수100 허용; 문자열 ‘50’은400이고 미저장 목록은50행 기준이다. |
| REQ-055 | covered | API boundaries 표의 getPreferences, putPreference — 밀도 설정 동기화: 좁게 저장 후 열린 목록 모두 좁게; 새로고침 재조회도 좁게다. |
| REQ-056 | covered | API boundaries 표의 getPreferences, putPreference — 표 열 설정 저장: 유효 order/hidden은 재조회 일치; 필수열 숨김 또는 중복 열은400·기존 설정 보존이다. |
| REQ-057 | covered | API boundaries 표의 getPreferences, putPreference — 금액 단위 선택 기억: 백만원 선택 후 재조회 백만원; 업무 테이블 숫자 변경0건이다. |
| REQ-058 | covered | API boundaries 표의 getPreferences, putPreference — 설정 화면ID·표시 보호: 미등록 screenId 저장 거부; 모바일 숨김 후 PC에서 기존 즐겨찾기 복원된다. |
| REQ-059 | covered | API boundaries 표의 getPreferences, putPreference — UI-27 열 정의 제한: codes의 code/name 숨김400; 미정의 viewId/화면 COLS400이며 작업버튼은 설정 목록에 없다. |
| REQ-060 | covered | API boundaries 표의 getPreferences, putPreference — 설정 적용과 실패 보존: 설정 실패 시 이전 밀도 유지·오류 안내; 성공한 페이지 크기 변경에도 미저장 코드 편집이 유지된다. |
| REQ-061 | covered | API boundaries 표의 getPreferences, putPreference — 기간 설정 타입·범위 검사: NONE PERIOD PUT은400; 저장기간이 범위 밖이면 안내/기본기간 표시·DB 수정0건이다. |
| REQ-062 | screen-only | component 경계・설정·기간·캐시의 일관성 — 공통 레이아웃/부모 hook의 controlled 기간 부품: YEAR/YEAR_MONTH 두 부모에서 유효선택 이벤트1회; 범위 밖/disabled 이벤트0회·내부 DB호출0회다. |
| REQ-063 | covered | API boundaries 표의 getPeriodContext, putPreference — 화면 기간 선택·기억: 누계 연도 변경 후 월이 새 범위 내로 제한되고 재조회 PERIOD의 year/month가 일치한다. |
| REQ-064 | covered | API boundaries 표의 getPeriodContext — 기간 범위와 운영 전 상태: YEAR는 시작연도~현재연도+1; 범위 역전이면 PERIOD 쓰기0회, UI-07/08/09는 시작연도-1 허용이다. |
| REQ-065 | covered | API boundaries 표의 getPeriodContext — 연도 배지: 지난 해 선택은 읽기 전용 배지, 내년 선택은 계획 배지이며 업무 데이터 쓰기0건이다. |
| REQ-066 | covered | API boundaries 표의 getMonthClose — 실제 월 마감 조회: CLOSE fixture는 마감; 행 없는 월은 작업중이며 가상 마감 기록/마감 쓰기 API0개다. |
| REQ-067 | screen-only | component 경계・설정·기간·캐시의 일관성 — UI-27의 UI-27 네 탭과 조회 상태: 4탭이 보이고 로딩·빈 결과·API 오류는 서로 다른 상태로 관찰된다. |
| REQ-068 | covered | API boundaries 표의 getGroupCodes, getCodeMappings, getOperatingStandards, getCommonCodeHistory — 표 페이지 계약: page0 또는 size30은400; 기준값43키는 페이지 분리 없이 모두 보인다. |
| REQ-069 | covered | API boundaries 표의 getCodeGroups — 그룹 목록과 선택: 초기64그룹/시스템44개 및 미삭제 코드 수가 보이고 선택 후 스크롤 위치를 유지한다. |
| REQ-070 | covered | API boundaries 표의 getGroupCodes — 일반 코드 조회: GET 그룹 코드에 DEL_YN=N인 중지 코드가 있고 UI에 흐리게 표시되며 순서는 읽기 전용이다. |
| REQ-071 | covered | API boundaries 표의 saveGroupCodes — 일반 코드 일괄 변경 저장: 여러 행 저장 후 재조회에 각 변경값이 있고 선택체크/일괄중지 버튼은 없다. |
| REQ-072 | screen-only | component 경계・설정·기간·캐시의 일관성 — UI-27의 편집 취소 복원: 신규·기존 편집 후 취소하면 신규 행 제거·기존값 복원·DB 변경0건이다. |
| REQ-073 | covered | API boundaries 표의 saveGroupCodes — 부분 변경·중복 코드 검사: 변경 한 행 저장 후 다른 페이지 보존; 동일 code 두 건 요청은400·전체변경0건이다. |
| REQ-074 | covered | API boundaries 표의 saveGroupCodes, deleteGroupCode — 시스템 코드 변경 제한: CTRT_TYPE TK 이름만 변경 성공; 이름+useYn 요청은409·이름과 이력도 변경0건이다. |
| REQ-075 | covered | API boundaries 표의 createCodeGroup — 그룹 중복과 채번 경합: 동일ID는409 DUPLICATE_GROUP; 동명이면409 DUPLICATE_GROUP_NAME이며 동시 등록도 중복0건이다. |
| REQ-076 | covered | API boundaries 표의 createCodeGroup — 업무 그룹 생성: POST 후201·DB에 일반 업무 그룹·NEW 이력1개; UI에서 새 그룹이 선택된다. |
| REQ-077 | screen-only | component 경계・설정·기간·캐시의 일관성 — UI-27의 그룹 추가 전 편집 처리: 계속 작성이면 그룹 추가0건·편집 유지; 저장/취소 처리 후에만 그룹 추가가 진행된다. |
| REQ-078 | covered | API boundaries 표의 saveGroupCodes — 일반 신규 코드 편집 행: 창 추가 뒤 DB0건; 하단 저장 후 신규 코드가 재조회되며 신규 취소는 DB 변경0건이다. |
| REQ-079 | covered | API boundaries 표의 saveGroupCodes, deleteGroupCode — 그룹 내 코드 PK 재사용 금지: DEL_YN=Y 기존 PK 재등록은409; 다른 신규 code는 그룹 저장 시 생성된다. |
| REQ-080 | covered | API boundaries 표의 saveGroupCodes — 신규 코드 기본값·정렬 채번: 복수 신규 저장 후 SORT_ORD가 전달 순서로 증가하고 PK는 불변이다. |
| REQ-081 | covered | API boundaries 표의 saveGroupCodes — 정렬 순번 한계: max+1이 범위를 넘는 fixture는409·신규/기존 변경 및 이력0건이다. |
| REQ-082 | covered | API boundaries 표의 saveGroupCodes — 코드명 중복 허용: 다른 code에 같은 name 저장 성공; 조회에서 각각의 code로 구별된다. |
| REQ-083 | covered | API boundaries 표의 saveGroupCodes — 그룹별 추가값 필수·길이: 필수 추가값 공백/101자는400; 다른 그룹 필드 주입은400이다. |
| REQ-084 | covered | API boundaries 표의 saveGroupCodes — PROJ_STEP 진척 정수: 0과100은 허용; 소수 또는101은400이며 전체 요청이 롤백된다. |
| REQ-085 | covered | API boundaries 표의 saveGroupCodes — BANK 코드와 RSM 라벨: 3자리 앞0 값은 그대로 재조회; 2자리 또는 비숫자는400이다. |
| REQ-086 | covered | API boundaries 표의 saveGroupCodes — RSM_LABEL 허용값: name 입력은 NAME으로 저장; 목록 외 문자열은400이다. |
| REQ-087 | covered | API boundaries 표의 saveGroupCodes — CSV 정규화·참조: 중복 CSV는 첫 등장 순서만 남음; 미존재/삭제/신규 중지참조는400, 기존 중지참조 유지와 JOB 자기참조는 허용이다. |
| REQ-088 | covered | API boundaries 표의 getGroupCodes — 수당 코드 조회·편집 형태: 일반 값 그룹에서 code/name/unitNm은 읽기 전용이고 값만 입력 가능하다. |
| REQ-089 | covered | API boundaries 표의 saveGroupCodes, deleteGroupCode — 값 코드 잠금: 일반 값 코드 단위 변경은409; 시스템+값 그룹 이름은 허용·codeVal 변경은409 SYSTEM_CODE_LOCKED다. |
| REQ-090 | covered | API boundaries 표의 saveGroupCodes — 수당 신규 코드 저장: 추가창 완료만으로 DB0건; 하단 저장 후 기본 codeVal=0·unitNm=원 재조회된다. |
| REQ-091 | covered | API boundaries 표의 saveGroupCodes, getCodes — 수당 값 변경과 업무 불변: ALLOW_TEAM=200000·INS_RATE=9.4·SEV_RATE=8.33 등 SQL값 일치; 값 변경 후 프로젝트 팀비/경비 변경0건이다. |
| REQ-092 | covered | API boundaries 표의 saveGroupCodes, getCodes — 일반 코드 중지·재개: 중지 후 신규 옵션에서 제외·includeInactive label 조회 가능; 재개 후 active 반환·다른 업무 저장코드 변경0건이다. |
| REQ-093 | covered | API boundaries 표의 saveGroupCodes — 사용 상태 변경 이력: USE_YN N 저장 후 STOP·Y 복원 후 RESUME가 이력 GET에서 조회된다. |
| REQ-094 | covered | API boundaries 표의 deleteGroupCode — 삭제 참조 차단: 참조1건 fixture 삭제는409·물리행/DEL_YN/이력 유지; 화면은 사용을 끄세요 안내를 보여준다. |
| REQ-095 | covered | API boundaries 표의 deleteGroupCode — 일반 코드 논리 삭제: 미참조 삭제 후 DEL_YN=Y·물리행 존속; 같은 PK 추가는409 DUPLICATE_CODE다. |
| REQ-096 | covered | API boundaries 표의 deleteGroupCode — 코드 삭제 이력: 미참조 삭제 후 이력에 DEL_YN의 N→Y가 조회되고 실제 코드 행은 보존된다. |
| REQ-097 | covered | API boundaries 표의 deleteGroupCode — 고정 참조 가드: 각 매핑 fixture의 참조1건은 삭제 차단; 코드 자체 NEW 이력만 있으면 삭제 허용이다. |
| REQ-098 | covered | API boundaries 표의 deleteGroupCode — 삭제 직전 재검사: 잠금 후 생긴 참조 fixture는409로 보호; ALWNC 삭제 요청은 값 코드 잠금 규칙으로 거부된다. |
| REQ-099 | covered | API boundaries 표의 getOperatingStandards — 운영 기준 전체 조회: 43키가 페이지 분리 없이 나오고 키/이름/단위/설명은 읽기 전용이다. |
| REQ-100 | covered | API boundaries 표의 getOperatingStandards, getOperatingStandardHistory — 기준 현재·과거·미래 이력: 미래행이 있어도 현재값으로 선택되지 않고 해당월 이전 행이 없으면 기준값 없음이다. |
| REQ-101 | covered | API boundaries 표의 getOperatingStandards, saveOperatingStandards — 일반 기준 적용 최소월: 최소월 저장은 허용; 최소월 이전 저장은409이며 기본월=max(현재월,최소월)이다. |
| REQ-102 | covered | API boundaries 표의 saveOperatingStandards — 기준 월별 추가·덮어쓰기: 덮어쓰기 후 해당월 값만 변경; 신규월 저장은 새행이며 다른 적용월 행은 동일하다. |
| REQ-103 | covered | API boundaries 표의 saveOperatingStandards — 기준 변경 키 중복 거부: 같은 key 중복 요청은400·전체 변경0건; 미변경 키의 시각/이력도 변하지 않는다. |
| REQ-104 | covered | API boundaries 표의 getOperatingStandards, saveOperatingStandards — 묶음 기준 표시: AI8개는2줄이며 변경 칸만 같은 적용월 요청에 들어가고 DB는 각 STD_CD행이다. |
| REQ-105 | covered | API boundaries 표의 saveOperatingStandards — DUN 순서 검증: D1이1 미만이면400; D1≥D2 또는 D2≥D3이면400·전체 롤백이다. |
| REQ-106 | covered | API boundaries 표의 saveOperatingStandards — 확률 범위 검증: 0/100 허용; 100 초과는400이며 키 묶음 전체가 변경되지 않는다. |
| REQ-107 | covered | API boundaries 표의 saveOperatingStandards — AI 점수 합계 검증: 합99/101은400·전체롤백; 합100은 저장되고 재조회 합100이다. |
| REQ-108 | covered | API boundaries 표의 saveOperatingStandards — 철수 통보일 정수: 1과99,999,999,999 허용; 0 또는 소수는400이다. |
| REQ-109 | covered | API boundaries 표의 getOperatingStandards, saveOperatingStandards — 자금 시작 연월 표현: 2026-01 화면은 API 202601·DB 정수로 저장; 월00/13 또는 소수는400이다. |
| REQ-110 | covered | API boundaries 표의 saveOperatingStandards — 기준 전체 영향구간 검사: 현재월 합100이어도 미래 경계 합99/101이면400·전체롤백; 전 경계 유효이면 commit된다. |
| REQ-111 | covered | API boundaries 표의 getOperatingStandards, saveOperatingStandards — 자금 두 키 즉시 갱신: 미마감 저장 후 두 키는 각1행·APLY_BGN_YM 동일; 일반 이력 요청과 혼합은400이다. |
| REQ-112 | covered | API boundaries 표의 saveOperatingStandards — 자금 기준 변경 이력: 성공한 실제 값 변경은 UPD 전후값으로 조회; 미변경값은 이력0건이다. |
| REQ-113 | covered | API boundaries 표의 getOperatingStandards, saveOperatingStandards — 기존 시작월 마감 잠금: 기존 시작월 CLOSE 시 새 시작월을 보내도409·두 키 불변; 기존 미마감이면 저장 허용이다. |
| REQ-114 | covered | API boundaries 표의 saveOperatingStandards — 자금 동시 잠금·후속 갱신: 정상 저장 후 기간 범위와 기준 재조회가 최신; 자금수지 계산·마감 자료 변경0건이다. |
| REQ-115 | covered | API boundaries 표의 getCodeMappings — 표기 대응 목록·검색: SRC_TEXT/CODE_GRP_ID/MAP_ID순 결과에 코드/이름이 있고 빈결과는 등록된 표기가 없습니다 안내다. |
| REQ-116 | covered | API boundaries 표의 createCodeMapping — 대응 입력·중복 검사: 동일표기+동일그룹은409 DUPLICATE_MAPPING; 같은표기+다른그룹은 허용된다. |
| REQ-117 | covered | API boundaries 표의 createCodeMapping — 대응 즉시 등록: POST201 후 대응 목록에서 입력 sourceText/groupId/code가 재조회된다. |
| REQ-118 | covered | API boundaries 표의 createCodeMapping, deleteCodeMappings, getCommonCodeHistory — 대응 등록·삭제 이력: NEW의 이후·DEL의 이전에 sourceText/groupId/code/codeName JSON이 조회된다. |
| REQ-119 | covered | API boundaries 표의 deleteCodeMappings — 대응 다중 물리 삭제: 유효 revision들 삭제 후 모든 선택 행이 목록에 없고 각 DEL 이력이 남는다. |
| REQ-120 | covered | API boundaries 표의 deleteCodeMappings — 대응 삭제 충돌: 두 대상 중 하나 revision 충돌 시409·두행 모두 유지·DEL 이력0건이다. |
| REQ-121 | covered | API boundaries 표의 createCodeMapping, deleteCodeMappings — 대응 변경의 업무 불변: 대응 추가/삭제 전후 기존 업무 테이블 값과 행수가 같다. |
| REQ-122 | covered | API boundaries 표의 getCommonCodeHistory — 변경 이력 조회: REG_DTTM DESC/HIST_ID DESC로 일시/대상/키/유형/항목/이전/이후/처리자가 보이고 admin은 임시 관리자다. |
| REQ-123 | covered | API boundaries 표의 saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards, getCommonCodeHistory — 변경 유형과 과거 이름 추적: 코드명 변경 후 CODE_NM 전후 이력이 조회되어 SKILL 과거 이름 참조검사에 사용된다. |
| REQ-124 | covered | API boundaries 표의 getCommonCodeHistory — 이력 필터 도메인: 잘못된 targetType은400; groupId 지정 결과에 운영기준 이력이 없다. |
| REQ-125 | covered | API boundaries 표의 saveGroupCodes, deleteGroupCode, createCodeMapping, deleteCodeMappings, saveOperatingStandards, getCommonCodeHistory — 이력 키·전후값 무손실: 대응 물리삭제 후에도 groupId 접두 구분자 필터로 조회; 500자 초과는 조용한 절단 없이 거부된다. |
| REQ-126 | covered | API boundaries 표의 getCodes — 공통 active 코드 조회: GET /api/codes/{group}은 code/name/addVal1~3/codeVal/unitNm; 중지코드는 기본 제외·includeInactive에서 조회된다. |
| REQ-127 | covered | API boundaries 표의 getParameter — 월별 단일 기준 조회: GET /api/params/{key}?month=의 유효값은 정확하며 계산엔진 실행0회다. |
| REQ-128 | covered | API boundaries 표의 getParameter — 기준 미존재 거부: 미등록 key 및 유효행 없는 month 둘 다404 STANDARD_NOT_FOUND다. |
| REQ-129 | covered | API boundaries 표의 getHealth, getCurrentUser, getMenus, getPeriodContext, getMonthClose, getPreferences, putPreference, deletePreference, getCodeGroups, createCodeGroup, getGroupCodes, saveGroupCodes, deleteGroupCode, getCodes, getCodeMappings, createCodeMapping, deleteCodeMappings, getOperatingStandards, saveOperatingStandards, getOperatingStandardHistory, getParameter, getCommonCodeHistory — API 필드와 서버 필드 보호: SYS/VAL/DEL/처리자/등록시각 주입400; 시스템 잠금 필드409이며 DB 변경0건이다. |
| REQ-130 | covered | API boundaries 표의 getHealth, getCurrentUser, getMenus, getPeriodContext, getMonthClose, getPreferences, putPreference, deletePreference, getCodeGroups, createCodeGroup, getGroupCodes, saveGroupCodes, deleteGroupCode, getCodes, getCodeMappings, createCodeMapping, deleteCodeMappings, getOperatingStandards, saveOperatingStandards, getOperatingStandardHistory, getParameter, getCommonCodeHistory — 공통 오류 반환과 비밀 보호: 참조삭제409는 CODE_IN_USE·참조건수; DB단절 health503은 DB_UNAVAILABLE이며 비밀정보가 없다. |
| REQ-131 | data-constraint | 데이터 설치·쓰기·보존 경계 — 변경 이력 무기한 보존: 과거 이력도 계속 조회되며 기간 기준 삭제 작업/이력 삭제 API가 없다. |
| REQ-132 | non-functional | 공통 계약 참조 — 원문 구현 기술 요구: 고정 계약 Java17/3.3.x와 충돌한 두 버전은 OQ-001로 남고 임의 확정하지 않는다. |
| REQ-133 | non-functional | 배포·실행 환경 — 3서비스 실행과 SPA: /UI-27 직접 새로고침 성공; compose3서비스 healthy·.env.example은 자리표시자/실행 안내만 포함한다. |
| REQ-134 | non-functional | 배포·실행 환경 — 서울 시간대 일관성: 현재일 API와 DB 세션 및 화면 일시가 Asia/Seoul 기준으로 일치한다. |
| REQ-135 | non-functional | blocking imperative persistence·공통 계약 참조 — Flyway만 생성/따옴표 없는 SQL/BigDecimal 유지; JPA ddl-auto=validate는 OQ-007 승인 정합화 전 구현·합격 미확정. |
| REQ-136 | non-functional | 배포·실행 환경 — 마이그레이션 파일 경로: backend/src/main/resources/db/migration/에서 V1 전체DDL 다음 V2 전체data가 적용된다. |
| REQ-137 | non-functional | component 경계와 소유 책임 — 프론트 구조와 계층 책임: UI-27 실제 공통부품 사용·hook/client 통신·서버 검증이 있으며 HTML 전체 iframe0개다. |
| REQ-138 | non-functional | 공통 계약 참조 — 고정 technology_stack: React+TypeScript+Vite, Java17+Spring Boot, PostgreSQL, docker·healthcheck_required=true가 유지된다. |
| REQ-139 | non-functional | 공통 계약 참조 — 고정 Version BOM: BOM 표가 원문5개 값과 동일하며 충돌하는 Spring Boot3.5.x를 몰래 대입하지 않는다. |
| REQ-140 | non-functional | 배포·실행 환경 — 고정 required_outputs: backend·frontend·infra/docker-compose.yml·/api/health 이름이 바뀌지 않는다. |
| REQ-141 | non-functional | 운영·observability·검증 인계 — 레퍼런스 시각 계약: sidebar/header/table/form 밀도·spacing/color/responsive 비교 증거가 있고 기능은 본문을 따른다. |
| REQ-142 | non-functional | 배포·실행 환경 — Docker 빌드 컨텍스트 제외: 각 context의 .dockerignore가 node_modules/.git/dist/build/.next/coverage를 제외한다. |
| REQ-143 | non-functional | 보안·신뢰 경계 — 격리 환경만 배포: 인터넷 공개 배포 경로가 없고 실제 인증/권한 검증 전 공개 배포는 차단된다. |
| REQ-144 | non-functional | 공통 계약 참조 — plan.md exact MyBatis 블록을 보존하되 승인 spec/REQ-144 JPA 결정 보존과의 OQ-007을 유지한다. REQ-144를 MyBatis 요구로 재정의하지 않고 T001 승인 전 구현차단. |
| REQ-145 | non-functional | 운영·observability·검증 인계 — 실제 백엔드 산출물: backend/ 변경과 실제 migration 증거 없이 UI만 있으면 완료 불가다. |
| REQ-146 | non-functional | 운영·observability·검증 인계 — 프론트·API 함께 검증: 실제 API 저장 후 브라우저 재조회 결과와 DB 값이 일치하며 미실행은 미검증이다. |
| REQ-147 | out-of-scope | 범위 경계·설계 결정과 미결 — 후속 업무 화면 경계: UI-27 외 업무 화면은 제목·해당 기간 줄·다음 단계 안내만 제공한다. 다른 업무 화면의 가짜 수치·무동작 저장버튼0개, 계산/저장/업로드0개다. |
| REQ-148 | out-of-scope | 범위 경계·설계 결정과 미결 — 월 마감 쓰기 제외: 월 마감/해제/스냅샷 생성은 구현하지 않는다. 마감 상태 읽기는 존재하되 마감 변경 API/생성 동작0개다. |
| REQ-149 | out-of-scope | 범위 경계·설계 결정과 미결 — 외부·출력·외형 확장 제외: 알림/메일·AI·엑셀 입출력·인쇄·다크 모드는 생성하지 않는다. 헤더 준비 중 안내 외 AI/알림 업무0개·엑셀 파서/업로드/다운로드0개다. |
| REQ-150 | out-of-scope | 범위 경계·설계 결정과 미결 — UI-20·단가·근태 제외: UI-20 지급관리·일/시간 단가·근태 업로드는 생성하지 않는다. 메뉴/UI등록표/API에 UI-20이 없고 일/시간 단가 및 근태 업로드 동작0개다. |
| REQ-151 | out-of-scope | 범위 경계·설계 결정과 미결 — 행정구역 파일 작업 제외: 주소 입력·행정구역 파일 확보/복사/적재는 이번 완료 조건에서 제외한다. 주소입력 기능/행정구역 적재작업 없이 DDL 제약과0행 상태로 검증한다. |
| REQ-152 | out-of-scope | 범위 경계·설계 결정과 미결 — 계산 엔진과 규칙 편집 제외: 계산규칙 테이블/데이터는 적재하되 편집 화면과 계산 실행 엔진은 생성하지 않는다. 94행 규칙 데이터가 있어도 규칙 편집 UI/계산 실행 API0개다. |
| REQ-153 | out-of-scope | 범위 경계·설계 결정과 미결 — 그룹 변경·삭제 제외: 그룹명/사용여부 변경과 그룹 삭제 기능은 제공하지 않는다. 그룹 추가/조회만 있고 그룹 수정/삭제 UI·API0개다. |
| REQ-154 | out-of-scope | 범위 경계·설계 결정과 미결 — 기준 키 CRUD 확장 제외: 운영 기준의 키·이름·단위·설명은 읽기 전용이며 신규 키/삭제는 제공하지 않는다. 값 변경 외 메타 수정/키 추가/키 삭제 API0개다. |
| REQ-155 | out-of-scope | 범위 경계·설계 결정과 미결 — 이력 변경·삭제 제외: 변경 이력은 조회만 제공하고 수정·삭제 API는 생성하지 않는다. 이력 GET만 존재하며 기존/오래된 이력 삭제·수정 경로0개다. |
| REQ-156 | covered | API boundaries 표의 getHealth — 실제 DB health 조회: 정상 DB에서200; DB 중단 시503 DB_UNAVAILABLE이며 단순 프로세스 생존만으로200을 반환하지 않는다. |

## 미해결 질문 (CHK)

요청서가 답을 주지 않아 사람에게 올라간 질문입니다. **답이 오기 전에는 구현하지
않습니다** — 지금 만들면 지어낸 값이 그대로 코드가 됩니다.

- `REQ-005, REQ-020, REQ-024, REQ-029, REQ-057, REQ-065, REQ-150, REQ-152` — 이 요구의 합격을 판정할 기준 값이 요청서에 없습니다. 숫자로 알려 주세요. (예: 보관 30일, 재시도 3회, 한도 1,000,000원)
- `REQ-149` — 중간에 실패하면 어디까지 되돌리나요? (예: 전체 롤백 / 성공한 건은 두고 나머지만 반려)

<!-- /chk-questions -->
