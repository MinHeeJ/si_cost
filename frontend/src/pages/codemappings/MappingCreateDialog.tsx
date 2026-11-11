import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import { CodeSelect } from "../../shared/CodeSelect";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { Text } from "../../shared/Text";
import {
  getMappingCodes,
  type CreateMapping,
  type MappingCode,
  type MappingGroup,
} from "./codeMappingsApi";

export function MappingCreateDialog({
  groups,
  pending,
  onCancel,
  onConfirm,
}: {
  groups: MappingGroup[];
  pending: boolean;
  onCancel(): void;
  onConfirm(body: CreateMapping): Promise<void>;
}) {
  const [sourceText, setSourceText] = useState("");
  const [groupId, setGroupId] = useState("");
  const [code, setCode] = useState("");
  const [codes, setCodes] = useState<MappingCode[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<ApiError | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!groupId) return;
    let active = true;
    setLoading(true);
    setError(null);
    setCodes([]);
    void getMappingCodes(groupId)
      .then((data) => {
        if (active) setCodes(data.items);
      })
      .catch((e) => {
        if (active)
          setError(
            e instanceof Error ? e.message : "코드를 불러오지 못했습니다.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [groupId, attempt]);
  const submit = async () => {
    setSaveError(null);
    const next: Record<string, string> = {};
    if (!sourceText.trim() || [...sourceText.trim()].length > 100)
      next.sourceText = "엑셀 표기는 1~100자로 입력해 주세요.";
    if (!groupId) next.groupId = "그룹을 선택해 주세요.";
    if (!code || !codes.some((item) => item.code === code))
      next.code = "사용 중인 코드를 선택해 주세요.";
    setFields(next);
    if (Object.keys(next).length || loading || error) return;
    try {
      await onConfirm({ sourceText: sourceText.trim(), groupId, code });
    } catch (e) {
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        setSaveError(e);
      }
      throw e;
    }
  };
  return (
    <Dialog
      open
      title="엑셀 표기 추가"
      pending={pending}
      confirmLabel="추가"
      onCancel={onCancel}
      onConfirm={submit}
    >
      <div data-screen-id="SCR-UI-27-M5" className="sicost-mapping-fields">
        <Text
          label="엑셀 표기"
          required
          value={sourceText}
          onChange={setSourceText}
          maxLength={100}
          error={fields.sourceText}
          disabled={pending}
        />
        <CodeSelect
          label="코드 그룹"
          value={groupId}
          onChange={(value) => {
            setGroupId(value);
            setCode("");
            setCodes([]);
          }}
          options={groups.map((g) => ({ code: g.groupId, name: g.name }))}
          disabled={pending}
          error={fields.groupId}
        />
        <CodeSelect
          label="바꿀 코드"
          value={code}
          onChange={setCode}
          options={codes.map((c) => ({
            code: c.code,
            name: `${c.code} · ${c.name}`,
          }))}
          disabled={pending || loading || !groupId || Boolean(error)}
          error={fields.code}
        />
        {loading && <p role="status">코드를 불러오는 중입니다.</p>}
        {error && (
          <ErrorBanner
            message={error}
            onRetry={() => setAttempt((v) => v + 1)}
          />
        )}
        {saveError && (
          <ErrorBanner
            message={saveError.message}
            requestId={saveError.body.requestId}
          />
        )}
        <p>
          필수 항목을 입력해 주세요. 같은 표기와 그룹은 중복 등록할 수 없습니다.
        </p>
      </div>
    </Dialog>
  );
}
