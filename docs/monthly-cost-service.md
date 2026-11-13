# 공통 월별 원가 서비스 사용 계약

상태: 구현·회귀 코드 인계, awaiting runner verification. 새 DDL, migration, seed, 원가 사본 저장 기능은 없다. 설치용 schema를 다시 실행하지 않는다.

## 호출 경계와 트랜잭션

패키지: `backend/src/main/java/com/sicost/profitability`.

- HTTP `getMonthlyCosts`: GET `/api/monthly-costs` → `MonthlyCostController.getMonthlyCosts` → `MonthlyCostService.getMonthlyCosts(MonthlyCostInput.Query)`.
- 월 요청: `Query(int year, int month, String personId)`. 사람 ID는 선택이며 회사 전체 계산을 마친 뒤 반환 범위에만 적용한다. UI의 이름/LOSS/휴직 숨김/TK 보기 조건을 받지 않는다.
- 연간 소비자: Spring 주입된 `MonthlyCostService.loadYear(Integer)`로 Sources를 한 번 읽고 `calculate(Sources)`로 회사 전체 `MonthlyCostsData`를 만든다. 여러 월·두 보기·출력을 조합하는 호출자는 자신의 public 애플리케이션 메서드를 `readOnly=true, isolation=REPEATABLE_READ` 트랜잭션으로 감싸야 한다. 단독으로 두 메서드를 따로 호출하면 서로 다른 DB 스냅샷이 될 수 있다.
- 기존 연간 소비자는 `IndividualProfitabilityService.snapshot(Query)`이다. `Snapshot(sources, common, jobs, grades)`에 대해 `project(Snapshot, Query, boolean allRows)`와 `detailMonths(Snapshot, String personId, boolean includeTurnkey)`를 사용하면 추가 원천 조회 없이 목록/합계/두 TK 보기를 얻는다. 후속 회사원가/마감 소비자는 UI 필터가 적용된 project 결과 대신 common을 사용한다.
- 모든 JSON은 기존 `ApiResponse.ok` envelope, 오류는 `ApiExceptionHandler`를 사용한다. 미확인 원가는 정상 200 응답의 null+diagnostic이며 HTTP 오류/0건과 다르다.
- 현재 계약은 공개 읽기/security=[]이다. CurrentUserProvider의 DB 임시 ADMIN은 actor이며 로그인/역할 검사 신설 근거가 아니다. 임의 R09 로그인 경로를 추가하지 않는다.

## 집합 원천과 실체화

`MonthlyCostRepository.loadYear(Integer)` / `loadMonth(int,int)` → `MonthlyCostMapper` 및 `DeveloperRepository.historicalPeople/historicalPrices/historicalStates`. 사람마다 DB를 반복 호출하지 않는다. Mapper XML은 `backend/src/main/resources/mappers/profitability/MonthlyCostMapper.xml`에 있다. SQL alias는 quoted camelCase이며 Java record와 일치한다.

| DTO 원천 | 기존 표/열과 의미 |
| --- | --- |
| Person/Price/WorkState | TB_HR_DEV, TB_HR_DEV_PRC, TB_HR_DEV_STAT. 현재 이름/직무/등급은 표시·필터, 월급여 분류를 대체하지 않는다. 중지·퇴사 인력의 과거 자료도 읽는다. |
| Payroll | TB_HR_MON_PAY의 PAY_AMT/ALWNC_AMT/INSR_CMPNY_AMT/RETIRE_RSV_AMT 및 공제·공급가·당시 EMP_TYPE_CD/DRCT_CLS_CD. 월 업로드 snapshot 우선. |
| Assignment | TB_PJ_ASGN_MON의 실제 DEV_ID, MON_MM, APLY_UPRC, SALES_AMT와 TB_PJ_ASGN의 ASGN_ID/JOB_CD/INPUT_TYPE_CD/ASGN_STAT_CD. CTRT_DEV_ID로 이중 배분하지 않는다. |
| Project/ProjectPart | TB_PJ_PROJ 저장 계약/예산/팀비/프로젝트경비, TB_PJ_PROJ_PART의 계산월 이하 최신 APLY_BGN_YM. 사람 소속파트와 프로젝트 영업파트는 별도다. |
| TurnkeyCell | TB_PJ_TK_DSTRB의 프로젝트·단계·ROW_SN·DSTRB_YM·DEV_ID·금액. B6Comparison.selected의 PLAN→CTRT→EST 전체표 선택을 재사용한다. |
| StoredCost | TB_CO_MON_COST. 네 칸 PK=(COST_YM,DEV_ID,PROJ_ID,ASGN_ID). LEFT JOIN 표시 마스터 누락 때문에 원가행을 버리지 않는다. |
| 기간/기준 | SharedQueriesService의 CASH_BASE_YM, 서버 Asia/Seoul Clock, TB_CL_CLOSE, TB_SY_CODE ALWNC. 상수 보험율·시안 월계수는 없다. |

DeveloperQueryService.get/list의 status refresh와 AssignmentMonthlyService의 동기화 경로는 계산에서 호출하지 않는다. 청구·입금·카드·이체·법인세·전월조정·추가 직접경비를 개인에게 나누지 않는다.

## 사본 선택 → 배분 → 분류 → 보정

1. 서버 올해 현재월/과거 12월과 CASH_BASE_YM을 교차한다. 입사월~퇴사월을 포함하고 입사일 NULL은 조회기간 시작부터다. 개인자료가 없으면 회사 이관손익에서 역산하지 않는다.
2. `Sources.protectedMonth`는 CLOSE 또는 기준월 이전, `protectedPersonMonth`는 여기에 같은 사람월 FIX_YN=Y 한 행이라도 있음을 더한다. 보호 사람월은 저장된 모든 사본을 유지하는 보수적 선택이다. 부분 고정 잔여분을 재배분하지 않는다.
3. 보호 사본은 `protectedRows`/`StoredCost.toRow`로 읽는다. 사본 0행은 누락이고 실제 금액 0의 존재행은 유효하다. 현재 급여/프로젝트 상태로 복원·재분류하지 않는다. 원 FIX_YN/파트/감사값을 유지한다. 혼합 고정월에 실제 투입 사본이 빠지면 coverage 진단을 낸다.
4. 미마감은 `EstimatedPayrollAdapter.resolve`가 업로드 급여를 먼저 선택한다. 없을 때만 유효 가격과 DB ALWNC를 `common/payroll/PayrollCalculation`에 전달한다. B1과 같은 순수 함수이며 저장하지 않는다. FT=급여+수당+회사보험+퇴직, CT=급여+수당+회사보험, FL=공제전 지급, OS=부가세전 공급가다.
5. 간접은 INDRCT 고정행, FT/CT 전체휴직은 IDLE 고정행에 노동비를 배분한다. 일반 유효투입은 ACT/MM>0/실제 DEV_ID이며 프로젝트ID→투입ID순으로 정렬한다. FT/CT 분모=max(MM합,1.00), FL/OS 분모=MM합. 분수 중간 절사 없이 원금×MM÷분모의 최종 원을 DOWN(음수도 0쪽)한다. T<1의 FT/CT는 IDLE에 잔액, 그 외 마지막 실제행에 잔액을 준다. 무투입 FL/OS 비영 지급은 허위 대기행 없이 배분 미완료 진단이다.
6. 프로젝트 분류 우선순위는 LOSE→IDLE, IN→RND, PRE→PRE, 나머지 ALLOC이다. 무상지원은 FREE_MM과 단가0으로 표시하되 노동비/경비는 유지한다. 실주 실제투입과 합성 IDLE은 ID로 구별한다.
7. 팀비/프로젝트경비는 각각 저장 프로젝트 금액×원본 월MM을 DOWN한다. OS와 합성 IDLE/INDRCT의 두 경비는0. 노동비 정규화 분모로 경비를 나누지 않는다.
8. `TurnkeyRevenueCalculation.calculate`가 노동비를 다시 계산하지 않고 개인 단가만 합성한다. 선택표에 그 사람이 전체기간에 있으면 해당 월의 사람 금액을 유상 MM 비례로 배분·마지막행 보정한다. 표에 사람이 없을 때만 노동비×저장 계약금액÷저장 예산을 사용한다. 없는 예산을 임의 80%로 만들지 않는다. 고정 사본은 이 경로를 통과하지 않는다.
9. `TurnkeyRevenueCalculation.project(..., false)`는 표시용 TK 단가만 제외한다. MM·노동비·경비·분류·원사본은 보존한다. 회사 전체 common을 이 표시결과로 대체하지 않는다.

## 반환 DTO와 금액 구별

정의: `MonthlyCostDtos.java`. 금액/율/MM은 JSON 십진 문자열(금액 원정수, MM2자리, 율1자리)이며 null은 명시 직렬화한다. Java 계산은 BigDecimal이다.

- `MonthlyCostsData(period, items)`의 items는 사람×월 `MonthlyPersonCost`이며 rows는 실제 네 칸 키의 `MonthlyCostRow`다.
- `MonthlyLaborSource`: 사람월 1회의 급여/수당/보험/퇴직/공제/netPay, laborCost, employeeType/directClass, source/estimated. 투입행마다 급여를 복제해 합하지 않는다.
- `MonthlyCostRow.personalRevenue`: 개인 배분 참고액. MO/DP는 월 원천 SALES_AMT, TK는 배분표/대체식 참고액이다. 청구일 기준 실제 회사 매출/입금이 아니다.
- `Project.contractAmount/budget`: 프로젝트 전체 참고 금액/저장 예산. 개인 월매출이나 급여가 아니다.
- `laborAllocated`: 월 노동비 중 그 투입 몫. `teamExpense/projectExpense`: 저장 프로젝트 경비에 MM을 곱한 각각의 금액. `directExpense`는 두 경비 합이며 추가직접경비 배분을 의미하지 않는다.
- `totalCost`: 노동비+팀비+프로젝트경비. `profit`: 개인단가−원가. 본인공제와 재무 지출을 다시 가감하지 않는다.
- `knownLaborAllocated/knownPersonalRevenue`: 알려진 부분합. 완전한 원가/단가로 바꿔 이름 붙이지 않는다.
- `rowGenerated`: 이번 순수 계산행 여부이며 DB 저장 여부를 변경하는 명령이 아니다. 미마감 fixed=N, 사본은 원값이다.
- `summarize(List<MonthlyPersonCost>, boolean includeTurnkey)`의 `PeriodAmounts`는 완전성 인식 합계/알려진 부분/문제월을 제공한다. `averageCost(int months)`의 분모는 소비자가 확정한다. 이 메서드가 UI의 근무월 정책을 결정하지 않는다.

## 완전성·진단·집계 정책

`MonthlyCostCompleteness(costComplete,revenueComplete,estimated,allocationComplete,issues)`와 `MonthlyCostIssue(personId,month,message,source,relatedScreen,sourceAmount)`를 함께 소비한다. null을0/적자/흑자로 단정하지 않는다. 원가 없음/마감 사본 없음/급여 미확인/표-투입 불일치/오버플로를 문제월·원천·관련화면으로 표시한다. 알려진 행을 삭제하거나 예시 급여로 채우지 않는다.

개인표는 공통 결과 뒤에 직접/휴직/검색/LOSS를 투영한다. 누계와 월평균은 다른 보기이며 workedMonths=N, employmentMonths/ utilizedMonths는 별도다. monthBasis의 포함 여부를 UI가 재계산하지 않는다. 평균은 기초금액별 DOWN 후 원가·이익 재조합, N0은 null이다. 전체합계는 전체 부모 집합 기준이며 페이지/펼친행과 무관하다. 회사 H/U/F와 율은 UI 필터와 독립이다.

## 무효화·쓰기 경계

계산 서버 캐시는 없으며 매 요청 새 committed 원천을 한 REPEATABLE_READ에서 읽는다. 인력/가격/휴직/급여/투입 월MM·매출/프로젝트·예산·경비·TK표·파트/마감/기준·코드 commit 이후 다음 조회는 미마감 최신값과 보호 사본을 함께 읽는다. 프론트는 기존 projects/clients/sales-parts/operating-standards/codegroups 및 assignments commit 이벤트를 구독한다. 별도 이벤트가 없는 원천 저장 뒤에는 조회 CTA로 다시 읽는다.

원가 조회/보기/새로고침/계산 안내에는 업무 DB 쓰기0. 사용자가 명시 저장한 설정은 기존 PreferenceService revision 경계만 쓴다. 다운로드는 ExcelExportService에서 한 snapshot으로 전 시트를 생성한 뒤 별도 필수 DNLD commit 완료 후 전송한다. 접근기록 실패 시 파일을 전송하지 않는다. 개발자 이름 클릭은 기존 Drawer의 별도 레거시 상세 GET이며 그 refresh 부수효과를 계산 무쓰기 증거에 섞지 않는다.

관련 검증과 미실행 경계: `docs/profitability-verification.md`.
