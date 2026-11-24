# si_cost 로컬 미리보기 Gmail 메일 설정

- 작성: 2026-11-23
- 대상: 로컬 미리보기(compose 프로젝트 `sicost-preview`)

## 1. 값 받기

담당자에게 공용 회사 Google 계정 주소와 앱 비밀번호(16자리)를 받는다. 비밀번호는 채팅방·이슈·커밋에 붙이지 않는다.

## 2. 설정 파일 만들기

`si_cost-app/infra/.env.mail.local`

```
MAIL_USERNAME=전달받은계정주소
MAIL_FROM=전달받은계정주소
MAIL_PASSWORD=전달받은앱비밀번호16자리
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_STARTTLS=true
```

```bash
chmod 600 ~/prj-syncops/si_cost-app/infra/.env.mail.local
```

## 3. compose.local-preview.yml 고치기

`si_cost-app/infra/compose.local-preview.yml`의 backend 부분을 아래처럼 한다(`MAIL_*` 줄은 `environment`에서 지운다).

```yaml
services:
  database:
    ports: !override
      - "127.0.0.1:32654:5432"
  backend:
    ports: !override
      - "127.0.0.1:32653:8080"
    env_file:
      - .env.mail.local
    environment:
      SESSION_COOKIE_SECURE: "false"
    depends_on:
      mailpit:
        condition: service_started
  frontend:
    ports: !override
      - "127.0.0.1:32652:8080"
  mailpit:
    image: axllent/mailpit:v1.20.0
    ports:
      - "127.0.0.1:32655:8025"
```

## 4. 서버 다시 띄우기

```bash
cd ~/prj-syncops/si_cost-app/infra
docker compose -p sicost-preview -f docker-compose.yml -f compose.local-preview.yml up -d --build --wait backend
docker compose -p sicost-preview -f docker-compose.yml -f compose.local-preview.yml restart frontend
docker compose -p sicost-preview -f docker-compose.yml -f compose.local-preview.yml exec backend printenv | grep -E '^MAIL_(HOST|PORT|STARTTLS|ENABLED)='
```

폴더 경로와 `-p` 뒤 프로젝트 이름(sicost-preview)은 각자 쓰는 값으로 바꾼다.

마지막 줄에 `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`이 보이면 된다.

## 5. admin 이메일을 받을 주소로 바꾸기

`infra` 폴더에서 실행한다.

```bash
echo "update tb_sy_user set email_addr='받을주소@gmail.com' where user_id='admin';" \
  | docker compose -p sicost-preview -f docker-compose.yml -f compose.local-preview.yml exec -T database sh -c 'psql -U $POSTGRES_USER -d $POSTGRES_DB'
```

## 6. 확인

1. http://127.0.0.1:32652 에서 admin 로그인
2. 받을 주소 메일함에 「SI-COST 관리자 인증번호」 메일(없으면 스팸함)
3. 6자리를 5분 안에 입력

| 안 될 때 화면 문구 | 확인할 것 |
|---|---|
| 인증 메일 설정을 확인해 주세요 | `.env.mail.local` 값 빠짐·오타, 3번에서 `MAIL_*` 줄이 남아 있음 |
| 인증 메일 발송에 실패했거나 접수 여부가 불명입니다 | 앱 비밀번호 오류 |
| 메일이 안 옴 | 스팸함, 5번 주소 |
