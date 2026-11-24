import { useLocation } from "react-router-dom";
import { StaffingAssignmentsScreen as Workspace, type StaffingAssignmentsProps } from "../assignments/StaffingAssignmentsScreen";

export const assignmentScreenGuide = (
  <dl>
    <div>
      <dt>투입 배정 조회</dt>
      <dd>
        연도를 선택하고 프로젝트, 직무, 경고 또는 이름으로 배정을 찾을 수
        있습니다. 특정 월만 보려면 [추가 조건]에서 선택 월을 지정하고
        [조회]를 누르세요.
      </dd>
    </div>
    <div>
      <dt>배정 후보</dt>
      <dd>
        영업 기회에 가배정한 인력은 해당 기회가 수주되면 배정 후보에
        표시됩니다. [확정]으로 배정 정보를 확인한 뒤 저장하거나, [제외]로
        후보 목록에서 뺄 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>엑셀 업로드 필수 입력</dt>
      <dd>
        [엑셀 업로드] 창의 [양식 내려받기]로 받은 파일의 첫 시트에
        프로젝트ID, 관리번호, 직무,
        투입일, 철수일, 투입 구분을 입력하세요. 관리번호는 앞자리 0을
        보존하도록 텍스트로 입력하고, 날짜는 2026-12-01 형식으로 입력하세요.
        대체투입은 같은 프로젝트에 배정된 다른 계약상 인력의 관리번호도 필요합니다.
      </dd>
    </div>
    <div>
      <dt>엑셀 업로드 선택 입력</dt>
      <dd>
        월 MM은 0.01~1.00 범위에서 소수 둘째 자리까지 입력할 수 있으며 빈칸은
        1.00으로 적용됩니다. 청구 단가는 유상 월단위·파견 배정에만 원 단위
        정수로 입력하세요. 연장 가능성은 높음·보통·낮음 중에서 선택하며
        빈칸은 미정으로 처리됩니다. 업로드는 신규 배정에 사용하고, 기존
        배정의 수정은 상세의 변경 기록에서 진행하세요.
      </dd>
    </div>
  </dl>
);

export function StaffingAssignmentsScreen(props: StaffingAssignmentsProps = {}) {
  const location = useLocation();
  const query = new URLSearchParams(location.search);
  const ids = query.getAll("assignmentId");
  const id = location.pathname === "/UI-12" && ids.length === 1 && ids[0].length > 0 && ids[0].length <= 20 ? ids[0] : undefined;
  return <Workspace {...props} entryAssignmentId={id} entryHistory={query.get("tab") === "history"} />;
}
