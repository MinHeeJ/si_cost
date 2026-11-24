import { SharedScreenFrame } from "./FollowUpScreen";
import {
  ProjectStatusBoard,
  type ProjectStatusBoardProps,
} from "../apiops/ProjectStatusBoard";
import {
  ProjectStatusIssuesPanel,
  type StatusIssueDialogHost,
} from "../projectstatusboardops/ProjectStatusIssuesPanel";
import { ProjectAnalysisDownload } from "../projectstatusboardops/ProjectAnalysisDownload";

const statusBoardGuide = (
  <dl>
    <div>
      <dt>프로젝트 현황 조회</dt>
      <dd>
        진행 중이거나 수주 예정인 프로젝트의 진척률, 원가, 이슈를 확인할 수
        있습니다. 신호등, 계약유형, 영업 파트로 좁혀 보면 상단 요약도 같은
        조건으로 집계됩니다.
      </dd>
    </div>
    <div>
      <dt>신호등과 업데이트</dt>
      <dd>
        신호등은 일정 지연, 진척 대비 원가, 기한이 지난 이슈, 주간 메모
        경과일을 기준으로 표시됩니다. [14일 이내 업데이트가 없는 프로젝트]
        버튼을 누르면 프로젝트별 경과일을 확인할 수 있습니다. 주간 메모가
        없으면 등록일과 계약 시작일 중 늦은 날부터 셉니다. 세부 판정 기준은
        하단 [계산 방법]에서 확인하세요.
      </dd>
    </div>
    <div>
      <dt>상세 및 이슈 확인</dt>
      <dd>
        프로젝트명을 누르면 진행 현황을 볼 수 있습니다. [이슈 모아 보기]에서는
        여러 프로젝트의 이슈를 한곳에서 확인할 수 있습니다.
        선투입 프로젝트의 원가는 진행 현황에서 확인하세요.
      </dd>
    </div>
  </dl>
);

export function ProjectStatusBoardScreen({
  renderIssue,
  ...props
}: ProjectStatusBoardProps & { renderIssue?: StatusIssueDialogHost } = {}) {
  return (
    <SharedScreenFrame screenId="UI-31" screenGuide={statusBoardGuide}>
      <ProjectStatusBoard
        renderIssues={(_query, onChanged) => (
          <ProjectStatusIssuesPanel
            onChanged={onChanged}
            renderIssue={renderIssue}
          />
        )}
        renderExport={(query) => (
          <ProjectAnalysisDownload view="statusBoard" query={query} />
        )}
        {...props}
      />
    </SharedScreenFrame>
  );
}
