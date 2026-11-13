# 공통 월 원가: TK 참고액·보호 사본 인계

## 변경 경계와 실행 상태

이 변경은 월별 계산 조합과 `GET /api/monthly-costs`만 담당한다. 기존 노동비/경비 계산, 급여 공통함수, 조회 전용 repository/mapper와 공개 개발 컨텍스트를 그대로 사용한다. UI, 인증, 원천 편집, 마감 실행, 테이블/컬럼/마이그레이션, 데이터 재적재는 추가하지 않았다.

시험 코드는 작성했으나 빌드·시험·DB·컨테이너·서버 실행은 하지 않았다. 상태는 awaiting runner verification이다. 아래 시험 대상은 실행 결과가 아니다.

## 확인한 변경 경로

| 경계 | 실제 연결 |
| --- | --- |
| HTTP | getMonthlyCosts: GET /api/monthly-costs, year/month 필수, personId 선택, 본문 없음 |
| 입력/응답 | MonthlyCostInput.query(MultiValueMap), Query(int,int,String); ApiResponse<MonthlyCostsData>; 200/400/500 |
| 인증/인가 | 승인된 공개 연산 security=[] 유지. RequestIdFilter는 요청 ID만 설정한다. 새로운 역할 guard 없음 |
| 개발 컨텍스트 | MonthlyCostRepository.loadMonth/loadYear → CurrentUserProvider.userId → 기존 DB 임시 관리자 확인. 사용자 입력의 역할로 대체하지 않음 |
| 트랜잭션 | MonthlyCostService의 readOnly REPEATABLE_READ → MonthlyCostRepository의 같은 격리수준. 사람 선택은 전체 계산 후 응답에서만 적용 |
| 원천 | 기존 MonthlyCostMapper의 storedCosts/payroll/assignments/projectMonths/projects/projectParts/turnkey/standards/parts, DeveloperRepository의 historicalPeople/Prices/States, SharedQueriesService/Mapper의 기간/코드/마감 |
| 순수 계산 | MonthlyCostCalculation.calculate(Sources,Person,String) → TurnkeyRevenueCalculation.calculate(Sources,MonthlyPersonCost) |
| 보호 조회 | Sources.protectedPersonMonth/protectedRows → StoredCost.toRow; 서비스는 모든 사본 키를 유지하고 LABOR_ALLOC_AMT를 한 번 합산 |
| 물리 구조 | 기존 V1: TB_CO_MON_COST 네 키(COST_YM,DEV_ID,PROJ_ID,ASGN_ID); TB_PJ_TK_DSTRB 네 키(PROJ_ID,DSTRB_STEP_CD,ROW_SN,DSTRB_YM); TB_PJ_PROJ_PART 두 키(PROJ_ID,APLY_BGN_YM). 새 DDL 없음 |
| materialization | 기존 quoted camelCase aliases/record 매핑 유지. TurnkeyCell에 UPLD_ID::text uploadId 추가; canonical constructor에 AutomapConstructor 지정; 기존 계산용 생성자 호환 |
| 부수효과 | select/순수 계산만. DeveloperQueryService refresh, 월투입 동기화, 급여 파생값 저장, 접근기록 쓰기, 잠금/캐시 없음 |
| 오류 | ApiExceptionHandler 유지. 미확인/누락/배분불일치는 200 내 진단; 일반 저장소 실패는 기존 비노출 500 |

## 후속 소비자가 사용할 인터페이스

- `getMonthlyCosts(Query)`는 한 달 전체를 계산하고, personId가 있으면 마지막에 해당 사람만 선택한다. 사람별 DB 조회나 필터별 원가 재배분은 하지 않는다.
- `loadYear(Integer)`로 원천을 한 번 읽고 `calculate(Sources)`를 호출하면 같은 원천으로 월 전체 결과를 얻는다. 후속 기간/출력 서비스의 readOnly REPEATABLE_READ 트랜잭션 안에서 두 호출을 수행해야 한다. 원천을 두 번 읽어 두 보기/파일을 만들지 않는다.
- `TurnkeyRevenueCalculation.project(MonthlyPersonCost, boolean)`는 사본/계산 원본을 변경하지 않는 행 단위 TK 제외 투영이다. 원가는 그대로, TK 매출만 0으로 계산하며 원래 진단도 유지한다. 비용 미확인은 해소하지 않는다.
- TK 전용 사람의 표제 단가 null/평균단가 분모 제외와 N/H/U/F 등 개인 기간 표시 정책은 후속 집계 책임이다. 행 단위 제외값 0을 TK 전용 부모의 표제 표시와 혼동하지 않는다.
- `MonthlyCostService.summarize(List<MonthlyPersonCost>, boolean)`는 완전성에 따라 합계 원가/단가/이익을 null로 전파하고 알려진 원가/단가, MM, 문제월/진단을 별도로 제공한다. `PeriodAmounts.confirmedLoss()`는 이익 null을 적자로 분류하지 않는다. `averageCost(int)`는 호출자가 별도로 확정한 근무월 분모를 사용하며 다른 평균 정책을 대신하지 않는다.

## 보호 및 TK 규칙

- CLOSE/시작 전은 저장 사본만 읽는다. 시작 전 사본도 없으면 자료 없음과 빈 목록이다.
- 미마감 사람월에 FIX Y가 하나라도 있으면 그 사람월의 모든 사본을 권위 집합으로 읽는다. 현재 유효 투입 키의 사본이 빠졌다면 불완전 범위 진단을 반환하고 새 배분행을 만들지 않는다(D9의 보수적 결정).
- 원금/MM/분류/프로젝트파트/FIX/등록·수정시각을 유지한다. 현재 표시마스터가 없어도 ID와 숫자 사본은 남고 관련 원천 진단을 제공한다.
- 보호 월급여가 없으면 급여 참고는 null이다. 사본 노동비 합은 유지하며 간접 여부만 사본 분류로 판단한다. 현재 기본급/직원구분으로 보호 급여를 복원하지 않는다(D10).
- TK 단계는 기존 B6Comparison.selected를 프로젝트 전체 월/행에 적용한다. DEV 실제인력의 선택단계 어느 월에라도 사람이 있으면 빈 월은 0이다. ITEM/계약상 대체인력은 실제인력의 매출로 중복 가산하지 않는다.
- 사람월 선택액은 유상 MM에 DOWN으로 나누고 정렬상 마지막 유상행이 잔액을 갖는다. 유상행 없으면 가산하지 않고 배분 진단을 제공한다. FREE/비청구 분류에는 단가를 넣지 않는다.
- 실제인력의 선택단계 사람행이 전무할 때만 노동비 × 저장 계약금액 ÷ 저장 예산을 최종 원에서 DOWN한다. 예산 null/0은 단가만 미확인, 알려진 비용은 유지한다. 기존 B6Comparison.compare를 동일 원천의 불일치 안내에 재사용한다.
- 새 캐시가 없으므로 다음 요청은 commit된 급여 파생값/월매출/가격/경비/계약/예산/표/파트/마감 상태를 다시 읽는다. 미마감 재조회와 보호 사본 조회를 섞지 않는다.

## runner 시험 대상

기본 DB 없는 시험:

- TurnkeyRevenueCalculationTest: 단계/빈월/ITEM/실제인력/복수 셀/잔액/음수/FREE/유상0/대체식/예산 null·0/오버플로/TK 제외/원가 미확인/저장 MO·DP 월매출.
- MonthlyCostContractTest: 실제 controller와 service를 MockMvc로 통과. 사본 불변·FIX/감사시각·누락과 유효0·orphan/다중 ASGN_ID·혼합 FIX 누락·다음 요청의 원천 교체·다른 문제월 null 전파·LOSS·혼합 TK 제외·입력400·정보누출 방지500.

PostgreSQL 독립 기존자료 시험:

- MonthlyCostSnapshotDatabaseTest: 기존 CostDatabaseSupport의 읽기 전용 REPEATABLE_READ 연결/기존 fixture 전후 전체 원천 비교 사용. 스키마 설치나 예제 INSERT/UPDATE/DELETE 없음.
- 기존 support의 SICOST_COST_TEST_* 실행 환경 계약을 재사용한다. DB 설정이 없으면 이 통합 시험만 건너뛴다. 기본 단위/MockMvc 시험은 DB를 요구하지 않는다.
- 실제 네 키/두 키와 실제 컬럼을 metadata로 확인하고 TK UPLD_ID까지 원천 값을 비교한다. JOB_CD와 파트 종료월을 추정하지 않는다.
- 원문 조건이 있는 독립 기존 DB에서 TK 복수유상(OQ-CALC-003 식), 대체식(OQ-CALC-004 식), 빈 사람월, 예산 미확인, 미배분, CLOSE 누락/유효0을 검증한다. 필수 조건 부재는 fixture 실패이며 샘플을 자동 생성하지 않는다.
- OQ의 확정되지 않은 구체 원금 dataset, 실제 외부 commit/05 갱신 이후 조회, 실DB 부트/API 동작은 runner 영수증이 필요하다. repository 교체 단위시험은 실제 DB commit 검증으로 주장하지 않는다.

예정 runner 대상: backend에서 `mvn -Dtest=TurnkeyRevenueCalculationTest,MonthlyCostContractTest,MonthlyCostSnapshotDatabaseTest test`. 선행 MonthlyCostSource/Allocation/EstimatedPayrollAdapter/B1 회귀도 runner가 함께 확인한다. 이 명령은 이번 실행에서 수행하지 않았다.
