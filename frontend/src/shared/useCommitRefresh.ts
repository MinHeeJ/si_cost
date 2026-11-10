import { useRef, useState } from "react";

export const COMMITTED_REFRESH_FAILED =
  "저장은 완료됐지만 목록을 다시 불러오지 못했습니다. 다시 조회해 주세요.";
export function useCommitRefresh() {
  const active = useRef(false);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const commit = async (
    save: () => Promise<unknown>,
    refresh: () => Promise<unknown>,
  ) => {
    if (active.current) return;
    active.current = true;
    setPending(true);
    setMessage(null);
    try {
      await save();
      try {
        await refresh();
        setMessage("저장했습니다.");
      } catch {
        setMessage(COMMITTED_REFRESH_FAILED);
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
