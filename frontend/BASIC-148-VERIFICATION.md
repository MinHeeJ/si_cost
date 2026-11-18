# BASIC-148 UI 개선 적용·검증 기록

## 기준과 범위

- 작업 시작 HEAD: 5f2ecb2b426153eebdf143eb77a13da4a7c52294 (main).
- Jira 원문과 docs/reference/si-cost-ui-redesign-v22.html의 최종 CSS·화면을 기준으로 기존 React 등록부와 인증 경계를 유지했다.
- 전달된 Scope 전체 보고서의 Markdown/JSON 경로는 현재 실행 환경에 없었다. 저장소 검색에서도 BASIC-148 전용 Spec-Kit 변경명세를 찾지 못했다. 후보 목록 대신 실제 소스·호출부·인접 시험으로 범위를 조사했다.
- 백엔드, DB, 마이그레이션, API 클라이언트, README 및 초기 로그인 안내 파일은 변경하지 않았다. 계정 초기화·비밀번호 재설정·업무 데이터 저장·삭제·발송·확정·마감은 실행하지 않았다.
- 기존 AGENTS.md는 작업 시작부터 있던 untracked 파일이며 변경 산출물에서 제외했다. 커밋·푸시는 하지 않았다.

## 적용한 변경

1. 공통 셸: 64px/모바일 56px 헤더, 248px 메뉴와 84px 남색 접힘 레일, 폴더형 화면 탭, 검색, 서버 기준일, 바로가기, 여백·타이포·카드·표·필터·반응형 표현을 정리했다. 서버 메뉴와 pcOnly 판정, 기존 등록 슬롯·location state·열린 화면의 마운트와 초안은 유지했다.
2. 공통 표현: SectionCard/KpiStrip/KpiItem/DetailDescription, controlled SegmentedTabs, FormSection, HelpTooltip을 추가하고 실제 거래처·프로젝트·원가·대시보드 등에 연결했다. 필수 경고와 미확인 이유는 툴팁으로 숨기지 않았다.
3. 폼·팝업: 개발자 편집을 기존 Dialog controller의 전체화면 표현으로 확장했다. 거래처 섹션, 공통 FormPage 고정 액션, 권한 저장 바, 상세 640px·확인 400px·넓은 폼 720px·모바일 하단시트를 정리했다. 중첩창의 최상위 Escape/Tab, 초점 복원, 스크롤 잠금을 공통화했다.
4. 업무 화면: 거래처/파트 탭, 프로젝트 상세의 기본→진행→이슈→계획→투입→이력, 원가 상세의 월별→인력→시뮬레이션→원인 순서를 적용했다. 기존 key·test ID·dirty/저장/권한 controller는 유지했다. 대시보드 기준 띠·KPI·수급 차트와 재무·마감·AI·시스템 화면의 공통 표현도 정리했다.
5. 차트: ECharts 5.5.1을 npm 정확 버전으로 추가했다. 공통 SVG Host에서 선·막대·누적·폭포·비율 표현, ResizeObserver, 숨김 탭 재노출, dispose, reduced-motion, 기존 SVG 대체 표시를 지원한다. 대시보드·자금·사업계획·수익 구조·수요 예측·영업 예측에 연결했다. 원본 금액 문자열과 기존 포맷터를 보존하고, null 선 끊김·음수·0을 구별한다. 구성비는 서버 비중과 showBar 조건을 사용하며 재정규화하지 않는다.
6. 인증 표현: 기존 AuthenticationBoundary/LoginForm/MFA/강제변경/세션 처리를 유지하면서 분할 로그인 화면과 비밀번호 보기, 작은 세션 메뉴를 적용했다. 세션 소비자는 메뉴를 닫아도 마운트 상태를 유지한다. 입력의 기존 접근성 이름도 브라우저에서 확인했다.
7. 모션: 페이지·메뉴·팝업·숫자·로고·별·실제 pending/AI 단계의 CSS 모션과 실시간 reduced-motion 설정을 적용했다. 숫자를 임의 계산하거나 저장값을 변경하지 않는다.

## 실제 실행 결과

실행 위치는 각 package.json이 있는 frontend/ 또는 e2e/다.

| 확인 | 명령 / 대상 | 결과 |
| --- | --- | --- |
| 타입 | npm run typecheck | exit 0 |
| 전체 회귀 | npm run test -- --run --pool=forks --poolOptions.forks.maxForks=3 --poolOptions.forks.minForks=1 | 220개 파일, 2,758개 테스트 통과 |
| 빌드 | npm run build | TypeScript + Vite production build 성공 |
| 로그인 브라우저 | npm test -- tests/ui-login-layout.spec.ts | 1440×1000, 1280×900, 768×1000, 390×844에서 4개 통과 |
| 실제 차트 렌더러 | npm test -- tests/ui-chart-renderer.spec.ts | 실제 SVG 렌더·null/음수 입력·문자열 비HTML 처리·숨김 후 390px 재노출 resize 통과 |
| 브라우저 묶음 | npm test -- tests/ui-login-layout.spec.ts tests/ui-chart-renderer.spec.ts | 5개 통과, 최종 CSS 뒤 차트 1개 재확인도 통과 |
| 인증 업무 E2E 준비 | npm test -- tests/ui-redesign.spec.ts --list | 6개 시험 수집 성공, 실행하지 않음 |
| 정적 검사 | git diff --check + 신규 소스 trailing whitespace 검사 | 오류 없음 |

브라우저는 npm run dev -- --port 19148 --strictPort로 시작한 실제 프런트엔드를 대상으로 실행했다. PLAYWRIGHT_BROWSERS_PATH=/opt/ms-playwright의 설치된 Chromium을 사용했다. 참고 시안도 실제 브라우저로 열어 헤더 64px·메뉴 248px과 최종 폴더 탭을 확인했다.

기존 업무/권한/인증 회귀 단언은 삭제하거나 약화하지 않았다. 대시보드 시험의 프레임 대역은 새 periodContext 슬롯을 렌더하도록 갱신했고 기존 날짜·다운로드 단언은 유지했다.

실행 환경 메모:
- Node 22.21.1/npm 10.9.4로 실행되어 저장소의 Node 20 engines 경고가 있었다.
- 지정 maxForks만으로는 환경의 기본 최소 worker와 충돌하여 minForks=1을 추가했다. singleFork는 쓰지 않았다.
- 첫 전체 실행의 ENOSPC 및 실행 도중 소스 변경으로 인한 이전 모듈 캐시 실패는 최종 소스 고정 후 재실행하여 해소했다.
- Vite의 기본 500kB 번들 크기 경고는 남아 있으며 빌드는 성공했다.

## 브라우저 증거와 검증 한계

저장한 캡처:

- evidence/basic148/login-1440.png
- evidence/basic148/login-1280.png
- evidence/basic148/login-768.png
- evidence/basic148/login-390.png

로컬 개발 서버에는 실 백엔드가 연결되어 있지 않아 로그인 캡처에는 연결 오류 안내가 있다. 캡처와 시험은 로그인 배치·접근성 이름·비밀번호 보기·비제출·가로 넘침을 확인한 것이며 실제 로그인 성공 증거가 아니다. 차트 시험은 실제 컴포넌트와 렌더러를 격리한 표현 시험으로, 실 API의 재무값 검증을 대신하지 않는다.

SICOST_E2E_BASE_URL, BASIC147_E2E_DISPOSABLE, BASIC147_E2E_ACCOUNTS_JSON, BASIC147_MAIL_COLLECTOR가 제공되지 않았다. 실제 인증을 요구하는 ui-redesign.spec.ts는 기존 authenticatedTest/loginUi와 ADMIN MFA 절차를 그대로 사용하며, 서버 메뉴 30개·4개 해상도·초안/중첩창·단축키/reduced-motion을 검증하도록 남겼다. 임의 인증 우회나 합성 API 성공 응답을 제품 코드에 넣지 않았다.

따라서 30개 화면과 모든 하위 탭·상세·폼·팝업의 실 로그인 후 시안 비교, 역할별 실 API 검증, DB 전후 CRUD/일괄/업로드/입금/확정/마감 검증은 미실행이다. 이 기록은 해당 전체 수용 조건이 완료됐다는 판정이 아니다. 나머지 업무 화면은 기존 controller와 등록부를 유지하면서 공통 표현층을 적용한 범위이며, 개별 상태의 시각적 동일성을 주장하지 않는다.

## 변경 파일 전체 목록 (61개)

- e2e/package-lock.json
- e2e/tests/ui-chart-renderer.spec.ts
- e2e/tests/ui-login-layout.spec.ts
- e2e/tests/ui-redesign.spec.ts
- frontend/BASIC-148-VERIFICATION.md
- frontend/evidence/basic148/login-1280.png
- frontend/evidence/basic148/login-1440.png
- frontend/evidence/basic148/login-390.png
- frontend/evidence/basic148/login-768.png
- frontend/package-lock.json
- frontend/package.json
- frontend/src/pages/apiops/ProjectAnalysisParts.tsx
- frontend/src/pages/authentication/AuthenticationBoundary.tsx
- frontend/src/pages/authentication/LoginForm.tsx
- frontend/src/pages/authentication/authentication.css
- frontend/src/pages/businessplansops/BusinessPlanRevenueChart.tsx
- frontend/src/pages/cashflowops/BalanceTrendChart.tsx
- frontend/src/pages/clients/ClientDrawer.tsx
- frontend/src/pages/clients/ClientForm.tsx
- frontend/src/pages/clients/ClientsScreen.tsx
- frontend/src/pages/costanalysis/ProjectCostDetailPanel.tsx
- frontend/src/pages/dashboardops/DashboardScreen.test.tsx
- frontend/src/pages/dashboardops/DashboardScreen.tsx
- frontend/src/pages/dashboardops/DashboardTrendChart.tsx
- frontend/src/pages/developers/DeveloperForm.tsx
- frontend/src/pages/forecast/SalesForecastScreen.tsx
- frontend/src/pages/projects/ProjectDetailPanel.tsx
- frontend/src/pages/roles/PermissionsScreen.tsx
- frontend/src/pages/roles/roles.css
- frontend/src/pages/shared/FollowUpScreen.tsx
- frontend/src/pages/shared/HeaderActions.test.tsx
- frontend/src/pages/shared/HeaderActions.tsx
- frontend/src/pages/shared/SharedApplication.tsx
- frontend/src/pages/shared/WorkspaceUx.test.tsx
- frontend/src/pages/shared/analysis.css
- frontend/src/pages/shared/financialDisplay.css
- frontend/src/pages/shared/shared.css
- frontend/src/pages/structure/ProfitStructureCharts.tsx
- frontend/src/pages/structure/ProfitStructureScreen.tsx
- frontend/src/pages/workforceforecastsops/WorkforceDemandForecastScreen.tsx
- frontend/src/shared/BrandMark.tsx
- frontend/src/shared/CodeSelect.tsx
- frontend/src/shared/DetailDrawer.tsx
- frontend/src/shared/Dialog.tsx
- frontend/src/shared/FormPage.tsx
- frontend/src/shared/FormSection.tsx
- frontend/src/shared/HelpTooltip.tsx
- frontend/src/shared/Presentation.test.tsx
- frontend/src/shared/SegmentedTabs.tsx
- frontend/src/shared/Surface.tsx
- frontend/src/shared/Text.tsx
- frontend/src/shared/charts/ChartHost.test.tsx
- frontend/src/shared/charts/ChartHost.tsx
- frontend/src/shared/charts/chartAdapters.test.ts
- frontend/src/shared/charts/chartAdapters.ts
- frontend/src/shared/charts/chartTheme.ts
- frontend/src/shared/common.css
- frontend/src/shared/motion.css
- frontend/src/shared/surfaces.css
- frontend/src/shared/useModalLayer.ts
- frontend/src/shared/useReducedMotion.ts
