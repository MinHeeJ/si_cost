# 로그인·실제 권한 최종 검증 상태

## 구현자 확인 범위

- 발견한 로컬 HEAD: `2072829d7912b79d61f6a80f47ce525bfd361b6e` (`main`). 변경 사항은 미커밋 상태다. 원격 최신 main과 동일하다고 주장하지 않는다.
- `session.json`과 `report.json`을 재독했다. 보고서는 `implementation_status=incomplete`, `verification_status=unverified`, `reason=interrupted`이며 과거 frozen snapshot의 실패/미검증 증거가 포함되어 있다. 이전 실행 성공을 현재 소스의 성공으로 재사용하지 않는다.
- 최근 e2e 변경 파일의 TypeScript 검사: `tsc --noEmit --strict --target ES2022 --module ESNext --moduleResolution bundler --skipLibCheck --types node --typeRoots ../frontend/node_modules/@types tests/support/authenticatedTest.ts tests/basic147/legacy-regression.spec.ts tests/basic147/role-revocation.spec.ts` — 종료코드 **0**. 브라우저 실행 결과가 아니다.
- Backend `mvn -q -DskipTests test-compile` — 종료코드 **0**. 시험 실행이 아닌 Java/test-source compile이다.
- Frontend `npm run typecheck` — 종료코드 **0**. 전체 build 또는 Vitest 결과가 아니다.
- Python 검증 runner 구문 compile — 종료코드 **0**. 구현자는 runner를 실행하지 않았다.
- `git diff --check` — 종료코드 **0**. TSV 정적 집계는 297개 method/path, 중복 0개이며 실제 Spring mapping 대조 실행은 별도다.
- 사용자 지시로 이번 재개에서 시험 suite, 전체 build, browser를 직접 실행하지 않는다. 따라서 시험수·실패수·skip수는 **미집계**이며 0으로 간주하지 않는다. 이전 backend/frontend 선택 시험 집계는 현재 변경의 증거가 아니다.

## 검증자 실행 산출물

`python3 scripts/security/run-basic147-verification.py --execute`는 검증자 전용이다. 명시적인 disposable 앱/DB/SMTP 환경이 없으면 실행하지 않는다. 실제 backend verify, frontend typecheck/unit/build, 실제 browser 보안·기존 회귀를 실행한 뒤 이 파일을 실제 종료코드와 새 시험 보고서의 합계로 교체한다. 비밀번호·OTP·쿠키·CSRF·응답 본문·원시 로그는 이 보고서에 쓰지 않는다. skip, flaky, 파싱할 수 없는 결과, 미실행/차단을 전체 통과로 바꾸지 않는다.

필수 환경 변수의 **이름**:

- `BASIC147_INTEGRATION=true`, `BASIC147_E2E_DISPOSABLE=true`.
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` — 검증자 소유 disposable DB만 사용.
- `SICOST_E2E_BASE_URL`, `BASIC147_MAIL_COLLECTOR` — 실제 앱과 실제 SMTP 수집 서버.
- `BASIC147_E2E_ACCOUNTS_JSON`, `BASIC147_E2E_TARGETS_JSON` — 환경 내 합성 fixture. 값/비밀을 저장소에 기록하지 않는다.
- `BASIC147_E2E_B1_FILE` — 실제 B.1 형식의 합성 XLSX 검사 파일.
- Maven을 Docker로 실행할 경우 `BASIC147_DOCKER_NETWORK`를 앱/DB/수집기의 검증 network에 맞춘다.

계정 fixture 키: `ADMIN`, `EXEC`, `FIN`, `SALES_LDR`, `PM`, `PM_FIN`, `COPIED`, `STOPPED_ROLE`, `MULTI_PART`, `HQ`, `PROJ`, `NO_SCOPE`, `REVOKABLE`, `MUTABLE`, `AGE89`, `AGE90`, `UPLOAD`, `DENIED`, `READ_ONLY`, `IDLE`, 선택 `ASYNC`. `REVOKABLE`의 유일 활성 역할과 `MUTABLE`/`UPLOAD`는 `fx147` 합성 식별자를 사용해야 하며 `DENIED`는 메뉴 허용이 없는 활성 합성 역할이어야 한다. `READ_ONLY`는 UI-02 INQ만 가지며 수정 동작이 없어야 한다. 서버 미사용 만료는 이 fixture 앱에서 1분으로 구성하고 AGE89/AGE90은 서버 서울 날짜 기준으로 준비한다. 설정/역할을 운영 원본에 덮어쓰면 안 된다.

기존 5개 browser suite의 업무 계산·마감·정산·취소 assertion은 보존하고 실제 로그인·ADMIN MFA·현재 서버 CSRF setup만 연결했다. 추가 route matrix는 실제 허용 메뉴 화면의 연결 smoke이며 계산 회귀를 대신하지 않는다. 저장 메뉴가 금지한 화면은 설정을 덮어쓰지 않고 skip/미검증으로 남긴다. trace/screenshot/video 및 storage-state credential 파일은 만들지 않는다.

## 미검증·제약

- AI 비동기 철회는 실제 지연 원천이 준비된 경우 `BASIC147_E2E_ASYNC_GATE_READY=true`로 실행한다. 준비되지 않은 외부 원천을 가짜 답변으로 대체하지 않으며 skip은 미검증이다.
- Git 메타데이터가 없는 frozen copy에서도 runner는 시험을 진행한다. 선택 `BASIC147_SOURCE_HEAD`는 형식만 검사한 제공 식별자로 기록하며 독립 확인한 HEAD나 정적 보존 증거로 바꾸지 않는다. 누락 증거가 있으면 결과는 INCOMPLETE다. SUITES_PASS도 기능별 독립 검증 완료와 구별한다.
- 단가 권한 없는 PM의 신규 후보 등록은 기존 필수 단가 원천 정책이 없어 `CANDIDATE_RATE_SOURCE_REQUIRED`로 차단되어 있다. 가짜 0/임의 단가를 만들지 않았으며 정상 등록 통합은 미완료 제약이다.
- 마지막 ADMIN 잠금은 `BASIC147_LAST_ADMIN_ISOLATED` 및 실제 단독 ADMIN 격리 DB가 필요하다. 일반 운영 ADMIN을 잠그지 않는다.
- 참고 HTML은 이전 조사에서 접근 실패/404, Windows 대안 파일은 부재였다. 시각·모바일 동등성은 확인되지 않았으며 검증자 browser 확인이 필요하다.
- 선행 08~17 통합은 문서 존재만으로 통과하지 않는다. 실제 구현·계산·저장 경로와 각 시험 결과를 검증자가 별도 확인해야 한다.
- migration/새 원장/비범위 password 갱신/감사 삭제의 추가 소스 정적 검사는 동적 보존 증거를 대신하지 않는다. 실제 fixture DB의 저장 설정·계좌 암호문·시퀀스·감사는 전후 재조회해야 한다.
- 협업 보고서의 과거 frozen snapshot 결함을 현재 코드와 대조하여 원인 수정 중이다. 이 문서는 제품 전수 검증 완료 선언이 아니다.
