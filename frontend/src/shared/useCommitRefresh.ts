import { useRef, useState } from "react";

export const COMMITTED_REFRESH_FAILED =
  "저장은 완료됐지만 목록을 다시 불러오지 못했습니다. 다시 조회해 주세요.";
export function useCommitRefresh(
  committedRefreshFailed = COMMITTED_REFRESH_FAILED,
) {
  const active = useRef(false);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
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
    if (!options.quiet) setMessage(null);
    try {
      await save();
      try {
        await refresh();
        if (!options.quiet) setMessage("저장했습니다.");
      } catch {
        setMessage(committedRefreshFailed);
      }
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "저장하지 못했습니다. 입력한 내용은 유지됩니다.",
      );
      throw error;
    } finally {
      active.current = false;
      setPending(false);
    }
  };
  return { pending, message, commit };
}
