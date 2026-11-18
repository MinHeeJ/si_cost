import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ScreenPermissionContext } from "../../shared/permission/PermissionBoundary";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { Icon } from "../../shared/Icon";
import { BrandMark } from "../../shared/BrandMark";
import { HeaderActions } from "./HeaderActions";
import { WorkspaceUsability } from "./WorkspaceUsability";
import { ProfileDialog } from "../meops/ProfileDialog";
import {
  getMenuManagement,
  type ManagedMenuTree,
} from "../menumanagement/menuManagementApi";
import {
  notificationDestination,
  type NotificationUnreadCount,
} from "../notificationsops/notificationsApi";
import { flattenMenus, SharedScreenContext } from "./SharedScreenContext";
import { screenRegistry } from "./screenRegistry";
import {
  deleteSharedPreference,
  getCurrentUser,
  getPeriodContext,
  getMenus,
  getSharedPreferences,
  putSharedPreference,
  type CurrentUser,
  type MenusData,
  type SharedPreference,
  type PeriodContext,
} from "./sharedApi";
import "../../shared/common.css";
import "./shared.css";
import { AnalysisAssistantProvider } from "./AnalysisAssistantContext";
export type SharedPreferenceAdapter = {
  items: SharedPreference[];
  loading: boolean;
  pending: boolean;
  error?: unknown;
  retry?(): void;
  save(type: string, key: string, value: unknown): Promise<void>;
  reset(type: string, key: string): Promise<void>;
};
export type SharedApplicationProps = {
  screens?: Partial<Record<string, ReactNode>>;
  codes?: ReactNode;
  mappings?: ReactNode;
  standards?: ReactNode;
  settings?: ReactNode;
  preferences?: SharedPreferenceAdapter;
  renderSettings?(open: boolean, onClose: () => void): ReactNode;
  isDirty?(screenId: string): boolean;
};
export function SharedRoutes() {
  const { pathname } = useLocation(),
    Component = screenRegistry[pathname.slice(1)]?.component;
  return Component ? (
    <Component />
  ) : (
    <div role="alert">등록되지 않은 화면입니다.</div>
  );
}
export function SharedApplication(props: SharedApplicationProps) {
  return (
    <AnalysisAssistantProvider>
      <SharedApplicationContent {...props} />
    </AnalysisAssistantProvider>
  );
}
function SharedApplicationContent({
  screens,
  codes,
  mappings,
  standards,
  settings,
  preferences,
  renderSettings,
  isDirty,
}: SharedApplicationProps) {
  const [utility, setUtility] = useState<"search" | "help" | null>(null);
  const [basis, setBasis] = useState<PeriodContext>();
  useEffect(() => {
    let live = true;
    const read = () => {
      void Promise.resolve(getPeriodContext())
        .then((value) => {
          if (live) setBasis(value);
        })
        .catch(() => {
          if (live) setBasis(undefined);
        });
    };
    read();
    window.addEventListener("focus", read);
    window.addEventListener("sicost:month-close-committed", read);
    return () => {
      live = false;
      window.removeEventListener("focus", read);
      window.removeEventListener("sicost:month-close-committed", read);
    };
  }, []);
  useEffect(() => {
    const open = () => setUtility("search");
    window.addEventListener("sicost:menu-search", open);
    return () => window.removeEventListener("sicost:menu-search", open);
  }, []);
  const location = useLocation(),
    navigate = useNavigate();
  const [menus, setMenus] = useState<MenusData | null>(null),
    [user, setUser] = useState<CurrentUser | null>(null),
    [localPrefs, setPrefs] = useState<SharedPreference[]>([]);
  const [management, setManagement] = useState<ManagedMenuTree | null>(null);
  const loadedMenus = useRef<MenusData | null>(null);
  const [menuDeparture, setMenuDeparture] = useState<(() => void) | null>(null);
  const [localPrefsReady, setPrefsReady] = useState(false);
  const [notificationBadges, setNotificationBadges] =
    useState<NotificationUnreadCount>();
  const prefs = preferences?.items ?? localPrefs,
    prefsReady = preferences ? !preferences.loading : localPrefsReady;
  const [error, setError] = useState(""),
    [preferenceError, setPreferenceError] = useState(""),
    [pending, setPending] = useState(false),
    [reload, setReload] = useState(0);
  const [folded, setFolded] = useState(() => window.innerWidth < 1280),
    [mobile, setMobile] = useState(() => window.innerWidth < 768),
    [mobileOpen, setMobileOpen] = useState(false),
    [groups, setGroups] = useState<string[]>([]);
  const [tabs, setTabs] = useState<string[]>(["UI-16"]),
    [dialog, setDialog] = useState<"settings" | "favorites" | "profile" | null>(
      null,
    ),
    [userMenu, setUserMenu] = useState(false);
  const userButton = useRef<HTMLButtonElement>(null);
  const saving = useRef(false),
    recentActivation = useRef(""),
    lru = useRef<string[]>([]);
  const nodes = flattenMenus(menus?.items ?? []).filter(
    (node) => node.screenId && screenRegistry[node.screenId],
  );
  const byScreen = (id: string) => nodes.find((node) => node.screenId === id);
  const getPref = (type: string) =>
    prefs.find((item) => item.type === type && item.key === "*");
  const storedIds = (type: string): string[] => {
    const value = getPref(type)?.value;
    return Array.isArray(value)
      ? value.filter((id): id is string => typeof id === "string")
      : [];
  };
  const ids = (type: string) => {
    const value = getPref(type)?.value;
    return Array.isArray(value)
      ? value.filter(
          (id): id is string => typeof id === "string" && Boolean(byScreen(id)),
        )
      : [];
  };
  const favorites = ids("FAV"),
    recent = ids("RECENT");
  const active = location.pathname.slice(1),
    current = byScreen(active);
  const visibleOnDevice = (id: string) => !mobile || !byScreen(id)?.pcOnly;
  const registered = management?.items.find((row) => row.screenId === active);
  const restriction =
    registered && !registered.use
      ? "사용 중지된 메뉴입니다."
      : mobile && (registered?.pcOnly ?? current?.pcOnly)
        ? "PC에서 이용해 주세요. PC전용 메뉴입니다."
        : "";
  useEffect(() => {
    const refresh = () => setReload((n) => n + 1);
    window.addEventListener("sicost:configuration-committed", refresh);
    return () =>
      window.removeEventListener("sicost:configuration-committed", refresh);
  }, []);
  useEffect(() => {
    const resize = () => setMobile(window.innerWidth < 768);
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setUserMenu(false);
        setMobileOpen(false);
      }
    };
    window.addEventListener("resize", resize);
    window.addEventListener("keydown", escape);
    return () => {
      window.removeEventListener("resize", resize);
      window.removeEventListener("keydown", escape);
    };
  }, []);
  useEffect(() => {
    let live = true;
    setError("");
    void Promise.all([getMenus(), getCurrentUser(), Promise.resolve(null)])
      .then(([menuData, actor, metadata]) => {
        if (live) {
          setUser(actor);
          const nextNodes = flattenMenus(menuData.items).filter(
            (row) =>
              row.screenId &&
              screenRegistry[row.screenId] &&
              (!mobile || !row.pcOnly),
          );
          const wasOpen = flattenMenus(loadedMenus.current?.items ?? []).some(
            (row) => row.screenId === active,
          );
          const unavailable =
            wasOpen && !nextNodes.some((row) => row.screenId === active);
          const apply = () => {
            loadedMenus.current = menuData;
            setMenus(menuData);
            setManagement(metadata);
            if (unavailable && nextNodes[0]?.screenId)
              navigate(`/${nextNodes[0].screenId}`, { replace: true });
          };
          if (unavailable) {
            const event = new CustomEvent("sicost:screen-navigation-request", {
              cancelable: true,
              detail: {
                screenId: active,
                label: "메뉴 사용·PC 설정 변경으로 화면 나가기",
                proceed: apply,
              },
            });
            if (!window.dispatchEvent(event)) return;
            if (isDirty?.(active)) {
              setMenuDeparture(() => apply);
              return;
            }
          }
          apply();
        }
      })
      .catch((e: unknown) => {
        if (live)
          (loadedMenus.current ? setPreferenceError : setError)(
            e instanceof ApiError
              ? `${e.message} 문의 번호: ${e.body.requestId}`
              : e instanceof Error
                ? e.message
                : "메뉴 조회 실패",
          );
      });
    if (!preferences)
      void getSharedPreferences()
        .then((result) => {
          if (live) {
            setPrefs(result.items);
            setPrefsReady(true);
            const fold = result.items.find(
              (item) => item.type === "MENU_FOLD" && item.key === "*",
            );
            if (typeof fold?.value === "boolean") setFolded(fold.value);
          }
        })
        .catch((e: unknown) => {
          if (live)
            setPreferenceError(
              e instanceof Error ? e.message : "설정 조회 실패",
            );
        });
    return () => {
      live = false;
    };
  }, [reload]);
  useEffect(() => {
    if (preferences && !preferences.loading) {
      const value = preferences.items.find(
        (item) => item.type === "MENU_FOLD" && item.key === "*",
      )?.value;
      setFolded(typeof value === "boolean" ? value : window.innerWidth < 1280);
    }
  }, [preferences?.items, preferences?.loading]);
  const savePreference = async (type: string, value: unknown) => {
    if (saving.current || !prefsReady || preferences?.pending) return false;
    if ((type === "FAV" || type === "RECENT") && Array.isArray(value)) {
      const hidden = storedIds(type).filter((id) => !byScreen(id));
      const visible = value.filter(
        (id): id is string => typeof id === "string" && !hidden.includes(id),
      );
      value = [
        ...visible.slice(0, (type === "FAV" ? 10 : 5) - hidden.length),
        ...hidden,
      ];
    }
    saving.current = true;
    setPending(true);
    setPreferenceError("");
    try {
      if (preferences) await preferences.save(type, "*", value);
      else {
        const result = await putSharedPreference(
          type,
          "*",
          value,
          getPref(type)?.revision ?? null,
        );
        setPrefs((previous) => [
          ...previous.filter(
            (item) => !(item.type === type && item.key === "*"),
          ),
          result.preference,
        ]);
      }
      return true;
    } catch (e) {
      setPreferenceError(
        e instanceof ApiError
          ? `${e.message} 문의 번호: ${e.body.requestId}`
          : e instanceof Error
            ? e.message
            : "설정 저장 실패",
      );
      return false;
    } finally {
      saving.current = false;
      setPending(false);
    }
  };
  useEffect(() => {
    if (!menus || !nodes.length) return;
    const hash = location.hash.match(/^#(UI-\d{2})$/)?.[1];
    const candidate = hash ?? (location.pathname === "/" ? "UI-16" : active);
    if (
      screenRegistry[candidate] &&
      (!byScreen(candidate) || !visibleOnDevice(candidate))
    )
      return;
    const valid = byScreen(candidate)
      ? candidate
      : byScreen("UI-16")
        ? "UI-16"
        : nodes[0].screenId!;
    if (location.pathname !== `/${valid}` || location.hash) {
      navigate(`/${valid}`, { replace: true });
      return;
    }
    setGroups((previous) =>
      current?.parentId && !previous.includes(current.parentId)
        ? [...previous, current.parentId]
        : previous,
    );
    setTabs((previous) => {
      if (previous.includes(valid)) return previous;
      return [...previous, valid];
    });
    lru.current = [...lru.current.filter((id) => id !== valid), valid];
    setMobileOpen(false);
    if (
      recentActivation.current !== location.key &&
      prefsReady &&
      !getPref("RECENT")?.error
    ) {
      recentActivation.current = location.key;
      void savePreference(
        "RECENT",
        [valid, ...recent.filter((id) => id !== valid)].slice(0, 5),
      );
    }
  }, [
    menus,
    location.pathname,
    location.hash,
    location.key,
    prefsReady,
    mobile,
  ]);
  const go = (
    id: string,
    financialConfirmed = false,
    state?: unknown,
  ): boolean | undefined => {
    if (!byScreen(id) || !visibleOnDevice(id)) return;
    if (
      !financialConfirmed &&
      ["UI-10", "UI-19", "UI-25"].includes(active) &&
      id !== active &&
      !window.dispatchEvent(
        new CustomEvent("sicost:screen-navigation-request", {
          cancelable: true,
          detail: {
            screenId: active,
            label: "다른 화면으로 이동",
            proceed: () => go(id, true, state),
          },
        }),
      )
    )
      return;
    if (
      active === "UI-24" &&
      id !== active &&
      isDirty?.(active) &&
      !window.confirm("저장하지 않은 변경을 버릴까요?")
    )
      return;
    if (!tabs.includes(id) && tabs.length >= 8) {
      const oldest =
        lru.current.find((item) => item !== "UI-16" && item !== active) ??
        tabs.find((item) => item !== "UI-16");
      if (
        !oldest ||
        !window.confirm(
          `${byScreen(oldest)?.name ?? oldest} 화면을 닫고 이동할까요?`,
        )
      )
        return;
      if (
        oldest !== "UI-10" &&
        isDirty?.(oldest) &&
        !window.confirm("저장하지 않은 변경을 버릴까요?")
      )
        return;
      setTabs((previous) => previous.filter((item) => item !== oldest));
    }
    navigate(`/${id}`, { state });
    return true;
  };
  const close = (id: string, financialConfirmed = false) => {
    if (
      !financialConfirmed &&
      ["UI-10", "UI-19", "UI-25"].includes(id) &&
      !window.dispatchEvent(
        new CustomEvent("sicost:screen-navigation-request", {
          cancelable: true,
          detail: {
            screenId: id,
            label: "작업 탭 닫기",
            proceed: () => close(id, true),
          },
        }),
      )
    )
      return;
    if (
      id === "UI-16" ||
      (id !== "UI-10" &&
        isDirty?.(id) &&
        !window.confirm("저장하지 않은 변경을 버릴까요?"))
    )
      return;
    setTabs((previous) => previous.filter((item) => item !== id));
    if (id === active) navigate("/UI-16");
  };
  const star = (id: string) => {
    if (!byScreen(id)) return;
    if (!favorites.includes(id) && favorites.length >= 10) {
      setPreferenceError("즐겨찾기는 최대 10개까지 등록할 수 있습니다.");
      return;
    }
    void savePreference(
      "FAV",
      favorites.includes(id)
        ? favorites.filter((item) => item !== id)
        : [...favorites, id],
    );
  };
  const fold = () => {
    const next = !folded;
    void savePreference("MENU_FOLD", next).then((ok) => {
      if (ok) setFolded(next);
    });
  };
  const reset = async (type: string) => {
    const pref = getPref(type);
    if (!pref?.revision || saving.current) return;
    saving.current = true;
    setPending(true);
    setPreferenceError("");
    try {
      if (preferences) await preferences.reset(type, "*");
      else {
        await deleteSharedPreference(type, "*", pref.revision);
        const result = await getSharedPreferences();
        setPrefs(result.items);
      }
      if (type === "MENU_FOLD") setFolded(window.innerWidth < 1280);
    } catch (e) {
      setPreferenceError(e instanceof Error ? e.message : "설정 초기화 실패");
    } finally {
      saving.current = false;
      setPending(false);
    }
  };
  if (error)
    return (
      <main className="sicost-screen-content">
        <ErrorBanner message={error} onRetry={() => setReload((n) => n + 1)} />
      </main>
    );
  if (!menus || !user)
    return (
      <main className="sicost-startup" role="status" aria-busy="true">
        <span className="sicost-logo">SI</span>
        <h1>프로젝트·원가 관리</h1>
        <p>메뉴를 불러오는 중입니다.</p>
        <div className="sicost-loading-lines" aria-hidden="true">
          <i />
          <i />
          <i />
        </div>
      </main>
    );
  if (!nodes.length)
    return (
      <main className="sicost-screen-content">
        <ErrorBanner
          message="사용 가능한 메뉴가 없습니다."
          onRetry={() => setReload((n) => n + 1)}
        />
      </main>
    );
  return (
    <SharedScreenContext.Provider
      value={{
        menus: menus.items,
        favorites,
        star,
        pending: pending || !prefsReady || Boolean(preferences?.pending),
        codes,
        mappings,
        standards,
        pageSize: [20, 50, 100].includes(Number(getPref("PAGE_SIZE")?.value))
          ? (Number(getPref("PAGE_SIZE")?.value) as 20 | 50 | 100)
          : 50,
        dense: getPref("DENSE")?.value === "좁게",
        saveDense: async (dense) => {
          if (!(await savePreference("DENSE", dense ? "좁게" : "넓게")))
            throw new Error("목록 설정을 저장하지 못했습니다.");
        },
        savePageSize: async (size) => {
          if (!(await savePreference("PAGE_SIZE", size)))
            throw new Error("목록 설정을 저장하지 못했습니다.");
        },
      }}
    >
      <div
        className={`sicost-app${folded ? " sicost-menu-folded" : ""}`}
        data-screen-id="SCR-UI"
        data-testid="application-workspace"
      >
        <header className="sicost-header">
          <button
            type="button"
            aria-label="메뉴 열기·접기"
            className="sicost-icon-button"
            data-testid="workspace-menu-toggle"
            onClick={() => {
              if (window.innerWidth < 768) setMobileOpen((open) => !open);
              else fold();
            }}
          >
            <Icon name="menu" />
          </button>
          <Link
            to="/UI-16"
            className="sicost-brand-link"
            data-testid="workspace-brand-home"
            onClick={(event) => {
              event.preventDefault();
              go("UI-16");
            }}
          >
            <BrandMark />
            <strong className="sicost-brand">프로젝트·원가 관리</strong>
          </Link>
          <button
            type="button"
            className="sicost-workspace-search"
            data-testid="workspace-menu-search"
            onClick={() => setUtility("search")}
          >
            <Icon name="search" size={18} />
            <span>메뉴 찾기</span>
            <kbd>Ctrl K</kbd>
          </button>
          <span className="sicost-header-spacer" />
          <span
            className="sicost-header-basis"
            data-testid="workspace-server-basis"
          >
            <Icon name="calendar" size={15} />
            {basis?.currentDate
              ? `기준일 ${basis.currentDate}`
              : "기준일 미확인"}
          </span>
          <HeaderActions
            onBadges={setNotificationBadges}
            onOpenTarget={(link) => {
              const target = notificationDestination(link);
              return (
                target !== null &&
                Boolean(
                  go(
                    target.screenId,
                    false,
                    target.screenId === "UI-13" && link.linkKey === null
                      ? { fromActivity: true, section: "receivables" }
                      : target.state,
                  ),
                )
              );
            }}
          />
          {user.developmentMode && (
            <span className="sicost-development">개발용 임시 관리자</span>
          )}
          <div className="sicost-user-anchor">
            <button
              type="button"
              aria-label={user.name}
              data-testid="workspace-user-menu"
              ref={userButton}
              onClick={() => setUserMenu((open) => !open)}
              aria-expanded={userMenu}
            >
              <Icon name="user" size={16} />
              <span className="sicost-user-name">{user.name}</span>
              <Icon name="chevron" size={12} className="sicost-chevron-down" />
            </button>
            {userMenu && (
              <div className="sicost-user-menu">
                <button
                  type="button"
                  data-testid="profile-menu-button"
                  onClick={() => {
                    userButton.current?.focus();
                    setDialog("profile");
                    setUserMenu(false);
                  }}
                >
                  내 정보
                </button>
                <button
                  onClick={() => {
                    setDialog("settings");
                    setUserMenu(false);
                  }}
                >
                  화면 설정
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setUtility("search");
                    setUserMenu(false);
                  }}
                >
                  메뉴 찾기
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setUtility("help");
                    setUserMenu(false);
                  }}
                >
                  단축키 보기
                </button>
              </div>
            )}
          </div>
        </header>
        <div className="sicost-app-body">
          {mobileOpen && (
            <button
              className="sicost-nav-scrim"
              aria-label="메뉴 닫기"
              data-testid="workspace-mobile-menu-close"
              onClick={() => setMobileOpen(false)}
            />
          )}
          <aside
            className={`sicost-sidebar${folded ? " folded" : ""}${mobileOpen ? " mobile-open" : ""}`}
            aria-label="주 메뉴"
          >
            <div className="sicost-quick">
              <div className="sicost-quick-title">
                <Icon name="star" size={13} /> 즐겨찾기{" "}
                <button onClick={() => setDialog("favorites")}>편집</button>
              </div>
              {favorites.length ? (
                favorites.filter(visibleOnDevice).map((id) => (
                  <Link
                    key={id}
                    to={`/${id}`}
                    onClick={(event) => {
                      event.preventDefault();
                      go(id);
                    }}
                  >
                    {byScreen(id)?.name}
                  </Link>
                ))
              ) : (
                <p>별을 눌러 화면을 추가하세요.</p>
              )}
              <div className="sicost-quick-title">
                <Icon name="clock" size={13} />
                최근 본 화면
              </div>
              {recent.filter(visibleOnDevice).map((id) => (
                <Link
                  key={id}
                  to={`/${id}`}
                  onClick={(event) => {
                    event.preventDefault();
                    go(id);
                  }}
                >
                  {byScreen(id)?.name}
                </Link>
              ))}
            </div>
            <nav>
              {menus.items.map((parent) => {
                const children = parent.children.filter(
                  (child) => !mobile || !child.pcOnly,
                );
                if (
                  mobile &&
                  (parent.pcOnly || (!parent.screenId && !children.length))
                )
                  return null;
                const first =
                  parent.screenId ??
                  flattenMenus(children).find((node) => node.screenId)
                    ?.screenId;
                const direct = children.length === 1 && first === "UI-16";
                const open = groups.includes(parent.menuId);
                return (
                  <div
                    key={parent.menuId}
                    className={`sicost-nav-group${open ? " open" : ""}${parent.menuId === current?.parentId ? " current" : ""}`}
                  >
                    <div className="sicost-nav-parent">
                      <Link
                        aria-label={parent.name}
                        aria-current={
                          direct && active === first ? "page" : undefined
                        }
                        to={`/${first ?? "UI-16"}`}
                        onClick={(event) => {
                          event.preventDefault();
                          if (first) go(first);
                        }}
                      >
                        <span
                          className="sicost-menu-icon"
                          title={parent.icon ?? undefined}
                          data-icon={parent.icon ?? undefined}
                        >
                          <Icon name={parent.icon} size={20} />
                        </span>
                        <span className="sicost-menu-label">{parent.name}</span>
                      </Link>
                      {!direct && children.length > 0 && (
                        <button
                          aria-label={`${parent.name} 하위 메뉴 ${open ? "접기" : "펼치기"}`}
                          aria-expanded={open}
                          onClick={() =>
                            setGroups((previous) =>
                              open
                                ? previous.filter((id) => id !== parent.menuId)
                                : [...previous, parent.menuId],
                            )
                          }
                        >
                          <Icon
                            name="chevron"
                            size={14}
                            className={open ? "sicost-chevron-down" : ""}
                          />
                        </button>
                      )}
                    </div>
                    <div className="sicost-nav-children" hidden={direct}>
                      {!direct &&
                        children.map((child) => (
                          <Link
                            key={child.menuId}
                            to={`/${child.screenId}`}
                            aria-current={
                              active === child.screenId ? "page" : undefined
                            }
                            onClick={(event) => {
                              event.preventDefault();
                              if (child.screenId) go(child.screenId);
                            }}
                          >
                            {child.name}
                            {child.screenId === "UI-13" &&
                              notificationBadges &&
                              (notificationBadges.receivableCount ?? 0) > 0 && (
                                <small data-testid="receivable-menu-count">
                                  {notificationBadges.receivableCount}
                                </small>
                              )}
                            {child.screenId === "UI-14" &&
                              notificationBadges?.currentMonthUnclosed && (
                                <small data-testid="month-close-menu-warning">
                                  !
                                </small>
                              )}
                            {child.pcOnly && <small>PC</small>}
                          </Link>
                        ))}
                    </div>
                  </div>
                );
              })}
            </nav>
            <button
              className="sicost-collapse"
              data-testid="workspace-menu-collapse"
              onClick={fold}
              disabled={pending}
            >
              <Icon name="menu" size={16} />
              <span>{folded ? "펼치기" : "메뉴 접기"}</span>
            </button>
          </aside>
          <main className="sicost-main">
            <nav className="sicost-screen-tabs" aria-label="열린 화면">
              {tabs
                .filter((id) => byScreen(id))
                .map((id) => (
                  <div
                    key={id}
                    className={id === active ? "active" : ""}
                    data-testid={`workspace-tab-${id.toLowerCase()}`}
                  >
                    <button
                      data-testid={`workspace-open-${id.toLowerCase()}`}
                      aria-current={id === active ? "page" : undefined}
                      onClick={() => go(id)}
                    >
                      {isDirty?.(id) && "● "}
                      {byScreen(id)?.name}
                    </button>
                    {id !== "UI-16" && (
                      <button
                        aria-label={`${byScreen(id)?.name} 탭 닫기`}
                        data-testid={`workspace-close-${id.toLowerCase()}`}
                        onClick={() => close(id)}
                      >
                        ×
                      </button>
                    )}
                  </div>
                ))}
            </nav>
            {preferenceError && <ErrorBanner message={preferenceError} />}
            {preferences?.error != null && (
              <ErrorBanner
                message={
                  preferences.error instanceof Error
                    ? preferences.error.message
                    : "화면 설정을 불러오지 못했습니다."
                }
                onRetry={preferences.retry}
              />
            )}
            {menus.diagnostics.map((item) => (
              <p className="sicost-banner sicost-tone-warn" key={item.menuId}>
                {item.menuId}: {item.message}
              </p>
            ))}
            {restriction && (
              <p role="alert" className="sicost-banner sicost-tone-warn">
                {restriction}
              </p>
            )}
            {!restriction && screenRegistry[active] && !byScreen(active) && (
              <p role="alert">이 화면을 볼 권한이 없습니다.</p>
            )}
            {tabs
              .filter((id) => byScreen(id))
              .map((id) => {
                const Component = screenRegistry[id].component;
                return (
                  <div
                    key={id}
                    hidden={id !== active || Boolean(restriction)}
                    className="sicost-screen-host"
                  >
                    <ScreenPermissionContext.Provider value={id}>
                      {screens?.[id] ?? <Component />}
                    </ScreenPermissionContext.Provider>
                  </div>
                );
              })}
          </main>
        </div>
      </div>
      <WorkspaceUsability
        menus={menus.items}
        active={active}
        onOpen={go}
        mode={utility}
        onClose={() => setUtility(null)}
      />
      <Dialog
        open={Boolean(menuDeparture)}
        title="저장하지 않은 내용이 있습니다."
        cancelLabel="계속 작성"
        confirmLabel="버리기/나가기"
        onCancel={() => setMenuDeparture(null)}
        onConfirm={() => {
          menuDeparture?.();
          setMenuDeparture(null);
        }}
      >
        <p>
          메뉴 사용·PC 설정이 변경되었습니다. 미저장 내용을 버리고 첫 진입
          가능한 메뉴로 이동할까요?
        </p>
      </Dialog>
      <ProfileDialog
        open={dialog === "profile"}
        onClose={() => setDialog(null)}
      />
      {renderSettings ? (
        renderSettings(dialog === "settings", () => setDialog(null))
      ) : (
        <Dialog
          open={dialog === "settings"}
          title="화면 설정"
          cancelLabel="닫기"
          confirmLabel="완료"
          onCancel={() => setDialog(null)}
          onConfirm={() => setDialog(null)}
          pending={pending}
        >
          <div data-screen-id="SCR-UI-16-M1">
            {settings ?? (
              <>
                <label>
                  메뉴 접힘{" "}
                  <input
                    type="checkbox"
                    checked={folded}
                    disabled={pending}
                    onChange={fold}
                  />
                </label>
                {[
                  {
                    type: "PAGE_SIZE",
                    label: "한 화면 건수",
                    options: [20, 50, 100],
                    fallback: 50,
                  },
                  {
                    type: "DENSE",
                    label: "목록 밀도",
                    options: ["넓게", "좁게"],
                    fallback: "넓게",
                  },
                  {
                    type: "AMT_UNIT",
                    label: "금액 단위",
                    options: ["원", "천원", "백만원"],
                    fallback: "천원",
                  },
                ].map((setting) => (
                  <div className="sicost-setting" key={setting.type}>
                    <label>
                      {setting.label}
                      <select
                        aria-label={setting.label}
                        value={String(
                          getPref(setting.type)?.value ?? setting.fallback,
                        )}
                        disabled={pending}
                        onChange={(event) =>
                          void savePreference(
                            setting.type,
                            setting.type === "PAGE_SIZE"
                              ? Number(event.target.value)
                              : event.target.value,
                          )
                        }
                      >
                        {setting.options.map((option) => (
                          <option key={option}>{option}</option>
                        ))}
                      </select>
                    </label>
                    <button
                      type="button"
                      disabled={pending || !getPref(setting.type)?.stored}
                      onClick={() => void reset(setting.type)}
                    >
                      이 설정 초기화
                    </button>
                    {getPref(setting.type)?.error && (
                      <p role="alert">
                        {getPref(setting.type)?.error?.message}
                      </p>
                    )}
                  </div>
                ))}
                <button
                  type="button"
                  disabled={pending || !getPref("MENU_FOLD")?.stored}
                  onClick={() => void reset("MENU_FOLD")}
                >
                  메뉴 접힘 초기화
                </button>
                <p>표 열 설정은 해당 표의 열 설정에서 변경합니다.</p>
              </>
            )}
            {preferenceError && <ErrorBanner message={preferenceError} />}
          </div>
        </Dialog>
      )}
      <Dialog
        open={dialog === "favorites"}
        title="즐겨찾기 목록 편집"
        cancelLabel="닫기"
        confirmLabel="완료"
        onCancel={() => setDialog(null)}
        onConfirm={() => setDialog(null)}
        pending={pending}
      >
        <div data-screen-id="SCR-UI-16-M2">
          {favorites.map((id, index) => (
            <div className="sicost-favorite-row" key={id}>
              <span>{byScreen(id)?.name}</span>
              <button
                type="button"
                aria-label={`${byScreen(id)?.name} 위로`}
                disabled={pending || index === 0}
                onClick={() => {
                  const next = [...favorites];
                  [next[index - 1], next[index]] = [
                    next[index],
                    next[index - 1],
                  ];
                  void savePreference("FAV", next);
                }}
              >
                ▲
              </button>
              <button
                type="button"
                aria-label={`${byScreen(id)?.name} 아래로`}
                disabled={pending || index === favorites.length - 1}
                onClick={() => {
                  const next = [...favorites];
                  [next[index + 1], next[index]] = [
                    next[index],
                    next[index + 1],
                  ];
                  void savePreference("FAV", next);
                }}
              >
                ▼
              </button>
              <button type="button" disabled={pending} onClick={() => star(id)}>
                별 해제
              </button>
            </div>
          ))}
          <p>최대 10개·중복 불가</p>
          {preferenceError && <ErrorBanner message={preferenceError} />}
        </div>
      </Dialog>
    </SharedScreenContext.Provider>
  );
}
