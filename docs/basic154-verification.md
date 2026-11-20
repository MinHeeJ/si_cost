# BASIC-154 변경 및 검증 결과

## 판정

요청한 인쇄·모바일 안내·대시보드 표시 코드를 수정했다. 실제 백엔드 인증과 DB 조회를 사용하는 Chromium 검증을 수행했다. 아래 미검증 항목이 남아 있으므로 원문의 모든 인수 조건을 완료했다고 판정하지 않는다.

계정·비밀번호·세션 쿠키·토큰 값은 이 문서나 검증 산출물에 기록하지 않았다. 초기 로그인 안내 파일은 변경하지 않았다.

## 변경 내용과 보존 경계

- `DashboardScreen.tsx:68`: 공통 기준 표시에서 `sourceService`와 그 툴팁을 제거했다. 각 위젯의 실제 `basisMonth` 또는 `basisDate`를 사용한다. 마감 진행·영업 파트·자금은 중복 보조 줄을 생략하되 실패·미확인·자료 없음과 사유는 보존한다.
- `DashboardScreen.tsx:332`: 현재 응답의 조회월·기준일·금액 단위·잠정/마감 상태를 인쇄용 문구로 제공한다. 미수금은 해당 카드의 실제 기준일을 표시한다. 카드 본문과 보조 값은 화면과 같은 DOM과 API 응답을 사용한다.
- `dashboard.css:295`: 데이터 카드 버튼만 공통 버튼 숨김의 예외로 둔다. 메뉴·작업 버튼·입력 컨트롤은 인쇄에서 숨긴다. 카드 및 카드 묶음의 페이지 분할을 방지한다. 인쇄 시 앱의 스크롤 영역·컨테이너 크기 제한·진입 애니메이션을 해제한다.
- `DashboardScreen.tsx:139`: 인쇄 중 및 인쇄 종료의 포커스 복귀로 대시보드를 재조회하지 않는다. 기존 일반 포커스 갱신 경로는 유지한다.
- `dashboardPresentation.ts`: 마감 점검과 계약 구분의 사용자 명칭을 제공하고, 알려지지 않은 코드는 사용자용 대체 문구로 표시한다. 내부 진단 문구를 이유·오류 필드에서 처리한다. 프로젝트·기회·파트명에는 이 처리를 적용하지 않는다.
- `DashboardTrendChart.tsx`: 회사 전체 매출목표의 설명·범례·툴팁·접근성 문구에서 `ALL`을 사용자 명칭으로 바꿨다.
- `AuthenticationBoundary.tsx:168`: 메뉴 조회 허용 여부와 기기별 첫 화면 선택을 분리했다. 조회가 허용된 PC 전용 직접주소는 공통 메뉴 안내로 전달한다. 실제 무권한·미인증 흐름은 유지한다.
- `SharedApplication.tsx:147,851`: 현재 메뉴의 `pcOnly`와 실제 메뉴명을 이용해 767px 이하에서 이름이 포함된 안내와 `대시보드로` 버튼을 표시한다. 버튼을 누르기 전에는 이동하지 않는다. 대시보드 권한이 없으면 기존 기기에서 이용 가능한 첫 화면 선택 순서를 따른다.
- 모바일 메뉴·즐겨찾기·최근의 PC 전용 숨김은 유지한다. 모바일 직접 방문에서는 해당 업무 본문을 새로 마운트하지 않는다. 이미 데스크톱에서 열린 탭은 폭 변경 시 숨겨서 기존 입력 상태를 보존한다.
- 계산·점검 코드·API 응답 구조·백엔드 권한·DB 스키마·카드 이동 계약은 변경하지 않았다.

## 실제 브라우저 검증 환경

수정한 프런트엔드의 `npm run build` 산출물과 기존 검증 백엔드를 연결했다. 검증 프로세스 안에서만 동작하는 정적 파일/API 프록시를 사용했고, 검증 종료 후 종료했다. 개발 서버나 watcher를 남기지 않았다.

로그인은 실제 서버의 CSRF 및 로그인 API로 세션을 발급받았다. 이후 앱의 실제 `AuthenticationBoundary`가 세션·capabilities·메뉴를 조회하도록 했다. 메뉴나 권한을 브라우저에 주입하거나 API 응답을 대체하지 않았다. 업무 값은 실제 DB/API 결과를 사용했다.

검증 계정은 UI-16·UI-23·UI-27 및 카드 이동 대상의 조회 권한이 있는 계정이다. 계정값은 생략한다.

## 항목별 실제 결과

| 접속 화면 | 폭 / 조회월 | 실제 조작 및 관찰 | 판정 |
|---|---|---|---|
| UI-16 | 1440px / 2026-02 | 전체 위젯을 순서대로 스크롤하고 기준 도움말과 매출·원가/인력 수급 차트 툴팁을 열었다. 내부 원천명·API 경로·집계 코드 노출 검사 통과 | 통과 |
| UI-16 | 1440px / 2026-11 | 실제 기간 선택 컨트롤로 조회월을 변경하고 같은 표시·툴팁 검사를 반복했다 | 통과 |
| UI-16 인쇄 PDF | 2026-02 / 천원 / 기준일 2026-11-20 / 잠정 | Chromium 인쇄 경로로 A4 PDF 생성. 4페이지 중 1페이지에 요약 카드 5개의 제목·표시값·보조 지표·각 기준이 모두 존재하고 카드가 분할되지 않음을 검사 | PDF 통과, 네이티브 미리보기 미검증 |
| UI-16 인쇄 PDF | 2026-11 / 천원 / 기준일 2026-11-20 / 잠정 | 같은 검사를 반복. 선택월과 무관하게 미수금 기준일은 해당 응답 기준을 유지. 인쇄 중 대시보드 추가 조회 0회 | PDF 통과, 네이티브 미리보기 미검증 |
| UI-23 사용자 | 1440px | 직접주소에서 실제 사용자 화면 본문 표시 | 통과 |
| UI-23 사용자 | 390px | 직접주소에서 「사용자」 안내·버튼 표시, 요청 본문 없음, 주소 유지. 버튼을 누른 후 UI-16 이동 | 통과 |
| UI-23 사용자 | 767px | 안내 유지 및 버튼 클릭 후 UI-16 이동 | 통과 |
| UI-23 사용자 | 768px | 직접주소에서 실제 사용자 화면 본문 표시, 모바일 안내 없음 | 통과 |
| UI-27 공통코드 | 1440px | 직접주소에서 실제 공통코드 화면 본문 표시 | 통과 |
| UI-27 공통코드 | 390px | 직접주소에서 「공통코드」 안내·버튼 표시, 요청 본문 없음, 주소 유지. 버튼을 누른 후 UI-16 이동 | 통과 |
| UI-27 공통코드 | 767px | 안내 유지 및 버튼 클릭 후 UI-16 이동 | 통과 |
| UI-27 공통코드 | 768px | 직접주소에서 실제 공통코드 화면 본문 표시, 모바일 안내 없음 | 통과 |
| UI-16 카드 이동 | 1440px | 5개 카드를 각각 클릭해 서버가 제공한 업무 대상 URL과 화면 제목을 확인. 월 마감·파이프라인 업무 이동 버튼도 확인 | 통과 |
| UI-27 미인증 직접주소 | 390px | 기존 로그인 화면 표시, PC 전용 안내 없음 | 통과 |
| UI-23·UI-27 실제 무권한 계정 | 390px | 별도 무권한 검증 계정 환경변수가 없어 실행하지 못함 | 미검증 |

PDF는 DOM의 인쇄 스타일 가시성뿐 아니라 실제 출력 파일의 텍스트를 PDF.js로 추출해 화면의 제목·값·보조 지표·기준과 대조한다. 카드 5개의 모든 내용이 같은 PDF 페이지에 있어야 테스트가 통과한다. pypdf를 이용한 별도 검사와 PDF 1페이지의 실제 이미지 렌더링·시각 확인도 수행했다.

이번 실제 응답의 매출·이익·미수금 0과 가동률 미확인은 그대로 유지했다. 미확인을 0으로 바꾸거나 검증용 금액을 만들어 넣지 않았다. 조회월을 원래 값으로 복구하고 `/api/me/preferences`에서 UI-16 기간 저장값을 다시 확인한다.

초기 검증에서는 인쇄 CSS의 카드 가시성 검사만 통과하고 PDF 본문은 잘리는 문제가 발견됐다. 이를 통과로 처리하지 않고 스크롤/컨테이너 인쇄 규칙을 추가 수정했으며, 실제 PDF 내용·페이지 검사를 회귀 테스트에 추가했다.

## 자동 검증

- 프런트엔드: `npm test -- --run src/pages/shared src/pages/authentication src/pages/menumanagement src/pages/me/usePreferences.test.tsx src/pages/dashboardops src/app/router.test.tsx src/shared/charts/chartValueContracts.test.tsx --maxWorkers=2 --minWorkers=1`
  - 관련 회귀 테스트 21개 파일, 337개 테스트 통과.
  - 실제 인증 경계와 공통 앱을 함께 렌더하는 테스트에서 권한/기기 제한 분리, 390/767/768 경계, 본문 비마운트, 캐시 보존, 동적 메뉴 설정, 무권한 차단, 미인증 로그인, 대시보드 권한 없는 첫 화면 이동을 검사한다.
  - 점검 배지의 명칭·건수·미확인 값, 알 수 없는 코드, 오류/부분 실패, 영문·슬래시가 포함된 업무명 보존을 검사한다.
- 프런트엔드: `npm run build`
  - TypeScript 검사 및 Vite 프로덕션 빌드 통과.
- E2E: 외부 검증 앱 주소를 `SICOST_E2E_BASE_URL`로 제공하고 `npm test -- tests/dashboard-presentation.spec.ts --reporter=list` 실행.
  - Chromium 4개 통과, 1개 건너뜀. 건너뛴 테스트를 통과에 포함하지 않았다.
  - `SICOST_VERIFY_USER`, `SICOST_VERIFY_PASSWORD`가 필요하다. 별도 무권한 계정 검사는 `SICOST_VERIFY_RESTRICTED_USER`, `SICOST_VERIFY_RESTRICTED_PASSWORD`가 추가로 필요하다. 필요한 경우 `SICOST_E2E_CHROMIUM_PATH`로 실행 파일 경로를 지정한다.
  - PDF 출력의 내용 회귀 검사를 위해 테스트 전용 `pdfjs-dist`를 정확한 버전으로 추가하고 npm lockfile을 갱신했다.
- `git diff --check` 통과.

실행 환경의 Node는 22.21.1, npm은 10.9.4였으며, 저장소 선언 Node 20.x와의 engine 경고가 있었다. Node 20 실행은 별도 검증하지 않았다. Vite의 기존 큰 번들/동적 import 경고는 남아 있다. 백엔드 소스·마이그레이션 변경은 없어 Maven/DB 마이그레이션 검증을 변경 검증으로 실행하지 않았다.

## 미검증 및 완료 판정 보류 항목

1. 네이티브 브라우저 인쇄 미리보기 대화상자와 종이 출력: 실행 환경에는 headless Chromium shell만 있고 DISPLAY·일반 Chromium·Xvfb가 없다. PDF 인쇄 경로와 파일 내용/렌더링은 검증했지만, 원문이 요구한 네이티브 미리보기 대화상자를 열어 조작했다고 주장하지 않는다.
2. 실제 별도 무권한 계정의 모바일 직접주소 차단: 해당 계정 환경변수가 제공되지 않았다. 실제 인증 경계를 포함한 회귀 테스트로 차단을 검사했으나 별도 계정의 브라우저 결과는 없다.
3. 대시보드 조회 권한 없는 실제 계정의 안내 버튼 목적지: 해당 계정이 없어 실제 브라우저에서는 확인하지 못했다. 권한 경계 결합 테스트에서는 기존 이용 가능한 첫 화면으로 이동함을 확인했다.
4. 이번 실제 DB 응답에 없는 `BILL`·`SETL`·`BILL_MISS` 잔여 점검 배지와 일부 조회 오류/마감 확정 분기: 명칭·건수·상세 사유와 실패/빈 결과 처리는 자동 테스트로 검사했다. 해당 실제 업무 상태를 만들기 위한 임의 DB/업무 데이터 변경은 하지 않았으므로 이 분기들의 실제 브라우저 재현은 미검증이다.

## 검증 산출물

아래 경로는 저장소 루트 기준이다. `e2e/test-results/`는 기존 gitignore 대상이며 커밋 대상이 아니다.

- `e2e/test-results/dashboard-presentation-두-조-45599-구를-실제-Chromium-인쇄-PDF에-유지한다/dashboard-2026-02.pdf`
- 같은 디렉터리의 `dashboard-2026-11.pdf`
- 같은 디렉터리의 `dashboard-2026-02-page1.png`, `dashboard-2026-11-page1.png`: 실제 PDF에서 렌더링한 이미지.
- 같은 디렉터리의 각 조회월 `*-expected.json`, `*-print.png`: 실제 화면/인쇄 기준 대조 자료.
- `e2e/test-results/dashboard-presentation-실제--9a0b1--767-안내-유지와-버튼-이동-768-정상-접근/`: UI-23·UI-27의 390/767px 안내 캡처.

## 변경 파일

- `e2e/package.json`
- `e2e/package-lock.json`
- `e2e/tests/dashboard-presentation.spec.ts`
- `frontend/src/pages/authentication/AuthenticationBoundary.tsx`
- `frontend/src/pages/authentication/AuthenticationBoundary.test.tsx`
- `frontend/src/pages/dashboardops/DashboardScreen.tsx`
- `frontend/src/pages/dashboardops/DashboardScreen.test.tsx`
- `frontend/src/pages/dashboardops/DashboardTrendChart.tsx`
- `frontend/src/pages/dashboardops/dashboardPresentation.ts`
- `frontend/src/pages/dashboardops/dashboard.css`
- `frontend/src/pages/menumanagement/MenuNavigation.test.tsx`
- `frontend/src/pages/shared/SharedApplication.tsx`
- `frontend/src/pages/shared/shared.css`
- `frontend/src/pages/shared/MobilePcAccess.test.tsx`
- `docs/basic154-verification.md`

기존 미추적 `AGENTS.md`는 수정하지 않았다. 커밋·푸시는 하지 않았다.

## APP_BOOT_NOT_READY 재작업 검증

### 확인한 원인과 수정 범위

- 원본 Quality 결과의 실패 대상은 `compose-up`이다. 컨테이너 생성 전 Docker 기본 주소 풀이 소진됐다. 실제 `docker network create basic154-address-pool-probe`도 같은 `all predefined address pools have been fully subnetted` 오류로 실패했다. 이 단계의 실패는 시드·계산·로그인 실패를 뜻하지 않는다.
- 루트 `aiops.runtime-check.json`, 헬스 연산·첫 메뉴 연산, Flyway V1/V2 및 기동 스키마 검증 코드를 먼저 확인했다. 계약은 `/api/health`와 인증 후 `/UI-27`의 `/api/code-groups` 조회를 요구한다. 인증 요구사항과 초기 관리자 복구 절차는 변경하지 않았다.
- `infra/docker-compose.yml`: 프로젝트별 기본 네트워크를 유지하면서 `SICOST_NETWORK_SUBNET`으로 재정의할 수 있는 명시적 IPAM 서브넷을 추가했다. 기본값은 `10.254.154.0/24`이다. 자동 주소 풀을 사용하거나 다른 배포의 네트워크를 공유하지 않는다. 호스트 네트워크 삭제·prune·Docker daemon 설정 변경은 하지 않았다.
- 동시 배포에서는 실행자가 서로 겹치지 않는 서브넷을 할당해야 한다. 예: `SICOST_NETWORK_SUBNET=10.254.155.0/24 docker compose -p sicost-second -f infra/docker-compose.yml up -d --wait`. 기존 Docker 네트워크뿐 아니라 호스트/VPN 라우팅과의 충돌도 배포 환경에서 확인해야 한다.
- `infra/nginx.conf`: `/api/`의 프록시를 명시적 `proxy_pass http://backend:8080/api/;`로 맞췄다. 기존 API 경로와 Host 헤더 전달을 보존한다.
- `scripts/test_compose_runtime.py`: 기본/재정의 서브넷, 프로젝트별 네트워크 격리, DB host port 미노출, 앱 build·healthcheck 보존을 실제 Compose config 결과로 검사한다. 해석된 설정에는 DB 자격 증명이 있으므로 전체 설정은 출력하지 않는다. 변경 전 세 시험 실패, 변경 후 세 시험 통과를 확인했다.
- 메서드 시그니처·업무 계산·시드·OpenAPI·인증 정책·프런트엔드 의존성·Dockerfile은 수정하지 않았다. `.aiops-spec` 결과는 참조만 했고 생성·수정하지 않았다. 원본 결과 JSON 자체에는 build unit 2개, test target 7개, execution plan 6개가 있다. 전달 패킷의 0/6/6 차이를 앱 파일을 수정해 해결했다고 주장하지 않는다.

### 실제 실행 결과

- backend와 frontend 모두 `docker build --target build` 성공. 프런트엔드 빌드는 Dockerfile에 선언된 Node 20 build stage에서 실행했다.
- `docker compose -p qa-si-cost-27-scope-analysis-20261120t000517 -f infra/docker-compose.yml build` 성공.
- 같은 프로젝트의 `up -d --wait --wait-timeout 180 --no-build` 성공. 별도 `compose ps` 조회에서 database·backend·frontend 모두 running/healthy, 네트워크 inspect에서 명시한 기본 서브넷을 확인했다. 빈 새 DB의 Flyway 초기화와 스키마 기동 검증을 포함한다.
- 실제 frontend 컨테이너에서 `nginx -t` 성공. nginx를 거친 `/api/health` 응답이 200이며 계약의 success·meta 조건을 충족했다.
- frontend build stage 안에서 Quality execution plan의 여섯 시험 파일 실행: 92개 통과, 실패·건너뜀 0개. Vitest+jsdom 렌더 시험을 유지했다.
- backend build stage 안에서 DB 환경변수 없이 `mvn -B test`: 6,354개 중 실행 5,607개, 건너뜀 747개, 실패·오류 0개. DB/통합 시험의 건너뜀은 통과로 집계하지 않았다. 첫 offline 시도는 미캐시된 Surefire provider 때문에 실행되지 못했으며, 일반 명령으로 재실행해 위 결과를 얻었다.
- runtime 이미지는 frontend `nginx`, backend `sicost` 비루트 사용자를 유지한다. frontend runtime에 Node `/app`·소스 시험 트리·node_modules가 없고 정적 빌드만 존재함을 컨테이너에서 확인했다.
- 프런트엔드 소스 읽기/`?raw` 시험과 `http://localhost:8080` API 주소 검사에서 위반 0개. Compose config 및 `git diff --check` 통과.

### 앱 사용 확인 재실행

실제 빌드한 nginx/backend와 이번 검증에서 새로 만든 독립 DB를 사용했다. 별도 검증 계정 환경변수는 없었다. 기존 배포나 사용자 DB를 수정하지 않고 이 독립 DB에서만 문서화된 초기 관리자 CLI 복구를 수행했고, 실제 SMTP 수집기와 정상 MFA·필수 비밀번호 변경 흐름을 사용했다. 복구와 변경 후 해당 DB 행의 상태를 다시 조회했다. 인증·권한·메뉴 응답을 주입하거나 대체하지 않았다.

기본 Compose만으로 먼저 모든 서비스의 healthy 상태를 확인했다. 인증 브라우저 검증에는 임시 Compose override로 수집용 SMTP를 연결하고, 격리된 HTTP 통신에 한정해 `SESSION_COOKIE_SECURE=false`를 설정했다. 앱의 기본 secure-cookie 정책은 변경하지 않았다. 이 HTTP 검증을 TLS 배포 검증으로 간주하지 않는다.

실제 Chromium 결과는 PASS이다.

1. 계약의 `/api/health` 응답 조건 통과.
2. 미인증 `/api/code-groups`가 401이고 `/UI-27` 방문 시 로그인 화면 표시.
3. 초기 관리자 로그인, 실제 메일의 MFA, 필수 비밀번호 변경, 이후 session 재조회에서 인증 완료 확인.
4. 인증 후 `/api/code-groups`의 success·items 배열 조건 및 `/UI-27` 공통코드 본문 표시 확인.
5. `/UI-16` 대시보드 로딩 완료와 실제 요약 카드 5개 확인.
6. UI-23·UI-27 각각 390/767px에서 이름을 포함한 안내·주소 유지·클릭 후 대시보드 이동, 768px에서 정상 본문 표시: 여섯 조합 통과.

본문·계정·비밀번호·OTP·쿠키·토큰은 검증 로그나 결과에 남기지 않았다. 브라우저 trace·영상·스크린샷·응답 파일을 생성하지 않았고 종료코드·단계별 통과 여부만 수집했다. SMTP 수집 메시지는 삭제 후 빈 수신함을 재조회했다. 이 결과는 실제 앱 재실행 증거이지 Quality Agent 공식 verdict를 재발급한 결과는 아니다.

### 보안 검사와 남은 범위

원본 Quality 결과는 `security_enabled=false`이며 보안 검사 통과 증거가 없다. 이번 재작업은 기존 시험·보안 관련 설정을 제거하거나 완화하지 않았다. 전체 Maven 시험의 인증/권한/CSRF 및 계약 시험을 그대로 실행했고, 별도로 lockfile 기반 npm audit를 실행했다.

- frontend audit: 총 10건(보통 4, 높음 4, 심각 2). frontend 의존성 manifest는 HEAD와 동일하다.
- e2e audit: 총 2건(높음 2), 기존 Playwright 계열. 기존 Playwright 버전은 HEAD와 동일하다. 추가된 PDF 시험 의존성은 이번 감사의 취약 패키지 목록에 없다.
- 위 audit는 종료코드 1이며 보안 통과로 표기하지 않는다. 기동 실패와 관계없는 광범위한 의존성 갱신은 수행하지 않았다.
- 전체 보안 E2E 검증자 전용 실행기는 필요한 별도 합성 계정·업무 fixture·메일 환경이 없고 구현 에이전트 실행 금지로 명시되어 있어 실행하지 않았다. 이번 초기 관리자 앱 확인은 해당 전체 보안 E2E를 대체하지 않는다.
- 앞선 검증의 네이티브 인쇄 미리보기 및 별도 무권한 계정 브라우저 검증 제한은 그대로 남는다. 이번 기동 수정으로 이를 완료했다고 변경하지 않는다.

이번 재작업에서 수정·추가한 저장소 파일은 `infra/docker-compose.yml`, `infra/nginx.conf`, `scripts/test_compose_runtime.py`, `docs/basic154-verification.md`이다. 기존 사용자의 staged/unstaged 앱 변경을 덮어쓰거나 커밋하지 않았다.
