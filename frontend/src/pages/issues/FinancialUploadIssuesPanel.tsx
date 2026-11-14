import {
  UploadIssuesPanel,
  type UploadIssuesPanelProps,
} from "../uploadsops/UploadIssuesPanel";

// SCR-UI-10-B2-ISSUES and SCR-UI-10-B2-ISSUES-XLSX reuse saved upload diagnostics.
export function FinancialUploadIssuesPanel(
  props: Omit<UploadIssuesPanelProps, "screenId">,
) {
  return (
    <section
      data-testid="financial-upload-issues"
      data-screen-id="SCR-UI-10-B2-ISSUES"
      data-download-screen-id="SCR-UI-10-B2-ISSUES-XLSX"
    >
      <UploadIssuesPanel {...props} screenId="UI-10" />
    </section>
  );
}
