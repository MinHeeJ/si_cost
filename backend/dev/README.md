검증용 로컬 메일 수집기: `python3 backend/dev/mail_collector.py`.
SMTP는 127.0.0.1:1025, 수집 결과는 http://127.0.0.1:8025 이며 메모리에만 보관한다. 외부 전달은 없다.
같은 호스트에서 앱을 실행할 때 MAIL_ENABLED=true, MAIL_HOST=127.0.0.1, MAIL_PORT=1025,
MAIL_FROM=sender@example.invalid, MAIL_STARTTLS=false, APP_PUBLIC_URL=http://127.0.0.1:8080 을 사용한다.
인증 ID/비밀번호는 생략한다. 기본 앱 설정 MAIL_ENABLED=false는 유지한다.
