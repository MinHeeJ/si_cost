# 공통 UI 관찰·적용 기록

공통 부품 체크리스트: 업무 route를 등록하지 않는 shared 모듈이다. 부모가 rows/columns/CTA/readonly/disabled/오류/성공 결과를 전달한다. 원문 저장 문자열은 표시/정렬이 바꾸지 않으며 모달은 pending 차단·첫 오류 포커스·trap/복원을 제공한다. 저장 후 재조회 실패와 저장 실패는 구분하며 자동 재저장은 없다.

지정 probe의 시스템 → 공통코드 및 그룹 추가 경로로 desktop 1280x800을 관찰했다. 입력 요청에 첨부된 동일 크기 이미지를 함께 기준으로 삼았다. desktop 결과는 확보했지만 tablet/mobile은 PC 전용 화면 클릭 timeout으로 관찰되지 않았다. probe의 전체 exit code는 그 이유로 1이며 화면 전체가 접속 실패한 것은 아니다.

관찰: header 56px, sidebar 240px, 본문 x=240, 제목 20px/700, table header/body 13px·40px, cell padding 12px, inline input 28px·border #D1D5DB·radius4, 그룹 card 260px·padding8·radius8·border #E5E7EB.
색: primary #1D4ED8, canvas #F9FAFB, surface #FFFFFF, head/lock #F3F4F6, muted #4B5563. 배지는 ok=녹색, warn=호박색, bad=적색, info=하늘색, lock=회색, neutral=투명 테두리로 구분한다. empty는 중앙 48px 16px이며 banner는 10px 16px/radius8이다.

적용: frontend/src/shared/common.css는 위 토큰/표 밀도/배너/빈 목록/폼/모달 치수와 focus를 사용한다. 외부 폰트 다운로드·CDN·iframe·레퍼런스 마크업/시연 데이터 복사는 없다. DataTable·controlled 입력·Dialog·Feedback을 화면 담당자가 재사용할 수 있다.

foundation 소유 진입점은 실제 DB 연결 상태만 보여 주는 bootstrap이다. 제품 레이아웃·공식 화면 등록은 병합 담당자 소유이며 공유 부품은 그 레이아웃을 덮어쓰지 않는다. 라우트·UI-27의 업무 모달을 등록하는 기능은 중복 생성하지 않는다.
