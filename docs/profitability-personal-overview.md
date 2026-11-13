# 개인 기간 집계 및 UI-03 본문 인계

상태: 구현 및 시험 코드 작성, awaiting runner verification. 이 단계에서는 Maven/npm/DB/브라우저 시험·빌드·설치·서버 실행을 하지 않았다. 실행 성공을 주장하지 않는다.

## 보존 및 변경

- 기존 UI-03 등록 경로, SharedScreenFrame/ScreenPeriodBar, 마운트된 작업 탭, 즐겨찾기, 공통 열/단위/밀도/페이지 설정을 유지한다. 초기화의 YEAR 복귀는 기존 기간 저장 경계로 전달한다.
- 기존 MonthlyCostService/MonthlyCostRepository의 회사 전체 읽기 결과를 사용한다. DeveloperQueryService의 refresh나 급여/배정 동기화는 호출하지 않는다.
- 신규 마이그레이션·테이블·시드·원가/급여/배정/손익 쓰기는 없다. 새 SQL은 기존 협력사 마스터의 ID/이름 SELECT뿐이다.
- 인증 유예를 유지한다. 임시 ADMIN은 기존 CurrentUserProvider의 DB 컨텍스트이며 신규 호출자 역할 검사·로그인·금액 가림을 추가하지 않았다.

## 변경 경로 원장

| 경계 | 실제 구현 및 근거 |
| --- | --- |
| 계약/진입 | getIndividualProfitability GET /api/individual-profitability → IndividualProfitabilityController.getIndividualProfitability, getIndividualProfitabilityPerson GET /api/individual-profitability/{personId} → 동일 컨트롤러의 getIndividualProfitabilityPerson |
| 입력 | IndividualProfitabilityInput.query(MultiValueMap, boolean): 목록 14개 쿼리 키, 상세 page/size 금지, 중복/미등록/strict boolean/허용 정렬 및 방향/페이지 경계 검증 |
| 인증/권한 | 공개 읽기. 기존 CurrentUserProvider.userId → CurrentUserMapper.findAdministrator/findAdministratorRoles를 MonthlyCostRepository에서 사용. 신규 미들웨어/권한 변경 없음 |
| 응용/트랜잭션 | IndividualProfitabilityService 클래스의 readOnly REPEATABLE_READ; snapshot(Query) → MonthlyCostService.loadYear(Integer), calculate(Sources) 한 번씩 |
| 영속성 | 기존 MonthlyCostRepository와 MonthlyCostMapper의 집합 원천 조회, DeveloperRepository.historicalPeople/historicalPrices/historicalStates 재사용; 추가 MonthlyCostMapper.companies()만 선택 필터 검증용 |
| 스키마/실체화 | 기존 V1__full_schema.sql의 TB_BS_CLNT.CLNT_ID/CLNT_NM/CLNT_TYPE_CD; quoted companyId/name alias → MonthlyCostDtos.Company. 기존 원가 PK COST_YM/DEV_ID/PROJ_ID/ASGN_ID와 기존 DTO 보존 |
| 계산/출력 | IndividualProfitabilityCalculation: 직접월, N, 실제행 MM, 급여 사람월 1회, P/E1/E2/R/C/G, TK 표시 투영, H/U/F, NULL LAST 정렬, 전체 합계와 직무·등급 공헌이익 |
| 부수효과 | 조회/투영 무쓰기·무캐시. 사용자 명시 단위/페이지/기간 설정만 기존 preference 경계 사용. 다운로드/접근기록은 후속 그룹 책임 |
| 회귀 시험 | IndividualProfitabilityCalculationTest, IndividualProfitabilityContractTest, IndividualProfitabilityDatabaseTest, IndividualProfitabilityScreen.test.tsx, individualProfitabilityApi.test.ts, SharedScreens.test.tsx의 UI-03 실제 본문 확인 |

## 후속 그룹이 재사용할 인터페이스

- `IndividualProfitabilityService.snapshot(Query)`는 `Snapshot(sources, common, jobs, grades)`를 한 DB 읽기 스냅샷에서 만든다.
- `project(Snapshot, Query, boolean allRows)`는 DB를 재조회하지 않는다. 다운로드는 한 번 만든 Snapshot에 TK 포함/제외 Query를 각각 적용한다. LOSS도 각 투영 뒤 독립 적용한다. allRows=true면 페이지 제한을 적용하지 않는다.
- `getIndividualProfitabilityPerson(personId, Query)`는 같은 필터 문맥의 부모와 월별 원본 금액/급여 출처/문제를 반환한다. 평균 부모와 무관하게 월별 금액은 평균으로 나누지 않는다. 선택한 TK 표시 투영만 적용한다. 원본 Sources/사본은 변경하지 않는다.
- DTO는 기존 `MonthlyCostDtos` 안의 IndividualProfitability* 레코드를 계속 사용한다. 프론트 API/상세 타입은 `individualProfitabilityApi.ts`에 있다. 별도 월 계산/개인 조회 서비스를 복제하지 않는다.
- 기존 shared/IndividualProfitabilityScreen은 기본으로 실제 본문을 표시하며 children 주입 호환성을 유지한다.
- 본문의 typed props: `onPerson(personId)`, `renderLedger(person, appliedQuery)`, `renderExport(appliedQuery, response)`, `renderCalculationGuide(response)`. 후속 그룹은 이 슬롯에 기존 개발자 Drawer·월원장·다운로드·계산 안내를 연결한다. 새로운 라우터나 별도 개발자 상세는 만들지 않는다.
- 모바일은 renderExport를 호출하지 않고 서버 size=20으로 더보기를 누적한다. PC 복귀 시 기존 PAGE_SIZE를 복원한다. 고정 이름/가로 스크롤/열 설정은 기존 ProfitabilityTable을 사용한다.

## UI 계약 체크리스트

- /UI-03: YEAR 연도에 대한 서버 기간이 표시 기준. 조회/Enter 초안 적용, 초기화, 누계/평균 전환, TK/LOSS/CONCURRENT 조건, DB 옵션 라벨.
- 사람 표: 18 기본열/4 숨김 참고열, 누계 급여 제목, 이름 고정, 서버 정렬·전체 합계, 페이지 변경/조건 변경 펼침 초기화, 예정/미확인/1.0초과 분리.
- 요약/공헌이익: 서버 필터 전체 결과와 회사 불변 H/U/F를 구별. 공헌이익 기본 접힘, DB 직무/등급 라벨과 미분류/전체 및 인원/미확인수 표시.
- 상태: 로딩, 빈 운영기간/빈 조건/초과 페이지, 필드별 오류 및 GET 재시도, NULL/유효0/TK 제외 구별. 서버 오류는 빈 결과로 위장하지 않는다. 늦은 응답은 abort+sequence로 무시한다.

## runner 검증 대상과 제한

- 기본 백엔드: IndividualProfitabilityCalculationTest, IndividualProfitabilityContractTest. DB 없이 저장소 대역으로 실제 HTTP→컨트롤러→서비스→계산 경계를 확인한다.
- 실제 PostgreSQL: IndividualProfitabilityDatabaseTest는 기존 CostDatabaseSupport의 독립 자료 DB 설정을 사용하며 DB URL이 없으면 건너뛴다. 원천·설정·감사의 전체 키/값/시각 전후 비교, 실제 HTTP/계산식, REPEATABLE READ/read-only 및 쓰기 잠금 부재를 검증한다. 마이그레이션/seed를 재실행하지 않는다.
- 동시 변경: 계약 시험은 원천 집합 교체와 투영 간 불변성을 검증한다. 실제 PostgreSQL 동시 급여/배정 commit 중 조회 및 XLSX 파일의 앞뒤 일관성은 runner의 독립 자료 시나리오로 추가 실행해야 한다. 저장소 대역 시험을 실제 DB 동시성 성공으로 간주하지 않는다.
- OQ-CALC-005 구체 독립 DB 자료는 runner 제공이 필요하다. 기초금액별 DOWN 후 원가/이익 재산출·N0 NULL 및 원본 불변 수식 시험은 작성했다. 임의 초기 데이터나 금리를 생성하지 않는다.
- 프론트: npm run test -- --run, npm run typecheck, npm run build. 컴포넌트 시험은 렌더링/입력/정렬/크기/모바일/늦은 응답을 확인하며 소스 문자열 단언은 없다. 실제 브라우저 DB 대조·시각 증거 및 전체 XLSX는 후속 최종 검증 범위다.

## 이번 그룹 변경 파일

- backend/src/main/java/com/sicost/profitability/IndividualProfitabilityInput.java
- backend/src/main/java/com/sicost/profitability/IndividualProfitabilityCalculation.java
- backend/src/main/java/com/sicost/profitability/IndividualProfitabilityService.java
- backend/src/main/java/com/sicost/profitability/IndividualProfitabilityController.java
- backend/src/main/java/com/sicost/profitability/MonthlyCostDtos.java
- backend/src/main/java/com/sicost/profitability/MonthlyCostMapper.java
- backend/src/main/resources/mappers/profitability/MonthlyCostMapper.xml
- backend/src/test/java/com/sicost/profitability/IndividualProfitabilityCalculationTest.java
- backend/src/test/java/com/sicost/profitability/IndividualProfitabilityContractTest.java
- backend/src/test/java/com/sicost/profitability/IndividualProfitabilityDatabaseTest.java
- frontend/src/pages/profitability/individualProfitabilityApi.ts
- frontend/src/pages/profitability/individualProfitabilityApi.test.ts
- frontend/src/pages/profitability/useIndividualProfitability.ts
- frontend/src/pages/profitability/IndividualProfitabilityScreen.tsx
- frontend/src/pages/profitability/IndividualProfitabilityScreen.test.tsx
- frontend/src/pages/profitability/ProfitabilityFilters.tsx
- frontend/src/pages/profitability/ProfitabilitySummary.tsx
- frontend/src/pages/profitability/ProfitabilityContributionTable.tsx
- frontend/src/pages/profitability/profitability.css
- frontend/src/pages/shared/IndividualProfitabilityScreen.tsx
- frontend/src/pages/shared/ScreenPeriodBar.tsx
- frontend/src/pages/shared/SharedScreens.test.tsx
- docs/profitability-personal-overview.md

앞선 그룹의 기존 변경은 보존했으며 커밋/스테이징하지 않았다. runner 전용 작업 상태 JSON은 커밋 대상이 아니다.
