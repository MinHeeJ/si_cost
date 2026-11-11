# SI 프로젝트 · 원가 관리 공통 기반

## 실행과 종료

저장소 루트에서 `docker compose -f infra/docker-compose.yml up --build -d`로 실행한다.
프론트는 http://127.0.0.1:3000, 백엔드 health는 http://127.0.0.1:8080/api/health 이다.
종료는 `docker compose -f infra/docker-compose.yml down`이다. PostgreSQL 지속 볼륨은 삭제하지 않는다.
DB를 호스트에 공개하지 않으며 앱 포트도 loopback에만 바인딩한다. 이 버전은 인증을 제공하지 않으므로 공개 배포하면 안 된다.
기본 개발 설정으로 시작할 수 있다. 운영에서는 DB_NAME/DB_USER/DB_PASSWORD를 별도로 정하고 기본 개발 비밀번호를 반드시 교체한다.

## 초기 관리자와 첫 사용

로그인·비밀번호 입력 화면은 없다. 최초 빈 DB 기동 시 Flyway V1/V2가 원본 스키마와 전체 초기값을 설치한다.
기존 초기 관리자·역할 연결을 DB에서 조회하는 CurrentUserProvider가 임시 관리자 컨텍스트를 공급하며, 비밀번호나 메일은 응답에 포함하지 않는다.
확인용 별도 계정이나 AIOPS_SMOKE 환경변수는 사용하지 않는다. 최초 관리자 생성은 원본 시드에 포함되고 별도 계정 설정에 의존하지 않는다.
프론트 진입점은 공식 라우터와 공통 레이아웃에 연결되어 있다. `/`는 `/UI-16`으로 이동하며 `/UI-27`에서 코드·엑셀 표기 대응·운영 기준값·변경 이력을 사용한다. 나머지 등록 업무 화면은 지정 기간 선택과 후속 단계 안내를 제공한다.

## 기술 결정과 원본 보존

Java 17 / Spring Boot 3.3.13 / Maven / MyBatis 3.0.4 / Flyway / PostgreSQL 16을 사용한다.
프론트는 Node 20 / React 18.3.1 / Vite 5.4.21 / TypeScript / React Router, npm을 사용한다.
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

루트에서 `python3 /app/infra/hermes/runner/code_agent_detail/node/self_check.py .`만 실행한다.
이 명령이 Maven/Vitest 시험 및 별도 PostgreSQL migration 검증을 소유한다. Dockerfile 안에서는 테스트를 실행하지 않는다.
DB 통합 테스트는 명시한 SICOST_TEST_DB_URL, SICOST_TEST_DB_USER, SICOST_TEST_DB_PASSWORD가 제공될 때만 실행한다.
URL에는 반드시 버려도 되는 독립 시험용 DB를 지정한다. 테스트마다 자체 UUID 스키마를 만들며 다른 스키마/DB를 초기화하거나 삭제하지 않는다.
환경변수가 없으면 통합 테스트를 건너뛴다. 기본 테스트는 서비스 없이 HTTP 계약·비밀 비노출·입력 경계·revision·트랜잭션 호출·컴포넌트를 검증한다.
frontend/package-lock.json이 포함되어 있으며 package.json의 의존성·engines 선언과 일치한다. 설치 시 해당 lockfile을 사용하는 `npm ci`를 적용하고 손으로 lockfile을 수정하지 않는다.
