# 요구사항 15 서버 연결점

대시보드 `GET /api/dashboard?month=YYYY-MM` 및 `/export`는 기존 회사손익·원가·수급·현금 서비스를 읽는다.
선택월 Q와 오늘 D/현재월 M은 DTO의 basis로 구분한다. GET은 업무 원장을 생성하지 않는다.

알림 `/api/notifications`·`/unread-count`·`/{id}/read`·`/read-all`은 현재 주체의 SYS/BOTH만 취급한다.
`NotificationPublisher.publish`는 업무 트랜잭션에 MANDATORY로 참여하며 NOTI/SYS TASK/필수 감사를 함께 저장한다.
역할 기반은 활성 사용자·활성 역할, UPLD/AI/QUOTE 명시 수신자는 활성 사용자만 요구한다.
USER_ID로 중복 제거하고 실제 이름·주소 사본을 발행 시 저장한다.

업로드 `markApplied` 성공 1건은 `UploadNotificationInterceptor`에서 공통 발행을 호출한다.
검사/중복 APPLIED는 발행하지 않으며 알림 실패는 파일 업무·이력·APPLIED 전체를 롤백한다.
이벤트는 `UPLD:{id}:APPLIED`. 견적 기존 직접 INSERT는 `QuoteNotificationInterceptor`가 공통 발행으로 대체하며
`QUOTE:{oppId}:{실제 REJ histSn}:REJ`로 중복을 막는다. 요구16은 성공 예측 완료 트랜잭션에서만
`PredictionNotificationAdapter.predictionCompleted(qnaId, userId)`를 호출한다. 일반 질문/규칙 추천에서는 호출하지 않는다.

미수 `/api/receivables/{billId}/delivery-preview`, `/reminders`, `/deliveries`는 확인 revision/operationId를 사용한다.
수동은 `DUN:M:{billId}:{operationId}`/RESEND=Y, 자동은 `DUN:A:{billId}:{step}`/RESEND=N이다.
`DunningAutomationCoordinator`는 09시 조정자에 등록되며 현재 최고단계만 예약한다. HIST/TASK의 최고단계 이하를 반복하지 않는다.
청구 잠금 아래 HIST/TASK max+1을 예약하며 SYS와 MAIL이 같은 회차를 공유한다.
수동 전송은 afterCommit, 자동 전송은 예약 단계의 commit 후 후속 단계에서만 수행한다.
SMTP 실행 중 청구/금융 트랜잭션을 보유하지 않는다. SENDING commit 후 SMTP 접수 결과를 SENT/FAILED/UNKNOWN으로 남긴다.
UNKNOWN은 자동 재전송하지 않는다. 재시작은 중단 SENDING 복구만 하고 PENDING을 즉시 발송하지 않는다.

02시 DAILY_OPERATIONS와 09시 RECEIVABLE_DUNNING의 실행상태는 `/api/automation-runs`에서 조회한다.
검증용 run-today는 AUTOMATION_MANUAL_RUN_ENABLED=true일 때만 허용한다. 같은 날 SUCCEEDED 재실행은 무변경이다.
권한·행 범위는 요구18에서 현재 CurrentUserProvider/DTO 경계를 확장한다.

MAIL_ENABLED 기본 false, 나머지 SMTP 설정 및 개발 수집기 사용법은 `dev/README.md` 참조.
