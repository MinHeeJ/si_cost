export { DeveloperPoolScreen } from "../developers/DeveloperPoolScreen";

export const developerPoolGuide = (
  <dl>
    <div>
      <dt>개발자 조회</dt>
      <dd>
        이름이나 관리번호를 입력하거나 직원구분, 직무, 상태 등의 조건을
        선택한 뒤 [조회]를 누르세요. 중지된 개발자도 함께 보려면
        ‘사용 중지 포함’을 체크하세요.
      </dd>
    </div>
    <div>
      <dt>상세 정보와 이력</dt>
      <dd>
        목록에서 이름을 누르면 기본 정보와 투입 이력, 근무 상태 이력,
        변경 이력을 확인할 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>개발자 등록</dt>
      <dd>
        한 명은 [등록]에서 추가할 수 있습니다. 여러 명을 등록할 때는 PC에서
        [양식 내려받기]로 받은 파일을 작성한 뒤 [엑셀 업로드]를 사용하세요.
      </dd>
    </div>
    <div>
      <dt>월별 급여</dt>
      <dd>
        [월별 급여]에서 연도와 월을 선택해 급여 내역을 확인할 수 있습니다.
        급여 자료를 올릴 때는 PC에서 [급여 양식 내려받기]로 받은 파일을 사용하세요.
      </dd>
    </div>
  </dl>
);
