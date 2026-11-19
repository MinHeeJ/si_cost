import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import {
  UserProjectDialog,
  type UserProjectChoice,
} from "../businessusersops/UserProjectDialog";
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
  const [roles, setRoles] = useState<string[]>(
    user?.roleIds ?? user?.businessRoleIds ?? [initialRole],
  );
  const [options, setOptions] = useState<{ roleId: string; name: string }[]>(
      [],
    ),
    [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false),
    [error, setError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({}),
    [discard, setDiscard] = useState(false);
  const [headOffice, setHeadOffice] = useState(user?.headOffice ?? false);
  const [projects, setProjects] = useState<
    (UserProjectChoice & { confirmed: boolean })[]
  >([]);
  const [choosingProject, setChoosingProject] = useState(false);
  const saving = useRef(false);
  const dirty =
    headOffice !== (user?.headOffice ?? false) ||
    projects.length > 0 ||
    userId !== (user?.userId ?? "") ||
    name !== (user?.name ?? "") ||
    email !== (user?.email ?? "") ||
    [...roles].sort().join(",") !==
      [...(user?.roleIds ?? user?.businessRoleIds ?? [initialRole])]
        .sort()
        .join(",");
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
      setOptions(await api.getManagedUserRoleOptions());
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
    if (!roles.length) next.roleIds = "역할을 하나 이상 선택해 주세요.";
    setFields(next);
    if (Object.keys(next).length) return;
    saving.current = true;
    setPending(true);
    setError(undefined);
    try {
      const body = {
        name: name.trim(),
        email: email.trim(),
        roleIds: roles,
        headOffice,
      };
      const result = user
        ? await api.putBusinessUser(user.userId, {
            ...body,
            expectedRevision: user.revision,
          })
        : await api.createBusinessUser({
            ...body,
            userId: userId.trim(),
            projectAssignments: (roles.includes("PM") ? projects : []).map(
              ({ project, pm, confirmed }) => ({
                projectId: project.id,
                expectedRevision: pm.revision,
                replaceConfirmed: confirmed,
              }),
            ),
          });
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
        open={!choosingProject}
        permission={{ screen: "UI-23", action: user ? "UPD" : "REG" }}
        title={user ? "사용자 수정" : "사용자 등록"}
        pending={pending}
        confirmDisabled={loading || !options.length}
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
            <legend>역할</legend>
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
            {(fields.roleIds || fields.businessRoleIds) && (
              <p role="alert" className="sicost-field-error">
                {fields.roleIds || fields.businessRoleIds}
              </p>
            )}
          </fieldset>
          <label className="sicost-field">
            담당파트
            <FieldHelp label="담당파트" /><select
              data-testid="business-user-head-office"
              value={headOffice ? "HQ" : ""}
              disabled={pending}
              onChange={(e) => setHeadOffice(e.target.value === "HQ")}
            >
              <option value="">–</option>
              <option value="HQ">본사(직접 영업)</option>
            </select>
          </label>
          <p>
            영업1·영업2 같은 영업 파트의 파트장은 기준정보 › 거래처·영업
            파트에서 정합니다.
          </p>
          {!user && roles.includes("PM") && (
            <fieldset disabled={pending}>
              <legend>담당프로젝트</legend>
              {projects.map((item) => (
                <p
                  data-testid={`business-user-project-${item.project.id.toLowerCase().replace(/_/g, "-")}`}
                  key={item.project.id}
                >
                  {item.project.name} ({item.project.id}){" "}
                  <button
                    data-testid={`business-user-project-remove-${item.project.id.toLowerCase().replace(/_/g, "-")}`}
                    type="button"
                    onClick={() =>
                      setProjects((v) =>
                        v.filter((p) => p.project.id !== item.project.id),
                      )
                    }
                  >
                    선택 해제
                  </button>
                </p>
              ))}
              <button
                data-testid="business-user-project-add"
                type="button"
                onClick={() => setChoosingProject(true)}
              >
                담당 프로젝트 추가
              </button>
            </fieldset>
          )}
          <p className="sicost-banner sicost-tone-info">
            로그인용 비밀번호 발급은 인증 기능 적용 시 제공합니다.
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
      {choosingProject && (
        <UserProjectDialog
          creating
          user={{
            userId: userId.trim(),
            name,
            email,
            roles: [],
            businessRoleIds: ["PM"],
            status: "USE",
            lastLoginAt: null,
            revision: "",
          }}
          pending={pending}
          onClose={() => setChoosingProject(false)}
          onAssign={async (choice, confirmed) => {
            setProjects((v) => [
              ...v.filter((p) => p.project.id !== choice.project.id),
              { ...choice, confirmed },
            ]);
            setChoosingProject(false);
          }}
        />
      )}
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
