# 개인 원가·수익표 최종 검증 인계

## 판정 경계

awaiting runner verification.

이 실행은 최종 조립·계산 안내·문서·회귀 코드를 작성하는 codegen 단계다. caller가 실행을 소유하므로 Maven/npm 시험·빌드·컨테이너·서버·네트워크·DB·브라우저를 실행하지 않았다. 아래 명령은 runner 실행 대상이며 성공 기록이 아니다. 시안 비교/DB 응답 대조/XLSX 내용/03~07 회귀가 통과했다고 주장하지 않는다.

실행한 정적 확인: `git diff --check` 종료코드0. 이는 추적 파일 whitespace 확인일 뿐 타입검사/컴파일/DB/브라우저 증거가 아니다. write_file/patch의 저장 확인도 실행 성공으로 해석하지 않는다.

Phase7 미해결 실패가 있다는 caller 안내는 유지한다. 작업트리에 해당 실패 receipt/stack trace가 제공되지 않아 원인을 추정해 기존 계산·테스트를 약화하지 않았다. runner가 같은 실패를 재현하면 정확한 경계(환경/fixture/selector/계산/DB materialization)를 분류해야 한다.

## 추가 구현과 보존

- 기존 shared/IndividualProfitabilityScreen에서 실제 export/계산 안내 슬롯을 기본 연결했다. 기존 registry/라우터/작업 탭/즐겨찾기·YEAR·CSS 토큰·열설정은 유지했다.
- 서버 목록과 상세의 동일 calculationBasis에 급여/업로드우선/배분분모/분류·잔액/경비/TK/평균/사본 설명을 제공한다. 하단 회사손익 차이 문장은 승인 원문 그대로다. 사람 분모는 응답 workedMonths/monthBasis를 그대로 표시한다.
- 월원장 급여·수당·보험·퇴직/공제 참고값과 계산 근거는 같은 상세 응답을 사용한다. 새 계산/급여 동기화 API는 없다.
- 배지를 실제 재사용 컴포넌트로 추출했다. 알려진 assignments commit 이벤트도 다음 조회를 유발하도록 연결했다. 금융 연산은 서버에 유지한다.
- 원천/사본 INSERT/UPDATE/DELETE·DDL/seed·설치자료 재적재·auth 신설은 없다. 단위/기간/열 저장 및 다운로드 DNLD는 기존 명시 부수효과다.

## runner 실행 순서와 필수 기록

기존 명령/환경을 사용하고 플랫폼 비밀값을 출력하지 않는다. 각 receipt에는 명령, 작업디렉터리, 종료코드, 발견/실행/skip/실패 개수, 보고서 경로를 기록한다. 건너뜀을 합격으로 세지 않는다.

| 대상 | runner 명령/조건 | 현재 결과 |
| --- | --- | --- |
| 백엔드 기본·관련03~07 회귀 | backend에서 `mvn test` (기존 Maven/JUnit 설정). B1 공통급여/B6 비교·개발자·프로젝트·배정/급여·청구/재무·공통 Excel/설정 시험을 기존 suite와 함께 실행 | 미실행, exit code 없음 |
| 백엔드 패키징 | backend에서 `mvn package -DskipTests` (시험 결과와 별도). executable boot jar/정상 Spring 시작/Flyway 리소스 포함 확인 | 미실행 |
| 프론트 | frontend에서 `npm ci`, `npm run typecheck`, `npm run test -- --run`, `npm run build` | 미실행 |
| Compose/이미지 | `docker compose -f infra/docker-compose.yml config --quiet`와 runner 표준 build/preview. 기존자료 사본을 연결하고 설치 migration/seed를 다시 실행하지 않을 것 | 미실행 |
| 월/개인 실DB | CostDatabaseSupport의 SICOST_COST_TEST_DB_* 슬롯으로 독립 기존자료 DB/스키마를 전달한 뒤 관련 DatabaseTest 실행 | 미실행, 자료/접속 receipt 미제공 |
| 실제 XLSX·DNLD | 독립 DB + SICOST_COST_TEST_AUDIT_WRITE_CONFIRMED=true로 IndividualProfitabilityExportDatabaseTest 실행 | 미실행 |
| 실제 브라우저 | runner가 시작한 앱의 SICOST_E2E_BASE_URL을 전달해 e2e에서 `npm test -- tests/individual-profitability.spec.ts` | 미실행 |
| reference 시각 대조 | 생성된 desktop/mobile PNG와 기존 승인 캡처를 구조/색/간격/글꼴/아이콘/표/필터/접힘/단위/설정/근거 기준으로 대조 | 미실행; 외부 reference 재분석 안 함 |

새 e2e package는 @playwright/test=1.51.1로 고정했다. codegen에서 package manager 실행/lockfile 수작업 작성은 금지되어 e2e/package-lock.json은 아직 없다. runner의 후속 dependency-materialization 단계에서 npm으로 lockfile을 생성한 다음 npm ci를 실행해야 한다. 기존 frontend/package-lock.json은 수정하지 않았다. 브라우저는 제공된 PLAYWRIGHT_BROWSERS_PATH=/opt/ms-playwright를 사용하고 install/install-deps/apt-get은 하지 않는다. config는 dev server를 띄우지 않는다.

## 실DB 무쓰기/산식/출력 증거

기존 `CostDatabaseSupport`는 독립 스키마의 default_transaction_read_only=on 연결을 사용하고, 앱/재설치 시험 DB 재사용을 거부한다. 데이터 생성·삭제·DDL·Flyway 실행을 하지 않는다. 필수 원문 조건이 없으면 fixture 진단 실패이며 임의 예시로 대체하지 않는다. 시험은 각각 연결/전후 snapshot을 소유하고 데이터 순서 의존성이 없다.

| 검증 | 실제 시험 경로/판정 |
| --- | --- |
| 원천 실체화/PK/과거 원천/중지·퇴사 인력 | MonthlyCostSourceDatabaseTest, MonthlyCostAllocationDatabaseTest. 기존 네 칸 원가PK/당시 분류/월원천/quoted alias와 실제 PostgreSQL 값 대조 |
| 합 보존/마지막행/음수 DOWN/경비 | MonthlyCostCalculationTest + MonthlyCostAllocationDatabaseTest. 원문 세 배분 사례는 기존 독립DB에서 찾아야 하며 자동삽입하지 않는다 |
| TK/사본·유효0/사본0행/고정혼합 | TurnkeyRevenueCalculationTest, MonthlyCostSnapshotDatabaseTest 및 MonthlyCostAllocationRegressionTest. 원본 snapshot 비교와 누락 진단 확인 |
| 평균 분모/인원·율/전체합계 | IndividualProfitabilityCalculationTest, IndividualProfitabilityDatabaseTest. 추가 시험은 실제 monthBasis 포함수=N/재직달/투입달, 기초금액별 DOWN, 완전 월의 노동비 합 보존, 누계/평균 전체 부모 합계를 독립 대조한다 |
| 동일 서버 근거 | IndividualProfitabilityContractTest의 실제 목록/상세 MockMvc 요청. 같은 BASIS 및 급여/분모/잔액/경비/TK/N 설명을 단언한다 |
| 파일/목록/독립 LOSS 인구/숫자셀 | IndividualProfitabilityExportContractTest, IndividualProfitabilityExportDatabaseTest. 기존 실제 두 시트 인구·상세행수·로그 검사에 필수 단가/원가/이익의 부모 및 전체합계 숫자셀/API 일치·NULL빈셀을 추가했다 |
| 업무/감사/설정 불변 | 각 DB 시험 @AfterEach의 db.snapshot 전후 완전값 비교. 급여/월배정/원가/회사손익/개발자/가격/휴직/프로젝트/경비/TK/파트/기준/마감/청구/수금/재무/변경이력/설정을 포함하며 단순 count 비교가 아니다 |
| 다운로드 허용 쓰기 | ExportDatabaseTest만 별도 opt-in으로 DNLD를 기록한다. 자기 LOG_ID만 추적/삭제하고 나머지 모든 키·값·시각은 전후 동일. 로그가 파일전송 전에 commit되는지 기존 계약 시험의 실패주입도 실행 |
| 일관성/무잠금 | 실제 repeatable read/read-only/pg_current_snapshot 동일과 쓰기잠금 부재를 기존 DB 시험에서 검증. 대역 동시성 시험을 실제 DB 동시 writer 합격으로 대체하지 않는다 |

전후 원천 값이 하나라도 변경되면 불합격이다. 보고서에 급여/개인금액·비밀 원문을 출력하지 말고 영향 테이블/키 범주와 결과만 기록한다. 실제 동시 source writer commit 중 API/XLSX 일관성은 runner가 별도의 독립자료 시나리오에서 실행·receipt로 남겨야 한다.

개발자 이름 클릭의 기존 DeveloperDrawer GET에는 레거시 상태 refresh가 있다. 이는 명시 사용자 상세 동작으로 별도 회귀 실행한다. 계산·목록·월원장·출력 무쓰기 시험에 섞지 않으며 그 부수효과를 숨겨 무쓰기 합격으로 기록하지 않는다.

## 브라우저/컴포넌트 증거

`e2e/tests/individual-profitability.spec.ts`는 API interception 없이 실제 앱을 사용한다.

- 실제 목록 응답의 서버 연도·인물·요약·필수열 전체합계를 DOM과 대조한다. 시안의 날짜/인물/계수를 기대값으로 박지 않는다.
- 원문 안내와 calculationBasis 전체를 비교하고 screenshot을 test output으로 저장한다.
- 월평균 전환 후 선택한 실제 DEV_ID 상세 GET을 기다리고 원본 원장/투입ID·급여출처를 대조한다.
- 명시 다운로드 실제 파일/서버 파일명/실패없음을 확인한다. workbook 숫자셀·DB 원천 및 audit commit 증명은 JUnit/POI 실DB 시험과 함께 판정한다.
- 모바일 size20/카드·근거·다운로드미호출과 PC 복귀를 확인한다.

렌더 시험은 frontend의 기존 Vitest/jsdom 설정을 사용한다. 새 source-text 시험이나 raw import는 없다. 등록 진입점 export/guide 조립, 서버 근거 변경·빈근거, 서버분모, 원문 문장, 모바일, commit 후 최신목록, 기존 배지/정렬/합계/원장/Drawer 상태를 검증한다. SharedScreens/공통 DataTable/설정 기존 회귀도 제거하지 않았다.

## 인계 문서

- `docs/monthly-cost-service.md`: 실제 서비스 입출력/사본우선/분류·잔액순서/완전성/DTO 금액구별/무효화.
- `docs/profitability-components.md`: 실제 경로·Props·사용처/확장 DataTable/표시와 서버계산 경계.
- 기존 source-foundation/monthly-allocation/turnkey-snapshots/shared-presentation/personal-overview/ledger-detail/export 문서는 앞선 그룹 근거로 보존한다.

## 이 실행에서 변경한 파일

- backend/src/main/java/com/sicost/profitability/IndividualProfitabilityCalculation.java
- backend/src/test/java/com/sicost/profitability/IndividualProfitabilityContractTest.java
- backend/src/test/java/com/sicost/profitability/IndividualProfitabilityDatabaseTest.java
- backend/src/test/java/com/sicost/profitability/IndividualProfitabilityExportDatabaseTest.java
- frontend/src/pages/shared/IndividualProfitabilityScreen.tsx
- frontend/src/pages/profitability/IndividualProfitabilityScreen.tsx
- frontend/src/pages/profitability/IndividualProfitabilityScreen.test.tsx
- frontend/src/pages/profitability/ProfitabilityBadges.tsx
- frontend/src/pages/profitability/ProfitabilityCalculationGuide.tsx
- frontend/src/pages/profitability/ProfitabilityMonthlyLedger.tsx
- frontend/src/pages/profitability/ProfitabilityMonthlyLedger.test.tsx
- frontend/src/pages/profitability/useIndividualProfitability.ts
- frontend/src/pages/profitability/profitability.css
- e2e/package.json
- e2e/playwright.config.ts
- e2e/tests/individual-profitability.spec.ts
- docs/monthly-cost-service.md
- docs/profitability-components.md
- docs/profitability-verification.md

runner 전용 task-status/final-verification.json은 작업상태 전달용이며 소스/빌드/런타임 입력이 아니다. 커밋/스테이징하지 않았다.
