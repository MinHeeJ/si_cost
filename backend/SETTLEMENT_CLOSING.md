# 정산의 후속 월 마감 연결

`SettlementClosingService`의 `listEligibleParts(month)`는 대상/확정/미확정 수와 파트별 문제를,
`previewSettlement(partId, month)`는 UI-19와 같은 전체 계산/사본을 반환한다.
탭에 확정 이력도 포함되므로 마감 대상은 `Tab.eligible=true`로 선택한다.
`confirmForClose(partId, month, actor)`는 호출자의 기존 트랜잭션이 필수다.
기존 카드와 같은 계산으로 AUTO를 만들며 기존 CNFM/AUTO는 덮어쓰지 않는다.
실패를 삼키지 말고 호출자의 전체 마감 트랜잭션을 롤백해야 한다.
이 기능을 실행하는 공개 API/배치/마감 화면은 추가하지 않았다.

잠금은 `SettlementCommandMapper.sourceKeys` → `SettlementSourceLocks`의
파트행/정산부모/기준행 → 기존 원천 advisory key 순이다.
원천 쓰기는 기존 `WriteCoordinator`를 통해 같은 잠금에 참여한다.

기존 POST `/api/exports/master-data`에 `SALES_PART_SETTLEMENTS`와
`RATE_RENEGOTIATIONS`만 추가했다. 입력 계약은 OpenAPI의 동명 ExportRequest와 같고,
정산 `filters.mode`는 MONTH/TREND다. 조회의 전체 결과를 같은 엑셀 snapshot 안에서 사용한다.
정산 이력은 기존 GET `/api/financial-history?scope=PART_SETTLEMENT&partId=...&month=...`를 쓴다.
