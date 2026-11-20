# SI 프로젝트 · 원가 관리 공통 기반

## 실행과 종료

저장소 루트에서 `docker compose -f infra/docker-compose.yml up --build -d`로 실행한다.
프론트는 http://127.0.0.1:3000, 백엔드 health는 http://127.0.0.1:8080/api/health 이다.
종료는 `docker compose -f infra/docker-compose.yml down`이다. PostgreSQL 지속 볼륨은 삭제하지 않는다.
Compose 기본 네트워크는 고정 서브넷을 공유하지 않고 Docker IPAM이 배포별로 사용하지 않는 주소 대역을 자동 할당한다. 자동 주소 풀이 소진되거나 호스트/VPN 경로와 겹치면 실행 전에 `COMPOSE_SUBNET`으로 사용하지 않는 사설 `/24`를 지정한다. 명시 지정 시 동시 배포마다 서로 다른 서브넷을 사용한다. 공유 호스트의 다른 네트워크를 prune하거나 Docker 데몬 설정을 변경하지 않는다. 네트워크 할당 실검증은 `COMPOSE_NETWORK_INTEGRATION=true python3 -m unittest scripts.test_compose_contract -v`로 실행하며, 이 시험이 만든 임시 네트워크만 정리한다.
DB를 호스트에 공개하지 않으며 앱 포트도 loopback에만 바인딩한다. 모든 업무 API에는 서버 세션 인증·현재 저장 권한 검사를 적용한다. 공개 배포 전 HTTPS·Secure 쿠키·SMTP 및 아래 미검증 통합을 확인해야 한다.
기본 개발 설정으로 시작할 수 있다. 운영에서는 DB_NAME/DB_USER/DB_PASSWORD를 별도로 정하고 기본 개발 비밀번호를 반드시 교체한다.

## 초기 관리자와 첫 사용

로그인 화면은 `/UI-22`다. 기존 계정/역할/메뉴/담당/기준값을 재설치하거나 일괄 비밀번호 초기화하지 않는다. `CurrentUserProvider`는 실제 인증 주체만 사용하며 임시 관리자·고정 비밀번호·자동 로그인은 없다.

기존 USE/LOCK ADMIN 계정의 초기 비밀번호를 모르는 경우에만 운영자가 `AuthenticationAdminCli`를 명시 실행한다. `mvn -f backend/pom.xml -DskipTests package`로 생성한 `backend/target/sicost.jar`와 운영 DB 환경을 사용한다. 다음 명령의 계정과 이메일은 운영자가 확인한 실제 기존 값으로 바꾼다. 비밀번호는 명령행 인수가 아니다.

```sh
java -Dloader.main=com.sicost.authentication.AuthenticationAdminCli \
  -cp backend/target/sicost.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  reset EXISTING_ADMIN_ID VERIFIED_EMAIL
```

콘솔의 숨김 입력으로 운영자가 정한 규칙에 맞는 비밀번호를 입력한다. 비대화형 실행에서만 비밀 주입 시스템의 `AUTH_INITIAL_PASSWORD` 환경을 사용할 수 있으며 셸 이력·스크립트·파일·공용 초기값에 저장하지 않는다. CLI는 새 사용자 생성·ADMIN 승격·STOP 재개를 하지 않는다. 초기화 후 TEMP=Y와 ADMIN MFA를 유지하며 일반 로그인 절차를 생략하지 않는다. 운영자 프로세스는 HTTP 서버와 자동 배치/발송 재개를 시작하지 않는다.

관리자 전원이 LOCK인 경우 동일 환경에서 다음 명시 명령으로 기존 ADMIN의 상태/실패횟수만 복구한다. 비밀번호·이메일·역할·담당을 바꾸지 않는다.

```sh
java -Dloader.main=com.sicost.authentication.AuthenticationAdminCli \
  -cp backend/target/sicost.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  unlock EXISTING_ADMIN_ID
```

필수 환경은 `DB_URL`, `DB_USER`, `DB_PASSWORD`와 인증 메일용 `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `MAIL_STARTTLS`다. SMTP 인증이 필요하면 `MAIL_USERNAME`/`MAIL_PASSWORD`를 함께 비밀 주입한다. 운영 STARTTLS는 true를 사용한다. 로컬의 격리된 MailHog 같은 수집 서버만 포트 1025·STARTTLS=false로 구성하고 실제 수집함에서 메일을 확인한다. `ACCEPTED`는 SMTP 접수이며 수신함 배달 보장이 아니다. 메일 미설정/거절/불명은 인증 우회가 아니며 이전 비밀번호/코드를 복구하지 않는다.

기본 쿠키는 HttpOnly·SameSite=Lax·Secure=true다. 로컬 HTTP 검증에서만 백엔드에 `SESSION_COOKIE_SECURE=false`를 명시 주입한다. 현재 Compose가 이 변수를 전달하지 않으므로 별도 로컬 override의 backend.environment에 설정한다. SMTP 환경도 동일하게 전달해야 한다. 운영에서는 HTTPS와 Secure=true를 유지한다.

정상 첫 사용: 로그인 → 실제 수집 메일의 ADMIN 인증번호(5분) → 임시 비밀번호 강제 변경 → 허용 대시보드 또는 메뉴 순서상 첫 허용 화면. 모바일은 PC 전용 화면을 제외하며 허용 화면이 없으면 안내와 로그아웃만 제공한다. 확인기의 `auth_required`와 `auth.required`는 true이고, 세션ID/비밀번호/인증번호는 확인 산출물에 저장하지 않는다.

롤백/재배포도 인증을 끄거나 옛 해시를 복구하지 않는다. 필요하면 앱 인스턴스를 모두 정지해 메모리 세션을 폐기하고 인증이 유지되는 버전을 배포한다. DB 원장·저장 권한·계좌 암호문·감사기록은 그대로 보존한다. 기존 미통합/제한 범위 안전 차단과 실제 검증 범위는 `docs/security/basic147-integration-discovery.md`를 참고한다. 안전 차단은 해당 업무의 통합 완료를 뜻하지 않는다.

## 기술 결정과 원본 보존

Java 17 / Spring Boot 3.3.13 / Maven / MyBatis 3.0.4 / Flyway / PostgreSQL 16을 사용한다.
프론트는 Node 20 / React 18.3.1 / Vite 5.4.21 / TypeScript / React Router, npm을 사용한다.
영구 OpenAPI 시험 자원은 `backend/src/test/resources/contracts/openapi.yaml`이다. 앱 사용 확인기의 8 MiB 읽기 한도 안에서 동일한 YAML 값을 앵커로 공유할 수 있으며, 연산·스키마·설명·추적 정보를 생략하지 않는다. 갱신 후 `python3 scripts/compact_openapi_fixture.py`로 의미 동일성과 크기를 확인한다(PyYAML 필요).
백엔드 단독 컨텍스트에서도 계약 시험을 실행하도록 같은 contracts 폴더에 runtime/첫 사용 안내 fixture를 보존한다. 인증 필수 값은 루트와 동일해야 한다. 저장소 전체 Maven 실행에서는 루트 파일을 우선 복사한다.
부모 POM과 MyBatis POM 및 npm 정확 버전 메타데이터의 실제 HTTP 200을 확인했다.
요청의 D1~D6/D9/D13 결정을 적용한다. 역사적 상류 문서는 수정하지 않았으며 JPA validate를 실행했다고 주장하지 않는다.
SQL 원본·승인 OpenAPI fixture는 수정하지 않는다. 원본·투영본 해시와 기계적 차이는 infra/sql-projection.json을 참조한다.
제공된 복사본에는 Git 메타데이터·기존 애플리케이션·기존 migration 이력이 없다. 기록된 원천 revision은 문서의 provenance이며 현재 checkout 확인 결과가 아니다.
Flyway만 구조를 생성한다. schema.sql은 별도 검토용 멱등 스냅샷이며 Spring SQL 초기화는 꺼져 있다.
설치 중 실패는 group 트랜잭션으로 롤백한다. 자동 baseline/clean/사용자 DB DROP·TRUNCATE는 사용하지 않는다.

## 공유 코드 사용

백엔드 com.sicost.common.api는 success/meta/data와 루트 오류 봉투, 안전한 X-Request-Id를 제공한다.
validation 패키지는 문자열·십진 입력·실제 연월·JSON을 검증하고 persistence.DatabaseMonthCodec은 YYYY-MM/ YYYYMM을 변환한다.
CurrentUserProvider는 DB 컨텍스트 단일 공급자다. 업무 DTO는 공급자의 CurrentUser 내부 모델을 그대로 직렬화하지 말고 승인된 응답 필드만 투영한다.
WriteCoordinator.write에는 오름차순 잠금 집합, expectedRevision, 전체 aggregate 조회, 도메인 검증, 실제 차이 판정, 본체+이력 쓰기를 전달한다.
전체 aggregate 조회는 정렬된 식별자·모든 저장값·등록/수정 시각을 포함해야 하며 그룹 전체 코드(삭제행 포함), 기준키 전체 적용월을 누락하면 안 된다.
실제 차이가 없으면 본체와 이력 콜백을 실행하지 않는다. 변경 작업 실패는 전체 롤백되며 CommittedChange 이벤트는 커밋 뒤에만 발행한다.
클라이언트가 보낸 테이블명으로 SQL을 만들지 않는다. MyBatis 쓰기 인터셉터가 허용 6테이블과 이력 append-only 경계를 적용한다.
ChangeHistoryWriter.append는 기존 업무 트랜잭션 안에서만 사용하고 500자를 절단하지 않는다.

프론트 공통 모듈은 frontend/src/shared/에 있다. DataTable<T>는 업무 renderer/columns를 주입받는다.
Text/Decimal/CodeSelect는 controlled 입력이고 Dialog는 focus trap/복원·pending 중복 제출 차단을 제공한다.
Feedback의 StatusBadge/ErrorBanner/Toast는 명시한 의미 색을 유지한다. common.css를 진입점에서 한 번 import한다.
request<T>는 상대 API·success/meta/data·201/204 빈 본문·field/details.violations를 지원하며 자동 재시도하지 않는다.
useCommitRefresh는 저장 성공 후 재조회 실패를 별도로 알린다. format 모듈은 저장 문자열을 변경하지 않고 정밀도를 보존한다.

## 검증

루트에서 `python3 /app/infra/hermes/runner/code_agent_detail/node/self_check.py .`를 실행한다.
계약 자원의 읽기 한도·루트/백엔드 fixture 일치·인증 필수 계약 및 Compose 네트워크 대조 회귀 검증은 `python3 -m unittest discover -s scripts -p 'test_*.py'`로 실행한다(PyYAML 및 Docker Compose 필요).
이 명령이 Maven/Vitest 시험 및 별도 PostgreSQL migration 검증을 소유한다. Dockerfile 안에서는 테스트를 실행하지 않는다.
DB 통합 테스트는 명시한 SICOST_TEST_DB_URL, SICOST_TEST_DB_USER, SICOST_TEST_DB_PASSWORD가 제공될 때만 실행한다.
URL에는 반드시 버려도 되는 독립 시험용 DB를 지정한다. 테스트마다 자체 UUID 스키마를 만들며 다른 스키마/DB를 초기화하거나 삭제하지 않는다.
환경변수가 없으면 통합 테스트를 건너뛴다. 기본 테스트는 서비스 없이 HTTP 계약·비밀 비노출·입력 경계·revision·트랜잭션 호출·컴포넌트를 검증한다.
frontend/package-lock.json이 포함되어 있으며 package.json의 의존성·engines 선언과 일치한다. 설치 시 해당 lockfile을 사용하는 `npm ci`를 적용하고 손으로 lockfile을 수정하지 않는다.

<!-- 병합 흐름 점검용 줄 (2026-11-11). 기능과 무관하며 지워도 된다. -->
