import { useState } from "react";
import { BusinessUsersScreen } from "./BusinessUsersScreen";
import { usePreferences } from "../me/usePreferences";
import {
  ColumnPreferencesDialog,
  type PreferenceColumn,
} from "../me/ColumnPreferencesDialog";
import type { ScreenColumnPreferences } from "../me/preferencesApi";
import { ErrorBanner } from "../../shared/Feedback";

const columns: PreferenceColumn[] = [
  { id: "userId", label: "아이디", required: true },
  { id: "name", label: "이름", required: true },
  { id: "email", label: "이메일" },
  { id: "roles", label: "역할" },
  { id: "status", label: "상태" },
  { id: "lastLoginAt", label: "마지막 접속" },
];
export function BusinessUsersWorkspace({
  onDirtyChange,
  registration,
}: {
  onDirtyChange?(dirty: boolean): void;
  registration?: import("./BusinessUsersScreen").BusinessUsersScreenProps["registration"];
}) {
  const preferences = usePreferences();
  const [editingColumns, setEditingColumns] = useState(false),
    [error, setError] = useState<unknown>();
  const view = (
    preferences.get("COLS", "UI-23")?.value as
      | ScreenColumnPreferences
      | undefined
  )?.users;
  const size = preferences.get("PAGE_SIZE")?.value;
  const pageSize = size === 20 || size === 100 ? size : 50;
  const dense = preferences.get("DENSE")?.value === "좁게";
  async function density() {
    setError(undefined);
    try {
      await preferences.put("DENSE", "*", dense ? "넓게" : "좁게");
    } catch (e) {
      setError(e);
    }
  }
  return (
    <>
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "화면 설정을 저장하지 못했습니다."
          }
        />
      )}
      <BusinessUsersScreen
        registration={registration}
        columnView={view}
        pageSize={pageSize}
        dense={dense}
        onDirtyChange={onDirtyChange}
        onColumnsChange={() => setEditingColumns(true)}
        onDensityChange={() => void density()}
        onPageSizeChange={(size) => preferences.put("PAGE_SIZE", "*", size)}
      />
      {editingColumns && (
        <ColumnPreferencesDialog
          open
          screenId="UI-23"
          viewId="users"
          columns={columns}
          onClose={() => setEditingColumns(false)}
        />
      )}
    </>
  );
}
