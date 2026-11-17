import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  getCurrentUserPermissionSettings,
  type CurrentPermissionSettings,
  type MenuPermission,
} from "./permissionSettingsApi";

const actions: { key: keyof Omit<MenuPermission, "menuId">; label: string }[] =
  [
    { key: "inquire", label: "조회" },
    { key: "register", label: "등록" },
    { key: "update", label: "수정" },
    { key: "delete", label: "삭제" },
    { key: "upload", label: "업로드" },
    { key: "download", label: "다운로드" },
    { key: "confirm", label: "확정" },
  ];

export function PermissionSettingsPanel() {
  const [settings, setSettings] = useState<CurrentPermissionSettings>();
  const [error, setError] = useState<unknown>();
  const [pending, setPending] = useState(true);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    setPending(true);
    setError(undefined);
    getCurrentUserPermissionSettings()
      .then(
        (value) => {
          if (active) setSettings(value);
        },
        (reason) => {
          if (active) setError(reason);
        },
      )
      .finally(() => {
        if (active) setPending(false);
      });
    return () => {
      active = false;
    };
  }, [refresh]);
  return (
    <section
      aria-label="현재 사용자 저장 권한"
      data-screen-id="SCR-UI-24"
      data-testid="permission-settings-panel"
      aria-busy={pending}
    >
      <div className="sicost-toolbar">
        <h2>현재 사용자 저장 권한</h2>
        <button
          type="button"
          data-testid="permission-settings-refresh"
          disabled={pending}
          onClick={() => setRefresh((value) => value + 1)}
        >
          다시 조회
        </button>
      </div>
      <p className="sicost-banner sicost-tone-info" role="note">
        저장된 역할·메뉴 권한과 담당 범위를 적용합니다.
      </p>
      <p>
        활성 역할의 저장 설정 합집합입니다. 각 업무는 메뉴·동작·담당 범위와
        민감항목 자격을 서버에서 검사하며 연결이 완료되지 않은 경로는
        거부합니다.
      </p>
      {pending && <p role="status">저장 권한을 조회하고 있습니다.</p>}
      {error != null && (
        <>
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "저장 권한을 조회하지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
          />
          {error instanceof ApiError &&
            Object.entries(error.fieldMessages).map(([field, message]) => (
              <p key={field}>
                {field}: {message}
              </p>
            ))}
        </>
      )}
      {settings && !pending && error == null && (
        <>
          <dl>
            <dt>사용자</dt>
            <dd>{settings.userId}</dd>
            <dt>활성 역할</dt>
            <dd>
              {settings.roleIds.length
                ? settings.roleIds.join(" · ")
                : "활성 역할이 없습니다."}
            </dd>
            <dt>민감항목 보기</dt>
            <dd>
              <StatusBadge tone={settings.sensitiveView ? "info" : "neutral"}>
                {settings.sensitiveView ? "허용 설정" : "허용 안 함"}
              </StatusBadge>{" "}
              급여·지급단가·개인별원가·거래처 지급계좌 (실제 가림 미적용)
            </dd>
            <dt>저장 데이터범위</dt>
            <dd>
              {settings.rowScope.all ? (
                <StatusBadge tone="info">전체 (ALL)</StatusBadge>
              ) : (
                <>
                  <p>
                    담당파트 (PART):{" "}
                    {settings.rowScope.partIds.join(" · ") || "대상 없음 (0건)"}
                  </p>
                  <p>
                    담당프로젝트 (PROJ):{" "}
                    {settings.rowScope.projectIds.join(" · ") ||
                      "대상 없음 (0건)"}
                  </p>
                  <p>PART 행집합과 PROJ 행집합의 합집합</p>
                </>
              )}
            </dd>
            <dt>담당파트</dt>
            <dd>
              {settings.assignedParts.length
                ? settings.assignedParts.map((part) => (
                    <span
                      key={part.partId}
                      data-testid={`permission-part-${part.partId}`}
                    >
                      {part.name} ({part.partId}){" "}
                      {part.headOffice && (
                        <StatusBadge tone="neutral">본사</StatusBadge>
                      )}{" "}
                    </span>
                  ))
                : "담당파트가 없습니다."}
            </dd>
            <dt>담당프로젝트</dt>
            <dd>
              {settings.assignedProjects.length
                ? settings.assignedProjects.map((project) => (
                    <span
                      key={project.scopeSn}
                      data-testid={`permission-project-${project.scopeSn}`}
                    >
                      {project.projectId} (연결 {project.scopeSn}){" "}
                    </span>
                  ))
                : "담당프로젝트가 없습니다."}
            </dd>
            <dt>설정 revision</dt>
            <dd style={{ overflowWrap: "anywhere" }}>
              {settings.settingsRevision}
            </dd>
          </dl>
          <div className="sicost-table-viewport">
            <table
              className="sicost-table"
              aria-label="활성 역할 메뉴 7동작 합집합"
            >
              <thead>
                <tr>
                  <th scope="col">메뉴 ID</th>
                  {actions.map((action) => (
                    <th key={action.key} scope="col">
                      {action.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {settings.menuPermissions.map((permission) => (
                  <tr
                    key={permission.menuId}
                    data-testid={`permission-menu-${permission.menuId}`}
                  >
                    <th scope="row">{permission.menuId}</th>
                    {actions.map((action) => (
                      <td key={action.key}>
                        <StatusBadge
                          tone={permission[action.key] ? "ok" : "neutral"}
                        >
                          {permission[action.key] ? "허용" : "미허용"}
                        </StatusBadge>
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            {!settings.menuPermissions.length && (
              <p className="sicost-empty" role="status">
                저장된 메뉴 권한이 없습니다.
              </p>
            )}
          </div>
        </>
      )}
    </section>
  );
}
