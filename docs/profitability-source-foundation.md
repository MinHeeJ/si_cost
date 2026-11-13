# 월 원가 원천 기반 인계

## 확인한 기준과 보존 경계

구현 시작 revision: `668f7677923f3c00d95fdc39165da221aa18a2c5`, branch `main`.
원격 fetch/병합은 하지 않았다. 이것은 작업트리의 실제 HEAD 확인이며 원격 최신성의 증명이 아니다.
시작 시 `backend/src/test/resources/contracts/openapi.yaml`에는 runner 소유 변경이 있었다. 이 파일은 덮어쓰지 않았다.

기존 `backend/pom.xml`은 Java 17 / Spring Boot 3.3.13 / MyBatis 3.0.4 / Flyway PostgreSQL / executable boot jar다.
프런트는 React 18.3.1 / TypeScript 5.6.3 / Vite 5.4.21 / npm이며 `frontend/package-lock.json` lockfileVersion=3이 있다.
의존성·manifest·lockfile·Docker·기존 화면·라우트·메뉴·업로드·원천 쓰기는 변경하지 않았다.
기존 `.gitignore`, `backend/.dockerignore`, `frontend/.dockerignore`는 요구하는 제외 항목을 이미 포함한다.

DB 설계를 먼저 대조했다. `V1__full_schema.sql`의 기존 테이블과 컬럼만 사용한다.
이 변경에는 신규 DDL, migration, seed, 설치 작업, 예제 업무행, source synchronization이 없다.
사본 PK는 `(COST_YM, DEV_ID, PROJ_ID, ASGN_ID)`다. `TOTAL_COST`, `PROFIT`, `INPUT_TYPE`, `JOB` 같은 사본 컬럼은 만들지 않았다.
사본 원가합계와 이익만 DTO 변환에서 `LABOR_ALLOC_AMT + DRCT_EXPS_AMT`, `SALES_AMT - 원가합계`로 파생한다.

## 실제 03~07 연결 목록

아래 경로는 `backend/src/main/java/com/sicost/` 기준이다.

| 선행 | 확인한 심볼/계약 | 이 그룹의 연결/보존 |
| --- | --- | --- |
| 03 | `developers/DeveloperRepository`: `read(Supplier<T>)`, `snapshot(String,boolean)`, mapper 읽기. `DeveloperQueryService.list/get`은 먼저 `refresh(false)` 호출 | 기존 Repository/Mapper에 `historicalPeople/historicalPrices/historicalStates` 집합 조회 추가. query service의 refresh/status/history 쓰기는 호출하지 않음 |
| 03 | `DeveloperQueryService.calculate(...)`는 현재 날짜 상태를 계산; `TimeConfiguration.SEOUL`과 `Clock`, `CurrentUserProvider` 존재 | 과거 재직/휴직은 별도 기간 값에서 판단. 현재 `USE_YN`/`WORK_STAT_CD`로 과거 사람을 제외하지 않음 |
| 04 | `projects/ProjectQueryService.getProjects/getProject`, `ProjectDtos.Project`와 `ProjectRepository`; `distribution/ProjectDistributionService.get/comparison` | 저장 프로젝트 금액/예산/타입/상태/실주일 및 단계표를 집합 읽기. 프로젝트 기본값 재생성 서비스는 호출하지 않음 |
| 04 | `parts/ProjectPartService.getProjectParts/saveProjectPart`, `ProjectPartDtos`; 명시 쓰기는 `WriteCoordinator`, 마감 잠금, revision | 시작월 이력을 전부 읽고 월별 파트 선택은 후속 계산에서 수행. 쓰기·잠금은 원가 조회에 사용하지 않음 |
| 05 | `assignments/AssignmentService.list/get/create`, `common/assignments/AssignmentMonthlyService.Source/StoredMonth/Month` | 저장 `TB_PJ_ASGN_MON.MON_MM/APLY_UPRC/SALES_AMT` 그대로 읽음. `m.DEV_ID`가 실제 인력이며 `CTRT_DEV_ID`는 참고 식별자 |
| 05 | `AssignmentMonthlyService.synchronize(...)`는 `MANDATORY` 트랜잭션에서 월자료와 표시값을 변경 | 원가 조회에서 절대로 호출하지 않음. 누락 월행을 만들지 않음 |
| 05 | `apiops/PayrollController.getPayroll`, `PayrollService.getPayroll(Query)`, `PayrollRepository`, `PayrollDtos.Row/Page` | 기존 API의 COALESCE/검색/페이지 규칙은 유지. 새로운 공통 원천에는 nullable 급여 참조값과 업로드 분류를 그대로 보존 |
| 05 | `b1/B1ImportDefinition.calculate`는 private이며 파싱/직원별 우선순위와 결합; `B1Repository.apply`가 실제 급여 쓰기 | public 공통 급여 계산기가 이미 있다고 가정하지 않음. 공통 함수 추출과 예정급여 계산은 다음 배분 그룹이 담당. 이 그룹은 원본 Payroll/Price/ALWNC 어댑터만 제공 |
| 04/05 | `b6/B6Comparison.selected(List<Cell>)`: PLAN→CTRT→EST를 표 전체에서 선택 | `turnkey()`는 월 필터 없이 모든 단계/월을 읽음. `projectMonths(range)`는 요청월에 관계된 프로젝트의 기간 전체 확정 월행을 제공 |
| 06 | `bills/BillService.getBills/getBill`, `BillDtos`; `receipts/ReceiptService.getBillReceipts/createBillReceipt`, `ReceiptDtos`, WriteCoordinator/revision/history | 선행 기능이 실제로 존재함을 확인. 원가 원천에 청구/입금 의존성·합산·쓰기 경로를 추가하지 않음 |
| 07 | `financialitems/FinancialService.getFinancialItems/editor`, `FinancialRepository.Snapshot`, 회사세액 파생 `common/financial/FinancialCalculation` | 재무 9종·법인세·조정·추가 직접경비는 개인 배분 입력에서 제외. 시험의 전후 불변 대조 대상에는 포함 |
| 공통 | `shared/SharedQueriesService.getPeriodContext/getCodes/getMonthClose`, `SharedQueriesMapper.cashBaseMonth/monthCloses` | 동일 서비스에서 CASH_BASE_YM/활성 ALWNC 코드 읽기. 동일 mapper에서 raw CLOSE 읽기. 별도 상수/가짜 기준값 없음 |
| 공통 | `common/concurrent/WriteCoordinator`, `RevisionService`, `CommitInvalidation.afterCommit` | 쓰기 서비스의 기존 잠금/revision/commit 이벤트 보존. 새 원천은 캐시가 없고 재호출마다 DB를 읽음. 계산용 쓰기 잠금·이벤트·캐시 테이블 없음 |

## 변경 경로 원장

| 경계 | 증거/결정 |
| --- | --- |
| 계약 | 후속 `getMonthlyCosts`: GET `/api/monthly-costs`, `security=[]`, 임시 관리자 개발 문맥. 신규 HTTP handler 등록은 이 그룹이 아니라 월 API 그룹 책임 |
| 진입/애플리케이션 | 이번 실제 진입점은 `MonthlyCostRepository.loadYear(Integer)` / `loadMonth(int,int)`. 두 메서드 모두 클래스 수준 `@Transactional(readOnly=true, isolation=REPEATABLE_READ)` 적용 |
| 인증/권한 | 기존 `CurrentUserProvider.userId()`가 DB 임시 관리자 actor를 구함. 로그인·caller role guard·ownership/민감열 마스킹·리다이렉트 추가 없음 |
| 공유 문맥 | 주입받은 Clock의 instant 한 개에서 서울 날짜/계산시점 결정. 기준월은 SharedQueriesService, 마감 원문은 SharedQueriesMapper. 다음 HTTP service/export의 외부 transaction도 REPEATABLE_READ를 유지해야 함 |
| 데이터 접근 | 기존 DeveloperRepository의 새 역사 조회 3개 + `MonthlyCostMapper`의 select 9개. SQL XML namespace와 인터페이스의 statement 이름 일치 |
| 스키마 | 기존 V1: 사람 417~442, 가격 458~468, 상태 472~485, 급여 489~508, 프로젝트 568~594, 파트 598~607, TK 688~702, 투입 721~740, 월투입 769~783, 원가 952~973 |
| 구체화 | `MonthlyCostDtos` record 구성요소와 quoted camelCase SQL alias 대응. 내부 계산 입력은 BigDecimal/LocalDate/LocalDateTime. 공개 DTO 금액/MM/큰 식별자는 문자열, 건수/페이지는 정수. nullable 공개 필드는 Include.ALWAYS |
| 사본 | snapshot부터 LEFT JOIN하여 표시 마스터 누락/합성 IDLE/INDRCT도 보존. 집계/group by로 PK의 ASGN_ID를 접지 않음. FIX와 기존 감사값은 그대로 변환 |
| 부수효과 | 새 mapper는 SELECT만 제공. 기존 재직 상태 refresh, assignment synchronize, B1 apply, 마감/손익 쓰기, DNLD/PREF 쓰기 호출 없음 |
| 시험 | `MonthlyCostSourceTest`, `MonthlyCostSourceDatabaseTest`, `CostDatabaseSupport`. 실제 shared HTTP 확인은 SharedQueriesController를 대상으로 함. 존재하지 않는 월 API를 HealthController 등으로 대신 시험하지 않음 |

## 다음 그룹이 재사용할 인터페이스

- `MonthlyCostDtos.Sources`: UI 필터 없는 회사 전체 immutable 목록. payroll는 퇴사 다음달 자료도 진단을 위해 원문 보존한다. 계산자는 `Person.employed(month)`로 비용/분모 포함을 결정하고 부적격 급여 진단을 남겨야 한다.
- `StoredCost`는 물리 사본 + 표시 LEFT JOIN 값이며 `toRow()`로 공개 snapshot DTO에 변환한다. 실제 LOSE 행의 IDLE 분류와 합성 IDLE 키는 서로 다르다.
- `Sources.protectedMonth/protectedPersonMonth`는 CASH_BASE_YM/CLOSE/동일 사람월 FIX Y 판정이다. 혼합 고정행은 사람월 전체를 보호하는 보수적 계약이다. 재계산/누락 사본 진단 자체는 다음 월 서비스 그룹 책임이다.
- `loadYear`의 계산 기간은 CASH_BASE_YM와 교차한다. 원문 읽기 범위는 연도 1월부터 올해 현재월/지난해 12월까지여서 시작전 보호 사본을 버리지 않는다. `loadMonth`는 첫 운영연도의 시작전 월에도 기존 사본을 읽을 수 있으나 새 계산은 금지한다.
- `period(...)`, `employmentIncludes(...)`, `WorkState.fullMonthLeave(...)`, `Price.effective(...)`는 순수 경계 연산이다. 업로드 금액의 일할 재계산을 하지 않는다.
- `MonthlyCostRow/MonthlyLaborSource/MonthlyPersonCost/MonthlyCostsData`, period/issue/completeness, 기간 metrics/person/list/detail/summary/contribution DTO를 한 파일에 정의했다. 후속에서 새 package/중복 DTO를 만들지 말고 확장한다.
- `projectMonths`는 기간 전체 확정 MM 확인용이다. TK 금액은 과거 단가 또는 청구/입금을 사용해 복원하지 않는다.

## 독립 기존자료 시험 DB 계약

`CostDatabaseSupport`는 기존 재설치형 `PostgreSqlDatabase.install()`을 사용하지 않는다.
Flyway/CREATE SCHEMA/INSERT/DELETE/cleanup을 하지 않고 runner가 제공한 별도 기존자료 복사본만 읽는다.
운영 계정/비밀번호/접속값은 이 문서나 fixture에 넣지 않는다.

runner가 시험 환경에만 제공할 값:

- `SICOST_COST_TEST_DB_URL`: 독립 PostgreSQL 기존자료 복사본의 JDBC 연결값
- `SICOST_COST_TEST_DB_SCHEMA`: 이미 존재하는 복사본 schema
- `SICOST_COST_TEST_DB_USER`, `SICOST_COST_TEST_DB_PASSWORD`: 복사본 조회 전용 연결
- `SICOST_COST_TEST_BASELINE_CONFIRMED`: 조회 전용 시험에서는 선택 사항. 전용 연결값으로 실행을 선택하며, 이 값도 제공하면 `true`여야 한다. 명시적으로 다른 값을 제공하면 연결 전에 거부한다. 설정 쓰기 시험의 별도 확인 gate는 그대로 유지한다.

앱 `DB_URL` 및 기존 설치형 `SICOST_TEST_DB_URL`과 동일한 URL을 거부한다.
연결은 default_transaction_read_only=on, TimeZone=Asia/Seoul이며 지원 객체의 read 콜백은 REPEATABLE_READ다.
시험끼리 원천행을 만들거나 고치지 않으므로 공유 seed 수정/ID 충돌/순서 의존/삭제 대상 공유가 없다.
비교는 금액 합계만이 아니라 모든 행 JSON의 정렬된 목록을 대상으로 하여 PK/nullable/audit microsecond 변경도 포함한다.
`CostDatabaseSupport.snapshot()`은 원가/급여/배정/손익·03/04 원천·기준값·청구/입금·07 재무·감사/설정의 전체 상태를 보관한다. 이를 후속 T137의 행위 전후 대조에도 재사용한다.

필수 기존자료 사례는 별도 예제 삽입 대신 runner의 독립 사본에 있어야 한다:

- 같은 사람/월/프로젝트의 다른 ASGN_ID 두 행
- 합성 IDLE/INDRCT 사본, 실제 식별자의 IDLE 분류(실주), 가능하면 표시 마스터 누락 사본
- 중지 인력의 유효 과거 원가, 퇴사월 업로드 급여
- 현재 사람 분류와 다른 월급여 snapshot, nullable 공제/공급가
- 유효 가격 이력과 과거 LEAVE 기간
- 실제 DEV_ID와 CTRT_DEV_ID가 다른 대체 투입
- 저장 프로젝트 경비, TK 단계/월표, 마감/고정 사본

DB URL이 없으면 DB 시험은 JUnit 조건으로 skip된다. URL을 제공했는데 필수 사례가 없으면 해당 시험은 fixture 부족으로 실패한다. 이를 업무 계산 실패나 통과로 바꾸지 않는다.
일반 무DB 시험은 순수 기간·재직·휴직·정밀 문자열 경계만 실행한다.

runner 집중 대상: `MonthlyCostSourceTest`, `MonthlyCostSourceDatabaseTest`.
기존 제품 test/package 명령은 runner만 실행한다. 이 구현 단계에서 Maven/npm/컨테이너/네트워크/브라우저를 실행하지 않았다.
정적 `git diff --check`는 수행했으며, 이는 컴파일·JUnit·PostgreSQL·HTTP 실행 통과 증거가 아니다.

## 남은 검증/후속 책임

상태: awaiting runner verification.

실제 독립 DB 자료와 실행 receipt는 이 작업에 제공되지 않았다. 연결/비교 fixture 코드는 제공했으나 자료의 존재나 시험 통과를 주장하지 않는다.
현재 표시명/기준값/재무 변경 전후의 최종 계산 결과, 보험/수당 중복 없음, 퇴사 다음달 비용 제외, 필터 불변 전체 월결과는 다음 배분/월서비스/기간집계 구현 후 T137에서 이 기반으로 검증해야 한다.
T007의 UI 열 설정으로 숨김 참고열을 다시 표시하는 컴포넌트 시험은 UI-03 설정 그룹의 구현이 필요하다. 이 그룹에서는 무인증 기존 shared HTTP 및 원천 참조열의 무마스킹 보존만 시험 코드로 작성했다.
이 그룹은 새 화면/API 쓰기·로그인·source editor·마감 실행·개인 급여명세·회사 손익·프로젝트 원가·파트 정산·AI를 추가하지 않는다. 최종 화면 조립 이후의 메뉴/버튼 회귀는 최종 그룹 책임이다.
