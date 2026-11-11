import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { Text } from "../../shared/Text";
import { ApiError } from "../../shared/api";
import * as api from "./businessUsersApi";

export function BusinessUserForm({
  user,
  initialRole = "PM",
  onClose,
  onSaved,
  onDirtyChange,
}: {
  initialRole?: api.BusinessRole;
  user?: api.BusinessUser;
  onClose(): void;
  onSaved(result: api.BusinessUserSaveResult): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [userId, setUserId] = useState(user?.userId ?? "");
  const [name, setName] = useState(user?.name ?? ""),
    [email, setEmail] = useState(user?.email ?? "");
  const [roles, setRoles] = useState<api.BusinessRole[]>(
    user?.businessRoleIds ?? [initialRole],
  );
  const [options, setOptions] = useState<api.BusinessRoleOption[]>([]),
    [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false),
    [error, setError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({}),
    [discard, setDiscard] = useState(false);
  const saving = useRef(false);
  const dirty =
    userId !== (user?.userId ?? "") ||
    name !== (user?.name ?? "") ||
    email !== (user?.email ?? "") ||
    [...roles].sort().join(",") !==
      [...(user?.businessRoleIds ?? [initialRole])].sort().join(",");
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const leave = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", leave);
    return () => window.removeEventListener("beforeunload", leave);
  }, [dirty]);
  async function loadOptions() {
    setLoading(true);
    setError(undefined);
    try {
      setOptions((await api.getBusinessRoleOptions()).roleOptions);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void loadOptions();
  }, []);
  function close() {
    if (pending) return;
    if (dirty) setDiscard(true);
    else onClose();
  }
  async function save() {
    if (saving.current || loading) return;
    const next: Record<string, string> = {};
    if (!user && !/^[a-z0-9]{4,20}$/.test(userId.trim()))
      next.userId = "아이디는 영문 소문자·숫자 4~20자로 입력해 주세요.";
    if (!name.trim() || Array.from(name.trim()).length > 50)
      next.name = "이름은 1~50자로 입력해 주세요.";
    if (
      email.trim().length > 100 ||
      !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())
    )
      next.email = "올바른 이메일을 입력해 주세요.";
    if (!user && !roles.length)
      next.businessRoleIds = "업무 역할을 하나 이상 선택해 주세요.";
    setFields(next);
    if (Object.keys(next).length) return;
    saving.current = true;
    setPending(true);
    setError(undefined);
    try {
      const body = {
        name: name.trim(),
        email: email.trim(),
        businessRoleIds: roles,
      };
      const result = user
        ? await api.putBusinessUser(user.userId, {
            ...body,
            expectedRevision: user.revision,
          })
        : await api.createBusinessUser({ ...body, userId: userId.trim() });
      onDirtyChange?.(false);
      await onSaved(result);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
  return (
    <>
      <Dialog
        open
        title={user ? "사용자 수정" : "사용자 등록"}
        pending={pending}
        onCancel={close}
        onConfirm={save}
        confirmLabel="저장"
      >
        <div className="businessusers-form" data-testid="business-user-form">
          <Text
            label="아이디"
            data-testid="business-user-id"
            value={userId}
            onChange={setUserId}
            readOnly={Boolean(user)}
            maxLength={20}
            error={fields.userId}
          />
          <Text
            label="이름"
            data-testid="business-user-name"
            value={name}
            onChange={setName}
            maxLength={50}
            error={fields.name}
          />
          <Text
            label="이메일"
            data-testid="business-user-email"
            value={email}
            onChange={setEmail}
            maxLength={100}
            error={fields.email}
          />
          <fieldset disabled={pending || loading}>
            <legend>업무 역할</legend>
            {loading ? (
              <p role="status">역할을 불러오는 중입니다.</p>
            ) : (
              options.map((option) => (
                <label
                  data-testid={`business-user-role-label-${option.roleId.toLowerCase().replace(/_/g, "-")}`}
                  key={option.roleId}
                >
                  <input
                    type="checkbox"
                    data-testid={`business-user-role-${option.roleId.toLowerCase().replace("_", "-")}`}
                    checked={roles.includes(option.roleId)}
                    onChange={(e) =>
                      setRoles((previous) =>
                        e.target.checked
                          ? [...previous, option.roleId]
                          : previous.filter((id) => id !== option.roleId),
                      )
                    }
                  />
                  {option.name}
                </label>
              ))
            )}
            {fields.businessRoleIds && (
              <p role="alert" className="sicost-field-error">
                {fields.businessRoleIds}
              </p>
            )}
          </fieldset>
          <p className="sicost-banner sicost-tone-info">
            PM·파트장은 업무 자격입니다. 상태·비밀번호·인증 정보와 다른 역할은
            변경하지 않습니다.
          </p>
          {user && <p>전체 표시 역할: {user.roles.join(", ")}</p>}
          {error != null && (
            <div role="alert" className="sicost-banner sicost-tone-bad">
              {error instanceof Error ? error.message : "처리하지 못했습니다."}
              {error instanceof ApiError && (
                <span>요청 ID: {error.body.requestId}</span>
              )}
              <button
                type="button"
                data-testid="business-user-options-retry"
                onClick={() => void loadOptions()}
                disabled={pending}
              >
                역할 다시 조회
              </button>
            </div>
          )}
        </div>
      </Dialog>
      <Dialog
        open={discard}
        title="저장하지 않은 내용이 있습니다."
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setDiscard(false)}
        onConfirm={onClose}
      >
        <p>입력한 내용을 저장하지 않고 나가시겠습니까?</p>
      </Dialog>
    </>
  );
}
