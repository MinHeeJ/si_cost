# BASIC-147 실제 통합 발견

## 기준선

- 작업 HEAD 및 `git ls-remote origin refs/heads/main` 결과: `2072829d7912b79d61f6a80f47ce525bfd361b6e`.
- 사용자가 확인한 `cffdfc52c4217bd91d6899174e30e369c485b8e9`보다 뒤의 실제 main이다.
- 최초 작업 상태에는 `backend/src/test/resources/contracts/openapi.yaml` 수정이 이미 있었다. 이 파일은 변경하지 않았다.
- 루트 AGENTS.md, CLAUDE.md, .cursorrules는 없었다. 다른 worktree의 규칙은 복사하지 않았다.
- Boot 3.3.13 / Java 17 / MyBatis 3.0.4 / Flyway. frontend React 18 / TypeScript / Vite, npm lockfile 존재.

## 실제 코드 연결점

기존 CurrentUserProvider는 DB의 첫 활성 ADMIN을 자동 선택하고 ALL 및 allows=true를 제공했다. 해당 선택 SQL을 제거했다. 현재 주체는 서버 세션에서 명시 저장된 SecurityContext이고, 로그인은 TB_SY_USER 인증열과 활성 역할을 읽는다. 보안 오류는 기존 ApiError 봉투 및 RequestIdFilter를 사용한다.

실제 코드로 발견한 후속 기능은 projectsops, assignmentsops, billsops, receiptsops, monthclosesops, salespartsettlementsops, projectstatusboardops, candidatesops, analysis, notificationsops, automationrunsops, roles/rolesops, menumanagement/menumanagementops, currentuserops 등이다. 이 디렉터리의 존재를 권한 통합 합격으로 취급하지 않는다. 업무 계산은 기존 서비스에 남겨 둔다.

- 인증메일은 기존 MAIL_* 환경 설정을 읽지만 업무 발송큐에는 넣지 않는 별도 메모리 전용 SMTP 경로다.
- 기존 사용자 등록 본체 트랜잭션이 반환된 후 첫 발급/SMTP를 호출한다.
- 사용자 초기화는 선택 사용자와 UI-23 UPD를 확인한다. 기존 상태/역할/담당은 보존한다.
- 실제 Controller 전체경로/HTTP메서드는 `backend/src/main/resources/permission-operations.tsv`에 명시되어 있다. 특이사항: ResumeExtractionController는 ResumeExtractionService.java 안에 있는 package-private 컨트롤러다.
- JSON 외 직접 fetch 27개 호출점을 발견했다. 업로드, xlsx 다운로드, AI 스트림, 이력서 추출을 공통 authenticatedFetch로 이동했다.
- 내부 자동작업은 DailyAutomationService.scheduled 및 기존 SYSTEM 감사 경로가 있다. 사용자 비동기 AI는 서버 전용 인증증명으로 작업 주체를 전달하고, 현재 상태/해시/ADMIN MFA 및 원천 메뉴를 재검사한다. 저장/최종 SSE 전에도 검사하며, 작업 스레드의 기존 SecurityContext를 복구한다. 제한 범위 AI와 과거 답변의 세부 재투영은 여전히 미완료다.
- 프로젝트 목록은 DB 소스 단계에서 범위를 적용하여 count/pagination 전 동일 조건을 사용한다. 업무 변경 중의 마감/정산 잠금·원천 가격 조회에는 응답 투영을 적용하지 않는다.
- 후보 희망단가는 PM 범위에서 JSON 키/열/모바일 카드/상세/열 설정/실제 xlsx에서 제외한다. 금지값 입력은 거부하고 기존 값 누락/null/빈 문자열은 저장값을 보존한다. 맞는 자리의 후보 원가·역산 가능한 이익률/판정도 응답에서 제외한다.
- 알림 자동 갱신, 자동작업/메일 전달상태 자동 폴링은 등록된 경로에서만 X-Background-Request로 구분한다. 사용자 직접 조회는 성공 활동으로 처리한다.
- 권한 없는 첫 화면은 안내/로그아웃만 제공한다. 만료 401의 재조회 순환, 동일 capabilities의 403 재마운트 순환, 오래된 CSRF 응답의 캐시 복원을 차단했다. 브라우저의 0초 표시만으로 다른 탭을 로그아웃시키지 않고 서버에 재확인한다.
- 알림 목록과 읽음 응답은 현재 메뉴 및 원천의 귀속월/프로젝트/파트로 다시 투영한다. 권한 없는 이름·금액·링크는 지정 문구로 대체하며 AI/업로드 완료는 일반 완료 상태만 표시한다. 저장된 알림 본문은 변경하지 않는다.
- 공통 마스터/사람 이력의 목적 메뉴를 분리하고 비밀·금지 단가 이력은 SQL에서 집계/페이지 이전 제외한다. PM/파트장/거래처/파트/프로젝트 옵션은 고정 입력업무 정책을 사용하며 전체 사용자 권한표를 옵션으로 반환하지 않는다.
- 사용자·역할·메뉴 관리와 프로젝트/거래처/개발자 폼에 서버 capabilities 기반 등록/수정 구분과 읽기 전용 잠금을 추가했다. 전체 화면의 버튼/단축키 전수 연결 완료를 뜻하지 않는다.
- 급여/개인원가 JSON의 실제 민감 키가 포함되면 필수 SNSTV_VIEW를 독립 트랜잭션으로 확정한 뒤 응답한다. 필수 기록 실패는 민감 본문 전송 전에 전파된다. ADMIN 추가 후 MFA 재완료는 기존 로그인에 LOGIN을 중복 기록하지 않는다.
- 비밀번호 권장 안내의 선택은 현재 서버 세션에 보존하여 새로고침 시 반복하지 않으며 새 로그인에는 다시 판정한다. 빈 인증 명령은 공통 클라이언트에서 JSON 객체로 전송한다.
- 스케줄러는 고정 SYSTEM 명령 문맥을 사용하며 이 문맥은 HTTP/인증된 사용자 요청에서 열 수 없다. SYSTEM은 ADMIN/ALL 역할을 받지 않는다. 운영자 CLI의 비웹 프로세스는 스케줄/중단 발송 재개를 실행하지 않는다. README의 패키지 PropertiesLauncher 진입점도 실제 해석을 확인했다.

## 시안 관찰 제한

- 브라우저 navigate/inspect는 `/evidence/...png` 쓰기 PermissionError로 실패했다.
- 지정 HTML URL 직접 요청은 HTTP 404였다.
- `/mnt/c/Users/basic/Desktop/example.html`은 존재하지 않았다.
- 따라서 시안 전체/인증 상세/모바일/역할 화면을 시각적으로 확인했다고 주장하지 않는다. 현재 로그인 UI는 기존 공통 컴포넌트 및 스타일에 맞춘 구현이며 시안 일치 수락은 미검증이다.

## 실제 실행

- Docker Maven Java17 `mvn -q -Dmaven.test.skip=true package`: 종료 0.
- frontend `npm run build`: 종료 0.
- 본 재개 실행의 선택 백엔드 회귀/패키지: 11개 suite 99개, 실패/오류/건너뜀 0, Maven package 종료 0. AuthenticationIntegrationTest 22개에는 실 PostgreSQL 조회, 실제 MailHog MFA/발급 수신, 운영자 별도 프로세스, SMTP 중 DB 잠금 해제, 필수 로그인 감사 실패, 세션 철회·배경 무연장, scoped 프로젝트 count, 후보 JSON/실제 xlsx 및 알림 원문 보존·재투영 검사가 포함된다. 기존 알림/활동 DB 회귀도 독립 UUID 스키마에서 실행했다.
- 본 재개 실행의 frontend build 종료 0 및 선택 회귀 6개 파일 70개 통과. 공통 인증 전송, 인증 경계/MFA 복구, 기존 NotificationBell/AutomationRunsPanel/DunningDelivery 시험이다. 이들은 단위/컴포넌트 시험이며 실제 브라우저 수락으로 바꿔 보고하지 않는다. 새 관리 화면 읽기 전용과 안내 선택 지속성의 실제 브라우저 수락을 이 수치로 주장하지 않는다.
- fixture는 독립 시험 DB에 난수 사용자/추가 역할/업무 행을 생성한다. 기존 역할의 초기 권한을 덮거나 운영 계정/시퀀스를 재초기화하지 않았다. 테스트용 식별자의 실제 20자 제한, 후보 ID 형식, 역할명 유일 제약을 반영하여 같은 DB에서 반복 실행했다.
- 기존 테스트 일부는 임시 ADMIN 자동선택, developmentMode=true 및 무인증 성공 자체를 명시적으로 요구한다. 이 보안 요구와 충돌하는 assertion은 삭제하거나 약화하지 않았다. 전체 기존 회귀가 통과했다고 주장하지 않는다.

## 아직 완료되지 않은 통합

프로젝트 목록/후보 예외 밖의 제한 PART/PROJ 업무별 SQL·쓰기·연결ID 전량 검사, 모든 민감 DTO/워크북과 금지필드 보존, 업로드 반영 직전 검사, AI 답변 재투영, 알림 수신자 최종 전송 검사, SYSTEM/사용자 비동기 전수 회귀는 별도 미완료다. 아직 범위 제한이 연결되지 않은 업무와 후보 외 제한범위 공통 다운로드는 POLICY_INTEGRATION_REQUIRED로 차단하며 전체자료를 허용하지 않는다. 이는 요구 기능 완료나 제한 역할의 정상 업무 수락이 아니다. 전역 설정/공통 옵션의 세부 자격과 UI 읽기 전용 전수 연결도 추가 구현이 필요하다. 전체 tasks.md 완료 또는 배포/최종 Quality 통과를 주장하지 않는다.
