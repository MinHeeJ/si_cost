import { Dialog } from "../../shared/Dialog";
import type { CodeMapping } from "./codeMappingsApi";

export function MappingDeleteDialog({
  rows,
  pending,
  onCancel,
  onConfirm,
}: {
  rows: CodeMapping[];
  pending: boolean;
  onCancel(): void;
  onConfirm(): Promise<void>;
}) {
  return (
    <Dialog
      open
      title="선택 표기 삭제"
      pending={pending}
      confirmLabel="삭제 확인"
      onCancel={onCancel}
      onConfirm={onConfirm}
      wide
    >
      <div data-screen-id="SCR-UI-27-M6">
        <p>선택한 표기를 삭제할까요? 이전 내용은 변경 이력에 보존됩니다.</p>
        <div className="sicost-table-viewport">
          <table className="sicost-table">
            <thead>
              <tr>
                <th>엑셀 표기</th>
                <th>코드 그룹</th>
                <th>바꿀 코드 / 이름</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr
                  data-testid={`mapping-delete-row-${String(row.mapId).toLowerCase()}`}
                  key={row.mapId}
                >
                  <td>{row.sourceText}</td>
                  <td>{row.groupId}</td>
                  <td>
                    {row.code} · {row.codeName}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p>한 행이라도 변경되었으면 선택한 행 전체를 삭제하지 않습니다.</p>
      </div>
    </Dialog>
  );
}
