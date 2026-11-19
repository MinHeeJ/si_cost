# 통합1 표시·입력 검증 변경 및 확인 결과

기준: 요청 본문의 CHG-0101, CHG-0104, CHG-0105, CHG-0201~0206 및 인용된 요구사항 v3.1.
기존 작업트리 수정을 보존하고 이어서 확인·보완했다. Scope 보고서와 인접 `.scope` 폴더는 열지 않았다.
하위 코딩 에이전트, 커밋, 푸시는 사용하지 않았다.

## CHG별 결과

| 구분 | 결과 | 확인한 화면·동작 |
|---|---|---|
| 공통 | 고침 | API 기반 `codeNames`, 업무명 `BusinessName`, 숫자 `NumericInput`, 칸별 오류 `FieldErrors`, 위험 `Dialog`, 미저장 `UnsavedDialog`, 요약 큰 금액 함수 |
| CHG-0101 | 고침 | UI-29 카드·종료·상세·필요인력·승인이력·수주/실주, UI-12 이력·입력자·변경/정정·월별표·직무 필터, UI-02 이력·직원정보·투입상태, UI-01 배분단계·상태·직무, UI-31 높음 이슈, UI-17 직무, UI-30 후보 근거·추천기록, UI-05 추천, UI-28 프로젝트·연장 가능성·개발자 상세 |
| CHG-0104 | 고침 | UI-12·17·30·33 단위 선택 제거·원 표시, UI-29 카드·종료 원 표시, UI-29 요약/예측 요약 큰 금액, 예측 월별 표만 단위 적용, UI-05 원 표시, UI-12·33 내려받기 원 단위 |
| CHG-0105 | 고침 | UI-29·05·30 수주확률을 기존 formatRate로 표시 |
| CHG-0201 | 고침 | UI-02 등록/수정·B.8, UI-01 등록/수정·B.9, UI-33 기술/외주업체, UI-12 변경기록의 필수 칸 표시 |
| CHG-0202 | 고침 | 서버의 칸별 violations, 개발자 미선택·계약/퇴사 이후 철수·청구단가·월MM·필수 사유·필요인력 줄·확률·예상금액·단계별 날짜·업로드 날짜/관리번호 문구, 칸 아래 표시 및 첫 오류 포커스/스크롤 |
| CHG-0203 | 고침 | B.5 및 같은 결함이 있던 B.7·B.8·B.9의 원래 값/변환 이름, 미등록 값 표시. 공통 메시지 함수에서 한국어 조사도 처리 |
| CHG-0204 | 고침 | 금액·MM·확률·인원·실적 입력에 공통 정제 사용. 화면의 금액 콤마는 요청값에서 제거하여 원 단위 숫자로 전달 |
| CHG-0205 | 고침 | UI-29 삭제/실주, UI-02 사용중지, UI-01 실주, UI-30 제외의 질문형 제목·빨간 실행 버튼 |
| CHG-0206 | 고침 | UI-33 계속 작성/나가기 및 입력 유지, UI-29 기회 폼 dirty 상태를 실제 화면 탭에 전달 |

## 이미 맞음·보존한 부분

- formatRate 자체는 이미 소수 한 자리 퍼센트였다. 표시 위치만 연결했다.
- formatAmount 기존 출력/단위 변환 로직은 변경하지 않았다. formatSummaryAmount만 추가했다.
- UI-03·04 및 명세가 지정한 재무 화면의 단위 선택을 보존했다.
- UI-31 실적 진척률에는 필수 표시를 추가하지 않았다.
- UI-02·01·12·31의 기존 미저장 보호 및 저장 후 재조회/충돌 처리는 유지했다.
- DB 스키마·마이그레이션·공통코드 데이터·금액 계산식·권한·라우트는 변경하지 않았다.
- 미사용 `pages/history/AssignmentHistoryPanel.tsx`는 변경하지 않았다.
- 계정 안내 파일과 자격 증명 파일은 읽거나 변경하지 않았다.

## 실제 검증 결과

### 프론트

- CHG별 관련 집중 검증: 대표 집중 실행 21개 파일, 423개 시험 통과.
- 전체 시험은 요청대로 한 번 실행:
  `npm --prefix frontend test -- --run --maxWorkers=2 --minWorkers=1 --testTimeout=15000`
  결과: 217개 파일 중 215 통과/2 실패, 2,802개 시험 중 2,800 통과/2 실패.
- 실패한 두 시험은 단계별 오류 응답을 여전히 공통 `steps` 오류로 가정했다.
  새 서버 계약의 `steps[0].endDate`로 맞추고, 정상 행 미표시·첫 오류 포커스·문의번호 확인을 보강했다.
  숫자 즉시 정제에 맞춰 실적 입력 시험도 기존 범위/소수 거부 시험을 유지하면서 정제된 값 저장을 검증했다.
- 수정 후 관련 3개 파일 재검증:
  `npm --prefix frontend test -- --run src/pages/progress/ProjectProgressPanel.test.tsx src/pages/receivablesops/ProjectDatesPreview.test.tsx src/pages/projects/ProjectsScreen.test.tsx --maxWorkers=2 --minWorkers=1 --testTimeout=15000`
  결과: 3개 파일, 56개 시험 모두 통과.
- 전체 시험을 두 번째 실행하지 않았다. 따라서 마지막 전체 실행 자체가 전부 통과했다고 표현하지 않는다.
- `npm --prefix frontend run build`: TypeScript 검사 및 Vite 빌드 통과.
  기존 동적/정적 import 혼용 및 큰 번들 경고는 남아 있다.
- `git diff --check`: 통과.

### 백엔드

Java 17 및 Maven 3.9.9로 다음 관련 대상들을 실제 실행했다:

`mvn -B -ntp -Dtest=AssignmentActionsContractTest,SalesOpportunityContractTest,ProjectProgressContractTest,B5ImportTest,B7ImportTest,B7ContractTest,DeveloperImportTest,DeveloperWorkbookTest,DeveloperImportContractTest,ProjectImportTest,ProjectImportContractTest,*ValidationTest,*InputPolicyTest test`

환경의 Maven 기본 미러 DNS 실패를 피하기 위해 저장소 밖의 임시 Maven 설정에 접근 가능한 egress 프록시와 Maven Central을 설정했다.
최종 결과: 454개 시험, 실패 0, 오류 0, 건너뜀 0, BUILD SUCCESS.
실제 컨트롤러/서비스 경로의 응답·검증과 파일 파싱/양식을 검증했으며, 매퍼가 대체된 HTTP 시험을 실DB 검증으로 주장하지 않는다.

### Playwright와 실DB의 남은 확인

- 새 재사용 시나리오: `e2e/tests/integration-one-input-protection.spec.ts`.
- UI-33 취소 후 계속 작성, UI-29 즉시 숫자 정제 및 탭 표시의 두 시험을 추가했다.
- `npm --prefix e2e test -- tests/integration-one-input-protection.spec.ts` 실제 실행 시도:
  설정 단계에서 `SICOST_E2E_BASE_URL: runner가 시작한 독립 DB 앱 주소가 필요합니다.`로 중단됐다.
- 테스트 수집만 확인할 목적으로 루프백 BASE_URL을 지정해 `--list` 실행: 1개 파일, 2개 시험 수집 성공. 브라우저 실행 성공을 뜻하지 않는다.
- `docker info`도 Docker daemon 연결 실패다. 다른 운영 앱/DB로 대체하지 않았다.
- 남음: 격리 DB 앱과 역할별 시험 계정 제공 후 위 Playwright 실행, 실제 화면 예시 데이터 확인,
  변경 이력 사용자 이름 SQL 및 대표 조회의 PostgreSQL 실행 확인.

## 완료 점검의 검증 수준

공통 부품, 코드 표시 지정 화면, 금액/확률, 필수 표시/머리글, 대표 오류 응답, 업로드 경고,
숫자 정제, 위험 확인 창, 미저장 보호는 소스 및 관련 자동 시험으로 확인했다.
실제 앱을 연 모든 화면의 육안 확인과 실제 PostgreSQL 경로는 환경 때문에 미확인이다.
전체 프론트 단일 실행은 두 실패가 있었으며, 이 두 실패를 수정한 관련 재시험은 통과했다.
전체 실행 녹색 영수증 및 브라우저/DB 확인까지 끝났다는 완료 판정은 하지 않는다.

## 변경 파일 목록

현재 작업트리의 기존 시도 변경을 포함한다. 처음부터 존재한 미추적 AGENTS.md는 수정하지 않았으므로 아래에서 제외했다.
- backend/src/main/java/com/sicost/assignmentsops/AssignmentActionsInput.java
- backend/src/main/java/com/sicost/assignmentsops/AssignmentActionsService.java
- backend/src/main/java/com/sicost/b5/B5ImportDefinition.java
- backend/src/main/java/com/sicost/b7/B7ImportDefinition.java
- backend/src/main/java/com/sicost/b8/B8Controller.java
- backend/src/main/java/com/sicost/b8/B8ImportDefinition.java
- backend/src/main/java/com/sicost/b9/B9Controller.java
- backend/src/main/java/com/sicost/b9/B9ImportDefinition.java
- backend/src/main/java/com/sicost/common/validation/InputValidator.java
- backend/src/main/java/com/sicost/common/validation/PeopleInputPolicy.java
- backend/src/main/java/com/sicost/common/validation/ProjectInputPolicy.java
- backend/src/main/java/com/sicost/progress/ProjectProgressCalculation.java
- backend/src/main/java/com/sicost/progress/ProjectProgressInput.java
- backend/src/main/java/com/sicost/salesopportunities/SalesOpportunityInput.java
- backend/src/main/java/com/sicost/salesopportunitiesops/SalesCommandInput.java
- backend/src/main/resources/mappers/history/AssignmentHistoryMapper.xml
- backend/src/test/java/com/sicost/assignmentsops/AssignmentActionsContractTest.java
- backend/src/test/java/com/sicost/b5/B5ImportTest.java
- backend/src/test/java/com/sicost/b8/DeveloperImportContractTest.java
- backend/src/test/java/com/sicost/b8/DeveloperImportTest.java
- backend/src/test/java/com/sicost/b9/ProjectImportContractTest.java
- backend/src/test/java/com/sicost/b9/ProjectImportTest.java
- backend/src/test/java/com/sicost/progress/ProjectProgressContractTest.java
- backend/src/test/java/com/sicost/salesopportunities/SalesOpportunityContractTest.java
- docs/verification/integration-one-2026-11-19.md
- e2e/tests/integration-one-input-protection.spec.ts
- frontend/src/app/router.tsx
- frontend/src/pages/apiops/ChangeHistoryTable.tsx
- frontend/src/pages/apiops/PeopleChangeHistoryTable.test.tsx
- frontend/src/pages/apiops/PeopleChangeHistoryTable.tsx
- frontend/src/pages/apiops/ProjectStatusBoard.tsx
- frontend/src/pages/apiops/WithdrawalListScreen.test.tsx
- frontend/src/pages/apiops/WithdrawalNextPanel.tsx
- frontend/src/pages/apiops/WorkforceOperationsScreen.test.tsx
- frontend/src/pages/apiops/WorkforceOperationsScreen.tsx
- frontend/src/pages/assignments/StaffingAssignmentsScreen.test.tsx
- frontend/src/pages/assignments/StaffingAssignmentsScreen.tsx
- frontend/src/pages/assignmentsops/AssignmentCreateForm.tsx
- frontend/src/pages/assignmentsops/AssignmentDialogs.tsx
- frontend/src/pages/assignmentsops/AssignmentDrawer.tsx
- frontend/src/pages/assignmentsops/StaffingAssignmentsScreen.test.tsx
- frontend/src/pages/assignmentsops/StaffingAssignmentsScreen.tsx
- frontend/src/pages/candidates/CandidateForm.tsx
- frontend/src/pages/candidates/CandidateFormResumeLayout.test.tsx
- frontend/src/pages/candidates/CandidatesScreen.test.tsx
- frontend/src/pages/candidates/CandidatesScreen.tsx
- frontend/src/pages/candidates/WorkforceRecommendationCandidates.test.tsx
- frontend/src/pages/candidates/WorkforceRecommendationCandidates.tsx
- frontend/src/pages/candidates/recommendationPresentation.ts
- frontend/src/pages/clientsops/ClientSelect.tsx
- frontend/src/pages/developers/DeveloperDrawer.tsx
- frontend/src/pages/developers/DeveloperForm.tsx
- frontend/src/pages/developersops/DeveloperAssignmentsPanel.tsx
- frontend/src/pages/developersops/DeveloperStatusToolbar.tsx
- frontend/src/pages/forecast/SalesForecastScreen.test.tsx
- frontend/src/pages/forecast/SalesForecastScreen.tsx
- frontend/src/pages/plans/ProjectPlansPanel.test.tsx
- frontend/src/pages/plans/ProjectPlansPanel.tsx
- frontend/src/pages/progress/ProjectProgressEditor.tsx
- frontend/src/pages/progress/ProjectProgressPanel.test.tsx
- frontend/src/pages/projects/ProjectForm.tsx
- frontend/src/pages/projects/ProjectsScreen.test.tsx
- frontend/src/pages/projects/ProjectsScreen.tsx
- frontend/src/pages/projectsops/ProjectStatusPanel.test.tsx
- frontend/src/pages/projectsops/ProjectStatusPanel.tsx
- frontend/src/pages/projectstatusboardops/ProjectStatusSummary.tsx
- frontend/src/pages/receivablesops/ProjectDatesPreview.test.tsx
- frontend/src/pages/salesopportunities/SalesOpportunityForm.test.tsx
- frontend/src/pages/salesopportunities/SalesOpportunityForm.tsx
- frontend/src/pages/salesopportunitiesops/SalesCommands.test.tsx
- frontend/src/pages/salesopportunitiesops/SalesOpportunityActions.tsx
- frontend/src/pages/salesopportunitiesops/SalesPipelineModal.test.tsx
- frontend/src/pages/salesopportunitiesops/SalesPipelineScreen.tsx
- frontend/src/pages/salesopportunitiesops/SalesQuotePanel.tsx
- frontend/src/pages/workforceforecastsops/WorkforceDemandForecastScreen.test.tsx
- frontend/src/pages/workforceforecastsops/WorkforceDemandForecastScreen.tsx
- frontend/src/pages/workforcerecommendationsops/RecommendationBrowse.test.tsx
- frontend/src/pages/workforcerecommendationsops/RecommendationHistoryPanel.tsx
- frontend/src/pages/workforcerecommendationsops/RecommendationSavedValues.tsx
- frontend/src/pages/workforcerecommendationsops/WorkforceRecommendationsScreen.tsx
- frontend/src/shared/BusinessName.tsx
- frontend/src/shared/CodeSelect.tsx
- frontend/src/shared/Decimal.tsx
- frontend/src/shared/Dialog.tsx
- frontend/src/shared/FieldErrors.tsx
- frontend/src/shared/NumericInput.tsx
- frontend/src/shared/PresentationInput.test.tsx
- frontend/src/shared/SkillsInput.tsx
- frontend/src/shared/Text.tsx
- frontend/src/shared/UnsavedDialog.tsx
- frontend/src/shared/codeNames.tsx
- frontend/src/shared/common.css
- frontend/src/shared/format.ts
