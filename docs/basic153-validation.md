# BASIC-153 수정 및 브라우저 검증 결과

## 판정

요청한 세 기능의 코드 수정과 재사용 회귀 테스트를 작성했다. 실행 가능한 계정으로 실제 Chromium 인쇄 미리보기·모바일 직접 진입·업무 이동·표시 문구를 확인했다. 아래의 별도 권한 계정 및 일부 데이터 조건은 실제 브라우저에서 미검증이므로 전체 합격 완료로 판정하지 않는다.

## 변경 경계

- UI-16은 기존 API 응답과 화면 DOM을 그대로 인쇄한다. 인쇄용 재조회·금액 계산·별도 카드 데이터 복사본을 만들지 않았다.
- 버튼형 카드·업무 목록의 본문만 인쇄에 남기고 입력·작업 버튼·메뉴를 숨긴다. 뒤에 로드되는 테마의 viewport 높이/스크롤 레이아웃 때문에 제목만 출력되는 문제도 대시보드 인쇄 범위에서 수정했다. 카드 묶음과 각 카드의 페이지 분할을 방지한다.
- 미수금은 응답의 기준일, 나머지 지표는 해당 응답의 기준월을 사용한다. 조회월·단위·잠정/마감 안내도 함께 출력한다. 인쇄 진입/복귀 포커스로 인한 재조회를 억제한다.
- 인증 경계는 기존 조회 권한과 기기에서 메뉴를 숨기는 판단을 분리한다. 현재 메뉴 API의 pcOnly 설정을 사용하는 공통 셸에서 화면명이 포함된 안내를 렌더링하며 요청한 PC 화면 본문을 마운트하지 않는다.
- 안내는 클릭 전까지 유지된다. 대시보드를 사용할 수 있으면 UI-16으로, 그렇지 않으면 인증 경계의 기존 첫 화면 선택 흐름으로 이동한다. 모바일 탐색·즐겨찾기·최근의 PC 전용 숨김과 768px 이상 진입을 보존한다.
- 원천명과 진단 번호는 표시하지 않는다. 대시보드의 공통 기간 조회/저장·메뉴 진단·선호 설정 오류도 포함하며, 다른 화면의 기존 오류 표시는 변경하지 않는다. 중복 기준월 줄을 없애고 필요한 기준월은 위젯의 실제 응답에서 읽는다. 점검 코드·계약 구분·회사 전체 집계 명칭을 사용자용 문구로 바꾸며 미등록 코드를 그대로 표시하지 않는다.
- 시스템 설명과 등록된 프로젝트·기회·파트 이름을 분리한다. 영문·슬래시가 포함된 업무 이름은 변경하지 않는다. 자금 미확인 설명에 포함되는 금액/급여 필드 이름도 사용자용 명칭으로 표시한다.
- 백엔드·API 응답·계산·권한 설정·DB 마이그레이션·로그인 안내 파일은 변경하지 않았다. 커밋/푸시하지 않았다.

## 실행 환경과 방식

- 이번 worktree의 `npm run build` 산출물을 로컬 검증용 프록시로 서비스하고 제공된 실제 검증 API에 연결했다. 로그인·세션·capabilities·메뉴·업무 API를 대체하지 않았다.
- 검증용 주소: `http://127.0.0.1:5175`. 기존 Vite 프록시의 요청 출처 변경으로 실제 로그인에서 ORIGIN_FORBIDDEN을 확인하여, 저장소 설정 대신 세션 외부의 검증 프록시에만 원래 Host/Origin을 유지하도록 설정했다.
- Playwright Chromium 134.0.6998.35. PDF 생성/텍스트 검증은 headless Chromium과 Python pypdf를 사용했다. 실제 `chrome://print/` 미리보기는 가상 디스플레이의 headful Chromium으로 별도 실행했다. CSS print 모드 검사를 미리보기 조작으로 대체 보고하지 않았다.
- 계정·비밀번호·OTP·쿠키·토큰을 결과 파일에 저장하지 않았다. 브라우저 trace/video/자동 실패 스크린샷은 껐으며, 증거 이미지는 로그인 이후의 인쇄/업무 영역만 캡처했다.
- 브라우저 조작은 기존 기간/최근 화면 선호 설정을 저장할 수 있다. 계정 생성·권한 변경·업무 데이터 시딩은 하지 않았다.

## 실제 브라우저 조작 결과

| 대상 | 폭 / 조회월 | 실제 결과 | 판정 |
|---|---|---|---|
| UI-16 실제 인쇄 미리보기 | 1440px / 2026-11 | window.print로 chrome://print 진입, previewState=display-preview 확인. 매출·영업이익·누계 영업이익·가동률·미수금 5개가 첫 페이지에서 잘리지 않고 보임. 단위·기준월·미수 기준일·잠정 안내 표시. 취소 후 카드 내용 동일 | 통과 |
| UI-16 실제 인쇄 미리보기 | 1440px / 2026-01 | 월 선택 후 같은 조작 반복. 과거월 기준과 오늘 기준 미수금 구분 유지. 취소 후 카드 내용 동일 | 통과 |
| UI-16 PDF 출력물 | 1440px / 최종 실행 2026-02, 2026-01 | 각 월의 카드 DOM 내용과 인쇄 내용 일치. PDF를 실제 파싱하여 같은 페이지에 5개 제목과 표시값·조회월·기준일이 존재함을 확인. 인쇄 구간 대시보드 추가 조회 0회. 작업 버튼·입력·사이드바 숨김 | 통과 |
| UI-23 사용자 직접 주소 | 390px | 데스크톱 접근 가능 계정으로 직접 방문. 「사용자」 안내와 대시보드로 버튼 표시, 요청 본문 없음. 안내 유지 후 클릭하여 UI-16 로딩 완료 | 통과 |
| UI-23 사용자 직접 주소 | 767px | 동일하게 안내 유지, 요청 본문 없음, 클릭 후 UI-16 | 통과 |
| UI-23 사용자 직접 주소 | 768px | 안내 없이 사용자 화면 프레임 정상 표시 | 통과 |
| UI-27 공통코드 직접 주소 | 390px | 데스크톱 접근 가능 계정으로 직접 방문. 「공통코드」 안내와 대시보드로 버튼 표시, 요청 본문 없음. 클릭 후 UI-16 로딩 완료 | 통과 |
| UI-27 공통코드 직접 주소 | 767px | 동일하게 안내 유지, 요청 본문 없음, 클릭 후 UI-16 | 통과 |
| UI-27 공통코드 직접 주소 | 768px | 안내 없이 공통코드 화면 프레임 정상 표시 | 통과 |
| 모바일 미인증 직접 주소 | 390px / UI-27 | 기존 실제 로그인 화면 표시, PC 안내 없음 | 통과 |
| UI-16 전체 스크롤·표시 문구 | 1440px / 2026-11, 2026-01 | 확인할 일·마감·경보·추이·인력·파트·파이프라인·자금 영역까지 스크롤/캡처. 현재 응답의 사용자용 표시와 도움말에서 원천명·불필요한 구분점 없음. 미확인·자료 없음 안내 유지 | 관찰한 조건 통과 |
| UI-16 차트 툴팁 | 1440px / 2026-11, 2026-01 | 실제 마우스 이동으로 툴팁 표시. 매출·매출원가·회사 전체 매출목표와 백만원 단위가 보이며 ALL 노출 없음 | 통과 |
| UI-16 업무 이동 | 1440px | 요약 카드 5개 각각 API의 기존 대상 UI-08/UI-18/UI-28/UI-13으로 이동하고 실제 화면 프레임 표시. 월 마감 UI-14, 파이프라인 UI-29도 클릭·화면 표시 확인 | 통과 |
| 실제 조회 권한 없는 별도 계정 | 390px / UI-23, UI-27 | 해당 계정 환경변수 미제공. 기존 계정 권한을 임의 변경하지 않음 | 미검증 |
| PC 화면 권한은 있으나 대시보드 권한 없는 별도 계정 | 390px | 해당 계정 환경변수 미제공 | 미검증 |

## 자동 검증 결과

최종 프런트 회귀 명령:

    cd frontend
    npm test -- --run src/pages/authentication src/pages/dashboardops src/pages/menumanagement/MenuNavigation.test.tsx src/pages/shared/SharedScreens.test.tsx src/pages/shared/WorkspaceUx.test.tsx src/pages/me/usePreferences.test.tsx --maxWorkers=4 --minWorkers=1

결과: 8개 파일, 179개 테스트 통과. 새 인증 경계 통합 테스트는 실제 AuthenticationBoundary와 SharedApplication을 함께 렌더링한다. 직접 모바일 진입의 본문 미마운트와, 데스크톱 편집 화면을 좁혔을 때 기존 DOM/입력 상태를 보존하며 숨기는 동작을 구분해 검증했다. API 응답 fixture를 사용하는 이 테스트는 실제 별도 계정 브라우저 검증의 대체 근거는 아니다. 401 회귀 테스트는 세션 경계와 SessionIndicator의 초기 조회 두 건이 완료된 후 기준 호출수를 잡도록 하여, 초기 effect 실행 순서에 따른 간헐 실패를 제거했다. 기존 401 이후 추가 조회 금지 단언은 유지했다.

    cd frontend
    npm run build

결과: TypeScript 검사와 프로덕션 빌드 통과. 기존 React Router/act 경고와 번들 크기·동적 import 경고는 남아 있다.

최종 실제 API 브라우저 명령:

    cd e2e
    SICOST_E2E_BASE_URL=http://127.0.0.1:5175 npm test -- tests/dashboard-presentation.spec.ts

결과: 9개 통과, 별도 권한 계정이 필요한 2개 건너뜀, 실패 0개. 계정값은 상속 환경에서만 읽는다. 필요한 계정 환경변수 이름은 SICOST_VERIFY_USER/PASSWORD, SICOST_RESTRICTED_VERIFY_USER/PASSWORD, SICOST_NO_DASHBOARD_VERIFY_USER/PASSWORD이다. 각 계정은 추가 인증/초기 비밀번호 변경을 완료한 상태여야 한다. PDF 텍스트 검증에는 Python pypdf가 필요하며, 파서가 없으면 PDF 생성만으로 통과시키지 않는다.

`git diff --check`도 통과했다. 백엔드 코드를 변경하지 않았으므로 백엔드 빌드/DB 마이그레이션 회귀는 이번에 실행하지 않았다.

## 증거 경로

루트 기준 `e2e/test-results/basic153-browser-evidence/`:

- print-preview-2026-11.png
- print-preview-2026-01.png
- users-mobile-390.png
- common-codes-mobile-390.png
- chart-tooltip-2026-11.png
- chart-tooltip-2026-01.png

실제 PDF는 `e2e/test-results/`의 dashboard-presentation 인쇄 테스트 출력 디렉터리에 있다. 이 파일들은 로컬 검증 산출물로 Git에서 제외되며 다음 Playwright 실행으로 정리될 수 있다. headful 미리보기 캡처는 CSS 인쇄 미리보기가 아니라 Chromium의 실제 인쇄 UI 캡처다.

## 남은 검증과 제한

1. 조회 권한 없는 별도 계정의 실제 차단 동작과 대시보드 권한 없는 별도 계정의 실제 첫 화면 이동: 계정이 없어 미검증. 두 Playwright 시나리오를 남겼으며 건너뜀은 통과로 집계하지 않았다.
2. 현재 검증 데이터에는 일부 프로젝트 경보·파트 실적·진행 기회·남은 마감 점검이 없다. BILL/SETL/BILL_MISS의 양수 건수 배지, 비어 있지 않은 프로젝트/기회명, 다양한 비영(非零) 금액, 마감 확정 상태는 실제 브라우저에서 확인하지 못했다. 금액과 데이터를 조작해 결과를 꾸미지 않았다.
3. 서버 조회 오류/부분 원천 실패 조건을 실제 서비스에 강제로 만들지 않았다. 이 조건의 진단 식별자 제거·자료 없음·미확인·미등록 코드·업무 이름 보존은 회귀 테스트로 검증했지만 해당 조건의 실제 브라우저 완료 근거로 보지 않는다.
4. 물리 프린터 출력은 실행하지 않았다. 실제 Chromium 미리보기와 PDF의 페이지·본문을 검증했다.

## 변경 파일

- frontend/src/pages/authentication/AuthenticationBoundary.tsx
- frontend/src/pages/authentication/AuthenticationBoundary.test.tsx
- frontend/src/pages/authentication/PcOnlyAccess.test.tsx
- frontend/src/pages/shared/SharedApplication.tsx
- frontend/src/pages/shared/ScreenPeriodBar.tsx
- frontend/src/pages/shared/SharedScreens.test.tsx
- frontend/src/pages/menumanagement/MenuNavigation.test.tsx
- frontend/src/pages/dashboardops/DashboardScreen.tsx
- frontend/src/pages/dashboardops/DashboardScreen.test.tsx
- frontend/src/pages/dashboardops/DashboardTrendChart.tsx
- frontend/src/pages/dashboardops/dashboardPresentation.ts
- frontend/src/pages/dashboardops/dashboard.css
- e2e/tests/dashboard-presentation.spec.ts
- docs/basic153-validation.md

시작 시 이미 미추적이던 AGENTS.md는 변경하지 않았다.

## APP_BOOT_NOT_READY 재작업 검증

### 원인과 수정 경계

- 원본 Quality 결과의 compose build는 PASS, compose up은 FAIL이었다. 원본의 `security_enabled=false`이므로 보안 검사 통과 근거는 없다. build unit 두 개와 프런트 시험 실행 계획 여섯 개도 원본 JSON에서 확인했다. 진단 패킷의 전달 수치 차이를 해소하려고 참조 전용 품질 산출물이나 실행기 코드를 수정하지 않았다.
- 실제 Docker에서 자동 대역을 사용하는 새 네트워크 생성이 `all predefined address pools have been fully subnetted`로 실패했다. 앱 컨테이너 실행 전의 공유 Docker 주소 풀 소진이며, health 연산이나 DB 시드의 오류라는 근거는 없었다.
- `infra/docker-compose.yml`의 기본 네트워크에 `10.203.0.0/24`를 명시하고 `COMPOSE_SUBNET` 재정의를 제공했다. 동시 배포 및 호스트/VPN 경로와 충돌하는 환경에서는 서로 다른 미사용 사설 대역을 지정한다. 다른 프로젝트의 네트워크 삭제나 데몬 설정 변경은 하지 않았다.
- `infra/nginx.conf`의 `/api/` 프록시를 명시적인 `http://backend:8080/api/`로 맞췄다. 기존 상대 API 호출, 비루트 앱 사용자, 8080 포트, pid/캐시 권한, DB 내부 통신 및 host port 비공개를 유지했다. Dockerfile, SQL, 인증 구현, 사용자 및 권한 초기화 코드는 변경하지 않았다.
- 기존 Python 보조 검사의 1 MiB·무인증 기대를 현행 Quality 확인기와 기존 JUnit의 8 MiB·인증 필수 계약에 맞췄다. OpenAPI fixture의 값·연산·설명·보안 선언은 수정하지 않았다. 작은 읽기 한도의 압축 및 초과 거절 단위 시험도 유지했다.
- 인쇄 브라우저 시험은 실제 PDF 생성·텍스트·동일 페이지·표시값 단언을 그대로 실행하되, PDF 경로 지정과 보고서 첨부를 제거해 메모리에서만 검증한다. 계정·비밀번호·본문·쿠키·토큰·원시 시험 출력은 이 재작업 보고에 보존하지 않는다. 브라우저 trace/video/스크린샷 비활성화를 유지했다.

### 실제 재실행 결과

- `python3 -m unittest discover -s scripts -p 'test_*.py'`: 9개 통과. 새 Compose 회귀 두 개는 수정 전 실패, 수정 후 통과했다. 기본 명시 대역 및 별도 배포용 대역 재정의, DB 포트 비공개, 앱 build/healthcheck 유지를 검사한다.
- backend/frontend 각각 `docker build --target build` 및 최종 runtime 이미지 빌드 통과. Compose build도 통과했다. 테스트는 Dockerfile 안이 아니라 build 이미지에서 별도로 실행했다. 최종 nginx 이미지에 앱 src/tests 디렉터리가 없음을 확인했다.
- backend build 이미지의 `mvn -B -ntp test`: 전체 6,354개, 실패 0, 오류 0, 건너뜀 747개. DB 환경을 주입하지 않았다. 첫 network-none 시도는 종료코드 1이며 시험 합계를 얻지 못했다. 일반 Docker 네트워크로 의존성 다운로드를 허용한 재실행의 결과만 이 합계로 사용했다. 실제 DB 통합 시험의 건너뜀을 통과로 집계하지 않는다.
- frontend build 이미지에서 모든 Vitest 시험을 실행한 첫 결과는 2,851개 중 2,849개 통과, 2개 타임아웃이었다. 동시 Maven/build 작업 종료 후 실패 경로 네 개를 분리 진단했을 때 모두 통과했다. 분리 진단의 타임아웃 변경을 기본 전체 시험 통과 근거로 사용하지 않는다.
- `docker compose -p qa-si-cost-26-scope-analysis-20261119t085413 -f infra/docker-compose.yml up -d --wait --wait-timeout 240 --no-build`: 종료코드 0. 실제 기본 네트워크는 명시한 대역으로 생성되고 database/backend/frontend 모두 healthy였다. backend USER=sicost, frontend USER=nginx를 확인했다.
- Quality의 `check_boot_ready` 판정 로직으로 실제 컨테이너 상태와 `/api/health`를 연속 관찰했다. 6회 HTTP 200 및 meta/success 단언 충족, 약 19초 관찰로 boot PASS였다. nginx 경유 `/`, `/UI-27`, `/api/health`는 200이고 인증 없는 `/api/code-groups`는 401이었다. `nginx -t`도 통과했다.
- 최초 새 DB Compose 앱의 브라우저 회귀는 1개 통과, 8개 실패, 2개 건너뜀이었다. 제공된 검증 계정을 임의로 새 DB에 만들거나 비밀번호를 재설정하지 않았다. 이후 현재 Docker 프런트 산출물을 로그 비활성 로컬 검증 프록시로 제공하고, 상속 환경의 실제 검증 API에 연결해 `tests/dashboard-presentation.spec.ts`를 재실행했다. 실제 로그인·세션·메뉴·업무 API는 대체하지 않았으며 9개 통과, 실패 0, flaky 0, 별도 권한 계정 미제공 2개 건너뜀이었다. 이 결과는 새 DB 자동 시드의 성공 근거가 아니다.
- 실제 PDF 두 조회월 검증 후 `e2e/test-results/`에는 `.last-run.json`만 남아 있고 PDF·본문 첨부·trace·스크린샷은 없다. 검증용 프록시는 종료했다. 이 작업의 Compose 컨테이너와 네트워크도 `down`으로 제거하고 정확한 프로젝트를 재조회해 제거를 확인했다. DB 볼륨은 보존했다.

### 남은 자동 앱 사용 계약 및 보안 판정

- 기동 실패는 재현 후 해소했지만 전체 Quality PASS 또는 REWORK 해소 완료로 판정하지 않는다. 현행 `parse_runtime_contract`로 루트 runtime-check를 재검사하면 `APP_RUNTIME_CONTRACT_INVALID`다. 인증 필수 표시는 있으나 `auth.login`, `auth.test_role`, `auth.credential_ref`, `auth.seed.initializer_path`, `identity`의 실행 선언이 없다. 기존 관리자 복구 CLI/일회성 SQL 시드는 확인기가 요구하는 기동 시 검증 계정 초기화가 아니므로 이를 임의 지정하지 않았다.
- 자동 로그인·identity·첫 메뉴 전체 게이트를 통과시키려면 기존 MFA·임시 비밀번호 변경·실제 권한을 우회하지 않는 승인된 검증 계정 공급 및 초기화 정책이 필요하다. 인증을 false로 바꾸거나 임시 ADMIN을 추가하지 않았다. runtime-check, 백엔드 durable runtime fixture, JUnit의 기존 인증 경계도 그대로 보존했다.
- 원본 Quality 보안 검사는 꺼져 있었지만 추가로 실제 Semgrep 자체 Phase A 규칙, Trivy 및 npm audit를 실행했다. Semgrep은 변경 코드·설정 17개 파일에서 finding 0개였다. 제한 시간을 늘린 재실행에서 timeout 0개였으나 부분 파싱 경고 2개가 남아 완전한 스캔 통과로 판정하지 않는다.
- Trivy fs(vulnerability/misconfiguration)는 High 3, Medium 7, Low 2를 보고했다. High 대상은 backend/pom.xml 및 frontend/package-lock.json이다. npm audit는 Critical 2, High 4, Moderate 4, 전체 10개 취약 의존성 항목을 보고했다. backend POM과 프런트 package/lockfile은 HEAD와 동일함을 확인했다. 이번 기동 수정에서 의존성·보안 정책을 우회하거나 취약점을 통과로 바꾸지 않았으며, 별도 의존성 보안 갱신이 필요하다.

### 이번 재작업에서 수정한 파일

- README.md
- docs/basic153-validation.md
- e2e/tests/dashboard-presentation.spec.ts
- infra/docker-compose.yml
- infra/nginx.conf
- scripts/compact_openapi_fixture.py
- scripts/test_compact_openapi_fixture.py
- scripts/test_compose_contract.py

기존 staged/unstaged 프런트 변경은 보존했다. 커밋·푸시하지 않았다.
