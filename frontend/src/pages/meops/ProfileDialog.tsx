import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { getMyProfile, type MyProfile } from "./profileApi";
import "./profile.css";

export function ProfileDialog({
  open,
  onClose,
}: {
  open: boolean;
  onClose(): void;
}) {
  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [loading, setLoading] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    if (!open) return;
    let live = true;
    setLoading(true);
    setProfile(null);
    setFailure(null);
    void getMyProfile()
      .then((data) => {
        if (live) setProfile(data);
      })
      .catch((error: unknown) => {
        if (live) setFailure(error);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [open, reload]);
  return (
    <Dialog
      open={open}
      title="내 정보"
      onCancel={onClose}
      onConfirm={onClose}
      confirmLabel="닫기"
      hideCancel
      testId="profile-dialog"
      confirmTestId="profile-close-button"
    >
      <section
        data-screen-id="SCR-UI-16-M4"
        data-testid="profile-content"
        className="profile-content"
        aria-busy={loading}
      >
        {loading && <p role="status">내 정보를 불러오는 중…</p>}
        {failure != null && (
          <ErrorBanner
            message={
              failure instanceof Error
                ? failure.message
                : "내 정보를 불러오지 못했습니다."
            }
            requestId={
              failure instanceof ApiError ? failure.body.requestId : undefined
            }
            onRetry={() => setReload((value) => value + 1)}
            retryTestId="profile-retry-button"
          />
        )}
        {!loading && profile && (
          <>
            <dl className="profile-fields">
              <div>
                <dt>이름</dt>
                <dd>{profile.userName}</dd>
              </div>
              <div>
                <dt>아이디</dt>
                <dd>{profile.userId}</dd>
              </div>
              <div>
                <dt>활성 역할</dt>
                <dd>
                  {profile.activeRoles.length
                    ? profile.activeRoles.map((role, index) => (
                        <span
                          data-testid={`profile-role-${index}`}
                          key={role.roleId}
                        >
                          {role.roleName}
                        </span>
                      ))
                    : "–"}
                </dd>
              </div>
              <div>
                <dt>소속 파트</dt>
                <dd>
                  {profile.parts.length
                    ? profile.parts.map((part, index) => (
                        <span
                          data-testid={`profile-part-${index}`}
                          key={part.partId}
                        >
                          {part.partName}
                        </span>
                      ))
                    : "–"}
                </dd>
              </div>
              <div>
                <dt>기준월</dt>
                <dd>{profile.basisMonth}</dd>
              </div>
            </dl>
            <p className="sicost-banner sicost-tone-info">
              {profile.editGuidance}
            </p>
          </>
        )}
      </section>
    </Dialog>
  );
}

export function ProfileMenu() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        data-testid="profile-menu-button"
        onClick={() => setOpen(true)}
      >
        내 정보
      </button>
      <ProfileDialog open={open} onClose={() => setOpen(false)} />
    </>
  );
}
