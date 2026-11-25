import { useRef, useState } from "react";

export const COMMITTED_REFRESH_FAILED =
  "저장은 완료됐지만 목록을 다시 불러오지 못했습니다. 다시 조회해 주세요.";
type CommitOutcome = {
  kind: "saved" | "refresh-failed" | "save-failed";
  message: string;
};

export function useCommitRefresh(
  committedRefreshFailed = COMMITTED_REFRESH_FAILED,
) {
  const active = useRef(false);
  const [pending, setPending] = useState(false);
  const [outcome, setOutcome] = useState<CommitOutcome | null>(null);
  /**
   * quiet: 사용자가 누르지 않은 자동 저장(예: 최근 본 화면 기록)이다. 성공해도 "저장했습니다."를
   * 띄우지 않고 앞선 메시지도 지우지 않는다. 실패는 그대로 알린다.
   */
  const commit = async (
    save: () => Promise<unknown>,
    refresh: () => Promise<unknown>,
    options: { quiet?: boolean } = {},
  ) => {
    if (active.current) return;
    active.current = true;
    setPending(true);
    if (!options.quiet) setOutcome(null);
    try {
      await save();
      try {
        await refresh();
        if (!options.quiet) {
          setOutcome({ kind: "saved", message: "저장했습니다." });
        }
      } catch {
        setOutcome({ kind: "refresh-failed", message: committedRefreshFailed });
      }
    } catch (error) {
      setOutcome({
        kind: "save-failed",
        message: error instanceof Error
          ? error.message
          : "저장하지 못했습니다. 입력한 내용은 유지됩니다.",
      });
      throw error;
    } finally {
      active.current = false;
      setPending(false);
    }
  };
  return {
    pending,
    message: outcome?.message ?? null,
    outcome,
    clearOutcome: () => setOutcome(null),
    commit,
  };
}
