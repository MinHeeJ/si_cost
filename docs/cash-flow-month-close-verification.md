# 월 마감 명령 보완 및 검증 인계

## 구현 범위와 보존

이번 실행 시작 시 지정된 POST 핸들러는 이미 현재 작업트리에 있었다. 중복 Controller, 라우트, 메뉴 또는 인증을 만들지 않았다.

- 실제 진입점: `com.sicost.monthclosesops.MonthCloseController`.
- 기존 `/UI-14` → shared 화면 export → `pages/monthclosesops/MonthCloseScreen.tsx` 연결을 유지했다.
- `checks`, `preview`, `close`, `reopen-preview`, `reopen`은 기존 `MonthCloseService`에 연결되어 있다.
- API 봉투, 오류 처리, BASE~현재월 관리범위, 일반 화면의 기간 제한, 임시 관리자 컨텍스트를 보존했다.
- 기존 Flyway 스키마를 사용한다. 신규 DDL, seed 재적재, 물리 operationId 컬럼, 현금 저장표는 추가하지 않았다.
- 기존 사용자 변경사항과 입력 문서를 수정하거나 정리하지 않았다.

## 경로 확인 원장

| Operation | Controller → Service | 트랜잭션 / 저장 경계 |
| --- | --- | --- |
| saveMonthCloseChecks | saveMonthCloseChecks → saveMonthCloseChecks | 배타 경계 후 전체 revision 검사, 필요한 WORK 부모와 CHK 6행만 저장 |
| createMonthClosePreview | createMonthClosePreview → createMonthClosePreview | REPEATABLE_READ 조회, 최신 전체 계산, 업무 쓰기 없음 |
| saveMonthClose | saveMonthClose → saveMonthClose | 배타 경계, 최신 동일 명령 replay 우선, 전체 계산/사본/AUTO/CHK/CLOSE/HIST/필수 감사 및 접근기록 단일 transaction |
| createMonthCloseReopenPreview | createMonthCloseReopenPreview → createMonthCloseReopenPreview | REPEATABLE_READ 조회, 최신 CLOSE/소유 사본/모든 후속조정 확인 |
| saveMonthCloseReopen | saveMonthCloseReopen → saveMonthCloseReopen | 배타 경계, 동일 명령 replay 우선, 소유 FIX만 N, WORK/OPEN 및 필수 기록; 정산 보존 |

경로의 공통 하위 경계는 `MonthCloseRepository` → `MonthCloseMapper` / `mappers/monthcloses/MonthCloseMapper.xml`이다. `MonthClosePersistence`는 선택월·전체 PK·FIX 및 소유감사 조건을 검사한다. `MonthCloseBoundary` / interceptor는 기존 일반 쓰기와 관리 명령을 공유/배타로 직렬화한다. 정상 주체는 기존 DB 임시 관리자이며 새 인증/역할 검사는 도입하지 않았다. 이는 실행 증거가 아닌 현재 코드의 정적 경로 확인이다.

## 이번 보완

1. 마감 미리보기의 별도 현금 잔액 계산을 제거했다. `MonthCloseCalculation`은 `CashFlowService.calculateCloseImpact`를 호출한다. 자금수지의 항목별 계산·BASE 연속 이월·외주 부가세·전월 공제액·NULL 전파를 그대로 사용하고 선택월 CLOSE만 가정한다. 실제 원천이나 CLOSE 행은 수정하지 않는다.
2. PAY/FI의 실제 설명과 SETL 전체 대상/미확정 수를 공통 점검 결과에 넣어 저장/미리보기/당시 사본의 설명이 일치하도록 했다.
3. UI-14 최상위에 정확한 화면 ID를 넣고 모달/확인/취소 및 명세 action ID를 연결했다. 본문 6카드와 미리보기 6카드의 test ID는 충돌하지 않는다. 전체기간 최신 CLOSE에만 해제 CTA를 표시한다. 공통 Dialog/Feedback의 새 식별자 props는 선택 사항이다.

## 작성한 시험

- `MonthCloseContractTest`: 실제 Controller/Service를 통과하는 5개 POST의 필수입력, 주체 주입, 기간, stale, 404, WARN 수락, 순서/소유 충돌, 명령 replay/입력 충돌, 오류 비노출 회귀.
- `CloseCheckServiceTest`: 급여 중복제거, PAY/FI 유효 0, 사람별 MM 합/동액 경계, 취소 청구 제외, 대상 파트와 첫 미확정 파트 문맥.
- `CashFlowContractTest`: 연간 자금수지와 마감 영향의 동일 결과, 외주 VAT/전월 공제, 예정 미수/세금 제외, NULL 잔액 유지.
- `MonthCloseDatabaseTest`: 실제 Spring MVC/서비스/계산/MyBatis/PostgreSQL의 5개 명령 경로. 시험마다 새 스키마를 생성하고 자체 파트/세금 자료를 사용한다. 공유 seed 행을 수정하지 않는다. 마감/해제/재전송, PL FIX/MIGR, AUTO 유지, 접근기록 실패와 세 번째 CHK 저장 실패의 전체 rollback을 read-back한다.
- `MonthCloseScreen.test.tsx`: jsdom 렌더/입력 기반 시험. 최신 해제 preview/revision/UUID/사유 전송, 모달 취소와 초점, stale 후 GET 복구, 서버 허용동작 및 식별자 회귀.
- `e2e/tests/month-close.spec.ts`: 실제 runner 앱의 6카드/이력, 5개 POST 입력 거부와 무변경 read-back, 최신 미리보기 취소/초점 복원. 공용 월을 실제 마감/해제하지 않는다. 선택월이 다음/최신 마감월이 아니면 미리보기 시나리오는 명시적으로 skip한다. 전체 쓰기 경로는 위 격리 DB 시험에서 담당한다.

## 실제 수행한 검사

- `git diff --check`: exit 0.
- Python XML 파싱: month-close mapper 및 Maven pom 파싱 성공.
- 현재 Controller 매핑 정적 확인: checks / preview / close / reopen-preview / reopen 모두 존재.
- 편집 파일의 줄 끝 공백 검사: 통과.
- 시험용 현금 산술 oracle은 Python Decimal로 확인했다. 운영 데이터나 실DB 결과가 아니다.

## Runner 실행 대상

코드 생성 요청의 실행 소유 제한을 따라 Maven/npm/Playwright/컨테이너/서버를 실행하지 않았다. 컴파일·시험 성공·DB rollback·화면 캡처 완료를 주장하지 않는다. 상태: awaiting runner verification.

- backend: `mvn test -Dtest=MonthCloseContractTest,CloseCheckServiceTest,CashFlowContractTest,MonthCloseDatabaseTest`
- PostgreSQL 통합시험은 기존 `SICOST_TEST_DB_URL`, `SICOST_TEST_DB_USER`, `SICOST_TEST_DB_PASSWORD` 구성을 사용한다. URL이 없으면 해당 통합시험은 조건부 비활성이고 통과 증거가 아니다.
- frontend: `npm run test -- --run src/pages/monthclosesops/MonthCloseScreen.test.tsx src/app/financialManagementRoutes.test.tsx src/pages/apiops/FinancialManagementScreens.test.tsx`
- frontend: `npm run typecheck`, `npm run build`
- e2e: `npm test -- tests/month-close.spec.ts` (기존 `SICOST_E2E_BASE_URL` 필요; 서버는 runner 소유)

이 문서는 승인된 논리 감사 저장규약의 추가 승인이나 운영 수치 벡터의 승인을 대신하지 않는다. 실제 실행 수락은 runner 영수증으로 별도 판단한다.

## 이번 실행에서 변경한 파일

- backend/src/main/java/com/sicost/cashflowops/CashFlowService.java
- backend/src/main/java/com/sicost/monthclosesops/MonthCloseCalculation.java
- backend/src/main/java/com/sicost/monthclosesops/CloseCheckService.java
- backend/src/test/java/com/sicost/cashflowops/CashFlowContractTest.java
- backend/src/test/java/com/sicost/monthclosesops/MonthCloseContractTest.java
- backend/src/test/java/com/sicost/monthclosesops/CloseCheckServiceTest.java
- backend/src/test/java/com/sicost/monthclosesops/MonthCloseDatabaseTest.java
- frontend/src/shared/Dialog.tsx
- frontend/src/shared/Feedback.tsx
- frontend/src/pages/monthclosesops/MonthCloseScreen.tsx
- frontend/src/pages/monthclosesops/MonthCloseScreen.test.tsx
- e2e/tests/month-close.spec.ts
- docs/cash-flow-month-close-verification.md
