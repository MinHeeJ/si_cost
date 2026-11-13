# 공통 급여·월 노동비 배분 구현 인계

## 이번 실행 경계

그룹 2(월 노동비·경비 배분 1/2)와 이월 T007/T022의 구현 및 시험 코드를 추가했다. 실행은 runner 소유이며 아래 시험은 실행하지 않았다. awaiting runner verification.

보존: 기존 B1 파일 파싱·직원구분별 무시열 경고·명시 회사보험 0 우선순위·수당/보험/퇴직 산식, 업로드 월급여 snapshot, 실제 DEV_ID의 저장 월 MM/매출, 원천/사본/감사 자료, 기존 메뉴/라우터/작업탭 및 다른 화면 설정.
변경: B1 순수 파생함수 추출, 읽기 예정급여 어댑터, 순수 미마감 노동비/경비 배분과 완전성, 임시관리자 UI-03의 선택 가능한 참고열 설정.
DDL/seed/원천 동기화/계산 캐시/인증 신설/마감 쓰기는 없다.

## 확인한 경로 원장

- 계약: getMonthlyCosts GET /api/monthly-costs의 원가 계산 책임. 해당 HTTP controller/최종 service 및 TK·보호사본 조합은 지정된 후속 그룹의 책임이다. 이번 그룹은 대체 controller나 별도 URL을 만들지 않는다.
- 인증·인가: 기존 SharedQueriesController/CurrentUserProvider의 DB 임시 관리자, security=[] 유지. PreferenceController의 기존 공개 GET/PUT 경로 사용. 열을 권한으로 가리지 않는다.
- 애플리케이션: MonthlyCostCalculation(EstimatedPayrollAdapter).calculate(Sources, Person, String)은 한 사람×미마감월의 전체 배분을 반환한다. UI 필터/역할/파트/턴키 표시옵션을 입력받지 않는다. 회사 전체는 같은 Sources에서 각 사람×월 호출을 합성한다.
- 읽기: 기존 MonthlyCostRepository.loadYear(Integer)/loadMonth(int,int)의 REPEATABLE_READ/readOnly 집합 원천만 사용. DeveloperQueryService.get/list·AssignmentMonthlyService 동기화·B1 업로드 서비스는 호출하지 않는다. 순수 계산에는 저장소 의존성이 없다.
- 저장 구조: V1__full_schema.sql의 TB_HR_MON_PAY, TB_PJ_ASGN/ASGN_MON, TB_PJ_PROJ, TB_PJ_PROJ_PART, TB_BS_CLNT/PART, TB_CL_CLOSE, TB_SY_OPER_STD/CODE의 기존 열/PK를 사용한다. 원천 매퍼/DTO는 선행 그룹의 단일 정의를 그대로 사용한다. 추정 열/새 SQL/새 migration은 없다.
- materialization: MonthlyCostDtos의 Payroll/Person/Assignment/Project/Sources 및 MonthlyPersonCost/MonthlyCostRow/MonthlyLaborSource/MonthlyCostCompleteness를 그대로 재사용한다. 금액은 BigDecimal, 응답은 십진 문자열이고 nullable 참고값을 유지한다.
- 부수효과: 계산은 없다. 명시적인 COLS/UI-03 설정 저장만 기존 PreferenceService의 revision/lock/commit 무효화를 따른다. 기존 다른 설정 행을 바꾸지 않는다.

## 후속 조합 인터페이스

- common/payroll/PayrollCalculation.calculate(Classification, Amounts, Map<String,BigDecimal>): B1와 예정급여가 공유한다. Amounts.companyInsurance의 null만 자동 계산이며 0은 입력 우선이다. 업로드의 무시열 파싱/경고는 B1ImportDefinition에 남아 있다.
- EstimatedPayrollAdapter.resolve(Sources, Person, month): 업로드 우선, 미업로드는 해당 월 유효 가격+현재 분류/직위+공통 ALWNC 코드로 읽기 계산한다. Resolution은 source/labor/issues다. uploaded(Payroll)는 snapshot 참고값을 현재 기본값으로 대체하지 않는다. OS pay/netPay 표시는 공급가이며 VAT를 가산하지 않는다.
- MonthlyCostCalculation.calculate(Sources, Person, month): ACT·MM>0·실제 DEV_ID만 정렬해서 배분한다. 보호월은 IllegalArgumentException으로 재계산을 거부한다. 후속 MonthlyCostService는 Sources.protectedPersonMonth를 먼저 판정해 기존 StoredCost.toRow를 읽어야 한다. 보호된 PRE 사본의 분류·금액을 현재 프로젝트로 재판정하지 않는다.
- LOSE→IN→PRE→ALLOC, 실제 실주행의 ID/경비 보존, IN 파트 null, 최신 월 프로젝트 파트, FREE_MM 및 고정행 플래그가 제공된다. 마지막 실제행 보정과 IDLE 잔액은 노동비에만 적용한다.
- allocate는 multiply-then-divide/DOWN, average/rate는 HALF_UP 1자리·분모0 null이다. 원천/행 범위 초과는 해당 금액 null+문제월이며 합계 reduce에는 NUMERIC(15) 상한을 두지 않는다.
- TK 유상행의 revenue는 아직 null이다. 후속 TurnkeyRevenueCalculation/MonthlyCostService가 노동비를 다시 계산하지 않고 이를 조합해야 한다. 이번에는 임의 TK 참고액/완전성 합격을 만들지 않았다.
- FT/CT 전체휴직은 인접 휴직기간 합집합까지 판정한다. 부분휴직 MM은 재일할하지 않는다. 퇴사 다음달의 급여는 source 참고/진단만 남기며 배분행과 원가 합에서는 제외한다.
- FL/OS 무투입 비영 지급(음수 포함)은 행을 만들지 않고 allocationComplete=false와 signed sourceAmount를 남긴다. 실제 0 지급은 행 없이 완료다.

## UI-03 설정의 국소 계약

- /UI-03의 기존 SharedScreenFrame·YEAR·메뉴·즐겨찾기를 유지하고 열 설정 CTA만 추가했다. 개인 집계 본문은 지정된 후속 그룹에서 기존 안내 영역을 교체한다.
- ProfitabilityColumnSettings는 기존 ColumnPreferencesDialog를 사용한다. profitabilityColumns는 22열 메타데이터이며 업무 금액/사람 데이터가 아니다.
- 기존 COLS/UI-03 individualProfitability의 order/hidden 구조로 저장한다. 기본 숨김 참고열은 selfInsurance/withholdingTax/incomeTax/netPay이고 전부 다시 표시할 수 있다. 필수 name/revenue/totalCost/profit 숨김은 금지한다.
- 후속 본문은 useTablePreferences("UI-03", "individualProfitability", profitabilityColumns)를 그대로 소비해야 한다. 다른 YEAR 화면의 동작은 이번에 바꾸지 않았다. UI-03 YEAR 상한과 전체 화면/테이블 확장은 지정 후속 그룹이 마무리한다.
- 기존 UI-27의 모달 식별자는 보존하며 다른 화면은 자신의 screenId에서 식별자를 만든다. API 경로·세션·라우터를 추가하지 않았다.

## 작성한 시험과 runner 실행 대상

- PayrollCalculationTest: 명시0 보험, FT 수당 포함 퇴직, CT 공제, FL/OS, 필수율/수당 정밀도/범위 오류.
- 기존 B1ImportTest/B1ContractTest/B1DatabaseTest는 수정/약화하지 않았다. 추출 전후 회귀를 runner가 실행해야 한다.
- EstimatedPayrollAdapterTest: FT/CT/FL/OS 공통함수 일치, 예정 무쓰기, 가격NULL/명시0, 업로드 당시 분류/nullable 참고열, 공제/VAT 경계.
- MonthlyCostCalculationTest: 원문 4,970,000원 1.00/0.50 및 0.60, FL 5,000,000원 0.50, 음수 DOWN, T=0/1, 취소·대체인력, 휴직·퇴사·간접, 복수행 경비/범위초과, 분류 우선순위, MM/파트/고정행, 결정성/불변성, 보호월 호출 거부.
- MonthlyCostAllocationDatabaseTest: CostDatabaseSupport로 기존 독립 자료 DB에 읽기 전용 연결. 실제 PK/타입/원천 DTO 대조, 월 전체 원천의 반복 배분·합 보존·경비, 세 원문 숫자 사례와 모든 업무/감사/제외 재무 테이블 전후 값 비교. DB 없으면 기존 패턴의 환경 조건으로 건너뛴다. DB가 제공됐는데 사례가 없으면 진단과 함께 실패하며 예제를 삽입하지 않는다. OQ-CALC-001/002의 확정 숫자 fixture는 runner 자료가 필요하며 기본값/seed로 만들지 않는다.
- PreferencesContractTest: 실제 PreferenceController→PreferenceService의 MockMvc GET/PUT, 공개 설정/참고열 전체표시, 필수열 숨김 거부, stale revision, 다른 설정 보존.
- ProfitabilityColumnSettings.test.tsx: Vitest/jsdom 실제 설정 컴포넌트+기존 provider/dialog/table preference hook 렌더. 참고열 토글/저장/소비자 갱신/재열기, 필수열 disabled, 경로 유지, 실패 시 이전 적용값과 draft 보존. 소스 문자열 비교 시험은 없다.

확인된 실행 결과는 git diff --check의 성공과 편집 도구의 YAML 문법 확인뿐이다. Maven/npm/DB/브라우저 시험·빌드·설치·서버 시작은 실행하지 않았다.

## 그룹 3: T077–T079 추가 인계

보존: 기존 미마감 배분 산식, ACT 월원천 MM/기간, PRE 사본의 네 칸 복합키·원금액·분류·파트·FIX/감사값, 기존 JSON 구조를 유지한다. 새 DDL/매퍼/HTTP 경로/동기화/저장 부수효과는 없다.

- T077: Sources.protectedRows(month, personId)는 보호 판정 후 해당 사람·월의 저장 사본만 정렬·투영한다. 현재 LOSE 상태로 PRE를 다시 분류하지 않으며 마감 사본이 없으면 빈 목록을 반환한다. 0원 사본을 생성하거나 현재 급여로 채우지 않는다. 미보호월에는 호출을 거부한다. 기존 calculate의 보호월 거부는 유지한다. 후속 MonthlyCostService는 보호월에서 이 투영을 재사용하고 빈 목록의 완전성/자료 없음 진단을 조립한다. 월 API 구현은 여전히 다음 그룹 범위다.
- T078: 기존 projectPart는 계산월 이하 최신 이력을 선택하고 없음/최신 명시 NULL/IN은 NULL이다. 합성 IDLE/INDRCT의 파트도 NULL이며 저장 사본의 원파트는 변경하지 않는다. Person.matchesPart(partId)는 DEV.PART_ID만 사용한다(null=필터 없음, UNASSIGNED=사람파트 NULL). 후속 개인 집계의 사람 파트 필터는 이 술어를 재사용하며 원가 행의 projectSalesPartId로 사람을 제외하지 않는다. 공통 계산에는 필터를 추가하지 않았다.
- T079: 기존 FREE_MM/IDLE 잔여 MM/간접 MM0 및 rowGenerated, idleRowGenerated, indirectRowGenerated를 회귀로 고정했다. rowGenerated는 이번 읽기에서 계산 생성된 행 여부이며 저장 사본은 false다. 따라서 저장된 유효 0원 행의 존재는 rows 항목 자체로 판별하고 false를 사본 누락으로 해석하면 안 된다.

추가 시험:

- MonthlyCostAllocationRegressionTest: 마감 PRE 반복 조회와 원감사 보존, FIX Y/누락 사본/미마감 오용, 실주 후 월원천 없는 배정 재생성 금지, 기존 월원천·기간 불변, 월파트 최신/미래/없음/명시 NULL/IN 및 사람파트 필터 분리, FREE/MM·0원 IDLE의 JSON null/생성 플래그, 간접 0원 MM0, 행 없음과 유효 0원 구별.
- MonthlyCostAllocationDatabaseTest에 추가한 세 시험: 독립 기존 DB의 실주 전 CLOSE/PRE 사본 반복 조회, SQL 월파트와 DEV.PART_ID 분리, FREE/간접/유효 0원 고정행 원천 대조. 기존 CostDatabaseSupport와 전후 전체 테이블/감사 비교를 재사용한다. 이 시험은 DB 환경 슬롯이 없으면 건너뛰며, 지정 DB에 필요한 원문 조건이 없으면 실패하고 예제나 사본을 삽입하지 않는다.

경로 원장: getMonthlyCosts GET /api/monthly-costs → 후속 MonthlyCostService(이번에는 미작성) → 기존 MonthlyCostRepository.loadMonth/loadYear(readOnly REPEATABLE_READ) → MonthlyCostMapper.storedCosts/projectParts/assignments → V1의 TB_CO_MON_COST/TB_PJ_PROJ_PART/TB_PJ_ASGN_MON → 기존 StoredCost.toRow 및 새 Sources.protectedRows. 공개 security=[]/임시 DB actor와 원천 무쓰기 경계는 불변이다. 프런트/API 최종 필터 연결과 HTTP 수락은 후속 작업이며 이번 순수 계산 회귀를 API 실행 증거로 간주하지 않는다.

그룹 3 시험·컴파일·DB 실행은 수행하지 않았다. runner 대상: MonthlyCostAllocationRegressionTest, MonthlyCostAllocationDatabaseTest 및 기존 MonthlyCostCalculationTest/MonthlyCostSourceDatabaseTest. awaiting runner verification.
