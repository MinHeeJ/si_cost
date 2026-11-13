# 개인 원가 월별 원장과 개발자 상세

## 구현 범위

T013, T100, T116, T117, T123, T133의 구현 및 회귀 시험 코드다. 실행 결과는 아직 없으며 awaiting runner verification 상태다.

- 기존 /UI-03 진입 래퍼, React Router, 작업 탭, 조회·설정·계산 DTO를 유지한다.
- IndividualProfitabilityScreen의 기본 ledger/person 동작을 연결했다. 기존 renderLedger/onPerson 슬롯은 계속 우선한다. 후속 출력·계산 안내 슬롯도 유지한다.
- useProfitabilityDetails가 DEV_ID 펼침 키와 선택한 개발자를 소유한다. 상세 닫기는 조회 조건·페이지·부모 표 DOM·펼침 키를 변경하지 않는다. 조회 문맥/반응형 모드 변경 시 펼침과 선택을 초기화한다.
- 현재 페이지 모두 펼치기는 현재 응답 items의 DEV_ID만 사용한다. 단일 투입도 펼칠 수 있다. 자식은 월/프로젝트/투입 ASC로 정렬하고 전체 원가 복합PK를 충돌 없이 인코딩한 키를 쓴다.
- 월 원장은 서버의 원본 금액만 표시한다. 누계/평균 전환에 따른 클라이언트 금액 재계산이나 급여 참고값의 투입별 복제는 없다. 원가/이익/율, 인건비 출처, 미확인 진단과 예정 표시를 유지한다.
- 프로젝트/투입 표시 마스터가 없으면 프로젝트 ID/투입 ID 및 직무 –로 표시하며 행을 버리지 않는다. 고정 사본, IDLE 남는 몫, 실주 대기, 내부, PRE, 무상지원을 구별한다.
- 이름/부모행은 ProfitabilityDetails를 거쳐 기존 DeveloperDrawer와 peopleClient.getDeveloper(DEV_ID)를 연다. 별도 편집기나 개발자 상세 복제는 없다. onEdit가 없는 호출자는 수정 버튼이 없으며 기존 호출자는 그대로 동작한다.
- 원장 펼침·도움말은 이벤트 전파를 차단한다. 열 설정은 부모 데이터행 밖의 기존 공통 동작을 재사용한다.

## 읽기 경로 근거

getIndividualProfitabilityPerson: GET /api/individual-profitability/{personId}, security=[]; 신규 인증/역할 우회나 제한을 도입하지 않는다. 기존 임시 DB 사용자 문맥은 MonthlyCostRepository.loadYear에서 CurrentUserProvider.userId()를 사용한다.

IndividualProfitabilityController.getIndividualProfitabilityPerson -> IndividualProfitabilityInput.query(detail=true) -> IndividualProfitabilityService.getIndividualProfitabilityPerson(String, Query) -> snapshot(Query) -> MonthlyCostService.loadYear(Integer) -> MonthlyCostRepository.loadYear(Integer).

기존 @Transactional(readOnly=true, isolation=REPEATABLE_READ) 경계를 유지한다. 저장소는 역사 사람/가격/상태 및 MonthlyCostMapper.storedCosts(Range) 등 SELECT만 호출한다. TB_CO_MON_COST의 COST_YM/DEV_ID/PROJ_ID/ASGN_ID 복합PK, 기존 MyBatis quoted alias 및 StoredCost.toRow 매핑을 변경하지 않았다. 서비스는 같은 query 필터의 부모와 월별 원본/laborSource/completeness/issues를 반환한다. 없는/조건에 맞지 않는 사람은 PERSON_NOT_FOUND 404, 존재 사람의 사본 누락은 200 및 costComplete=false와 issues다. 원가 수정/삭제 경로를 추가하지 않는다.

명시적 개발자 상세 클릭의 기존 getDeveloper 경로는 상태 refresh 동작과 원천 작업 탭을 포함한다. 이 기존 동작은 유지했으며 계산/월원장 조회에서는 호출하지 않는다. 계산 읽기 불변성 검증과 개발자 상세 클릭의 기존 동작은 별개의 검증 경계다.

## Runner 검증 대상

- frontend: ProfitabilityMonthlyLedger.test.tsx (실제 화면/원장/기존 Drawer 렌더), 기존 IndividualProfitabilityScreen.test.tsx 및 DevelopersScreen.test.tsx 회귀.
- backend: IndividualProfitabilityPersonContractTest (실제 HTTP/controller/service/calculation, 영속 어댑터만 교체). 404, 동일 필터 문맥, 마감 사본 누락과 유효 0, 같은 프로젝트 두 투입, 평균 전 원본, 출처/문제, 민감 오류 비노출, 쓰기 매핑 부재.
- backend: IndividualProfitabilityDatabaseTest의 사람 상세 시험은 기존 CostDatabaseSupport의 독립 PostgreSQL 읽기 전용 DB를 사용한다. FX-B137-CLOSE에 해당하는 실제 마감월·같은 프로젝트 두 투입을 찾아 모든 사본 필드와 두 보기 원장을 비교하고, 기존 afterEach에서 업무/설정/감사값 전체를 대조한다. 자료가 없으면 명시적으로 실패하며 DDL·시드·예제를 생성하지 않는다.
- UI 시험은 vitest/jsdom 렌더 결과만 단언한다. 소스 문자열 비교, 패키지 설치, 테스트 실행, 브라우저/서버 실행은 하지 않았다. 브라우저의 실제 픽셀/스크롤과 작업 탭 전환은 runner의 실사용 검증이 필요하다.

후속 그룹은 별도 서비스/mapper/DTO/상세를 만들지 않고 기존 월 원가 및 개인 집계, 기본 원장, 기존 Drawer 연결과 화면 슬롯을 확장한다.
