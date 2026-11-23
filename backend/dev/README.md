검증용 로컬 메일 수집기: `python3 backend/dev/mail_collector.py`.
SMTP는 127.0.0.1:1025, 수집 결과는 http://127.0.0.1:8025 이며 메모리에만 보관한다. 외부 전달은 없다.
같은 호스트에서 앱을 실행할 때 MAIL_ENABLED=true, MAIL_HOST=127.0.0.1, MAIL_PORT=1025,
MAIL_FROM=sender@example.invalid, MAIL_STARTTLS=false, APP_PUBLIC_URL=http://127.0.0.1:8080 을 사용한다.
인증 ID/비밀번호는 생략한다. 기본 앱 설정 MAIL_ENABLED=false는 유지한다.

## 브랜치 미리보기 테스트 계정

SyncOps가 `syncops-preview-`로 시작하는 Compose 프로젝트를 실행하면 기본 DB
마이그레이션 완료 뒤 미리보기용 계정이 추가됩니다. 같은 아이디가 이미 있으면
비밀번호, 역할과 상태는 기존 값으로 유지됩니다.

| 역할 | 아이디 | 초기 비밀번호 |
|---|---|---|
| 재무 담당 | `fin1` | `finance1234` |
| 경영진 | `exec1` | `SiCost-QA-29!` |
| 영업 파트장 | `sales1` | `SiCost-QA-29!` |
| PM | `pm1` | `SiCost-QA-29!` |
| 시스템 관리자 | `qaadmin` | `SiCost-QA-29!` |

영업 파트장과 PM은 담당 파트·프로젝트를 지정한 범위의 데이터를 조회합니다.
`qaadmin` 로그인은 관리자 이메일 2단계 인증을 사용합니다. 인증 메일을
테스트할 때는 위의 로컬 메일 수집기나 미리보기 전용 SMTP를 연결합니다.
