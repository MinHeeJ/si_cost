import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DeveloperDrawer } from "../developers/DeveloperDrawer";
import { getCurrentUserPermissionSettings } from "../currentuserops/permissionSettingsApi";
import { useSharedScreen } from "../shared/SharedScreenContext";

/** Reuse the developer detail and its peopleClient. No profitability-specific editor or detail copy. */
export function ProfitabilityDetails({
  personId,
  onClose,
}: {
  personId: string;
  onClose(): void;
}) {
  const navigate = useNavigate();
  const { menu } = useSharedScreen("UI-02");
  const [allowed, setAllowed] = useState(false);
  useEffect(() => {
    let live = true;
    setAllowed(false);
    if (menu)
      void getCurrentUserPermissionSettings()
        .then((settings) => {
          if (live)
            setAllowed(
              settings.menuPermissions.some(
                (permission) =>
                  permission.menuId === menu.menuId &&
                  permission.inquire &&
                  permission.update,
              ),
            );
        })
        .catch(() => {
          if (live) setAllowed(false);
        });
    return () => {
      live = false;
    };
  }, [menu?.menuId]);
  return (
    <DeveloperDrawer
      key={personId}
      id={personId}
      onClose={onClose}
      onEdit={
        allowed
          ? (developer) =>
              navigate(
                `/UI-02?${new URLSearchParams({ editDeveloperId: developer.id })}`,
              )
          : undefined
      }
    />
  );
}
