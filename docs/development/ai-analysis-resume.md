# AI analysis and resume assistance — development notes

## Runtime

The existing Spring MVC/MyBatis/Flyway application and registration services remain authoritative. There are no new migrations, resume storage tables, or AI business-calculation ledgers.

- `POST /api/analysis/questions`: validated JSON; SSE `progress` and `final` events wrapped in the existing API envelope. Final success is emitted only after the append-only QNA transaction commits.
- `GET /api/analysis/questions?cursor=...&limit=50`: server-owned current user, timestamp/numeric-ID keyset pagination.
- `GET /api/analysis/questions/{qnaId}`: immutable saved result, owner check and current policy recheck.
- `GET /api/analysis/questions/{qnaId}/excel`: saved whole tables and evidence, existing workbook/DNLD mechanism, current policy recheck. No live business-data refresh during export.
- `POST /api/resume-extractions`: one multipart `file` and `targetType=DEVELOPER|CANDIDATE`; suggestions only. Registration remains a separate deliberate action through the existing endpoints.

Business reads use one short repeatable-read transaction. Model waiting is outside that transaction. Successful prediction completion and its existing notification callback share the QNA insert transaction. General reads, unsupported questions, and failures do not publish AI prediction completion.

## Optional model configuration

Default: `AI_PROVIDER=NONE` (no external model request). These are backend environment variables, not browser settings:

| Variable | Value |
| --- | --- |
| `AI_PROVIDER` | `NONE` or `OLLAMA` |
| `AI_BASE_URL` | Operator-controlled HTTP(S) origin/base path, without credentials, query, or fragment |
| `AI_MODEL` | Installed Ollama model name |
| `AI_TIMEOUT_SECONDS` | 1–120 seconds; default 30 |

For an operator-provided Ollama installation, set `AI_PROVIDER=OLLAMA`, `AI_BASE_URL=http://<operator-host>:11434`, `AI_MODEL=<installed-model>`, and optionally `AI_TIMEOUT_SECONDS=30` before launching the normal backend jar/container. The adapter calls `/api/chat`, `stream:false`, with a closed JSON Schema. It does not install or download models. It only sends enum tokens and opaque local references, never the raw question, names, dates, monetary arguments, result rows, prior conversation, or resume contents. Response size and full-response elapsed time are bounded, and redirects/retries are disabled. Clear rules can skip the model; rejected/failed model responses fall back to the already resolved rules with a visible notice.

The local HTTP-server model tests validate the request/response contract and failure behavior. They are **not** evidence of a successful live Ollama inference. A live model requires an operator-provided running service/model and must be verified separately.

## Resume processing

Pinned Java 17 dependencies: Apache POI 5.3.0 (including `poi-scratchpad`) and PDFBox 3.0.5. DOC/PPT use POI, PDF uses PDFBox, RTF uses the JDK's text reader; DOCX/PPTX/HWPX read bounded body/table XML with external entities/DTDs disabled; HWP5 reads bounded OLE BodyText records and table cell headers. No office converter, OCR, macro execution, linked-resource fetching, embedded-document execution, model call, or permanent file store is used.

Limits: 1–10,485,760 bytes, 200,000 extracted characters, 30-second whole-extraction deadline, bounded worker queues, bounded archive expansion. Encrypted/restricted/damaged documents and expansion/character/time limits are errors, not partial successful suggestions. Supported documents containing no text produce a real zero-field result. TXT supports UTF-8/BOM, then strict MS949 decoding. Legacy HWP is explicitly identified as unsupported.

The dictionary is one repeatable-read snapshot of active groups/codes, active hierarchical areas, and active vendor/both companies. Raw files and extracted text are request-only. Multipart temporary files are container-managed; no application permanent path is created. Buffer cleanup runs on success/failure/deadline. Closing a form aborts its request, clears evidence, and advances a request-generation guard; old responses/finally handlers cannot refill or lock a reopened form or interfere with a newer upload.

## Verification commands

Use Java 17 / Maven 3.9 and Node 20:

- `mvn -f backend/pom.xml test`
- `npm --prefix frontend ci`
- `npm --prefix frontend run build`
- `npm --prefix frontend test -- --run`

Database tests follow the existing `SICOST_TEST_DB_URL`, `SICOST_TEST_DB_USER`, `SICOST_TEST_DB_PASSWORD` convention and install migrations in isolated, randomly named **test schemas**, not the application's business schema. Set those values through the test runner's secret environment; do not put credentials into this document or logs.

Targeted suites: `AnalysisAssistantDatabaseTest`, `AnalysisModelAdapterTest`, `AnalysisQuestionInterpreterTest`, `ResumeDocumentParserTest`, `ResumeFieldExtractorTest`, and frontend `useResumeExtraction.test.ts`. The parser suite exercises all nine extensions; independent HWP/HWPX fixtures are documented separately under `backend/src/test/resources/resume/README.md`. Synthetic/control-record and mocked-transport tests are distinguished from actual document/DB tests.

Existing legacy frontend assertions that expect UI-26 to remain a preparation screen or the candidate creation action to retain its old “저장” label conflict with this feature's approved actual screen / “등록” labels. Those original assertions are retained, not weakened to conceal the conflict.

## Resumed implementation verification (2026-11-16)

- The selected backend feature/shared-service run reports 231 tests: 207 passed, 24 skipped, no failures/errors, and an executable jar was packaged. The skipped monthly-cost database suites require the repository's independent existing-data baseline (`SICOST_COST_TEST_DB_URL`); an empty or newly fabricated baseline is not substituted. Six analysis PostgreSQL tests do run in separate migrated test schemas, including the real saved-result workbook and DNLD mapper/transaction path.
- The final targeted frontend run reports 158 passing tests across 22 files. It includes extraction cancellation/races, byte-fragmented SSE, duplicate history clicks, exact decimal presentation, 51-row result pagination, native navigation bindings, and original profitability/settlement/developer regressions. One worker and an explicit 15-second test timeout were used to avoid shared-runner contention; assertions were not removed or weakened.
- An earlier full frontend run reported 2,519 passing and seven failing tests. Four still look for the old candidate “저장” action, and three depend on the previous preparation-screen/provider fixture. This is an explicitly retained test-contract conflict, not an all-green full-suite claim.
- The verifier's supplied numerical fixture was replayed only in the disposable runtime database. Real HTTP/SSE and saved-detail reads verify 48.8% operating margin and 36.3% net margin against the common closed-month structure, unsupported seven-month revenue/four-month cash rejection, equivalent `overdueDays GE 60` filtering in both Korean word orders, and an MO project's 666,000-won actual cost despite its intentionally absent TK forecast. Thirteen protected business-table count/content fingerprints remain identical across those analysis/export requests.
- The same live check exports all 65 overdue rows, the header and whole-result total (67 spreadsheet rows), then re-reads the identical saved answer. The workbook also records each table's scenario, period, state, assumptions/evidence and missing-cell reasons. The existing exact-decimal writer falls back to text only when Excel cannot represent a value exactly; representable negative monetary values remain numeric cells and user strings never become formulas.
- Real Chromium checks exercise one accepted question shared by the page and header, manual-name/consent protection during actual multipart extraction, 390-pixel dark layouts without horizontal overflow, both registration entry points, and zero page exceptions. The independently verified T110 generation guard remains in place.
- Explicit month counts are applied within the supported forecast window or rejected, never silently replaced by defaults. Both `률` and `율` rate spellings are recognized; overdue-day conditions are order-independent, and unhandled/contradictory day conditions request clarification. Non-TK actual-cost reads do not dereference a missing TK forecast.
- Original-screen links retain unique targets, periods and filters without arbitrarily selecting one entity from a multi-entity result. Project costs use the native URL query, settlement uses the native selected-part/month entry, and individual profitability opens the selected year/person. Where the original view has a different period granularity, its source-screen notice retains the analysis range and explains the ledger comparison instead of pretending that a different native aggregate is the same result.

Live Ollama inference is still unverified because no operator-provided running model was supplied. Default `NONE`, no external transmission, genuine document parsing and controlled HTTP-transport tests are separate claims. Final Quality Docker/PR delivery is owned by the surrounding system, not represented by the local checks above.

### Follow-up repairs after preservation resume

- **T052:** The open-month common `MonthlyCostCalculation` now requires a resolved `appliedUnitPrice` before treating a paid MO/DP assignment's mandatory `salesAmount` as confirmed revenue. A missing price may coexist with a stored zero placeholder; it yields NULL revenue/profit plus the original missing-price reason, not a confirmed loss. A genuinely resolved price of zero remains valid zero. Known labor/direct costs are retained. FREE/internal and protected saved-month paths are unchanged; the analysis consumes this repaired common result instead of patching its own numbers.
- **T059:** Turnkey eligibility uses the original `Detail.sourceStatus`, not only the normalized display `Row.status` (a PRE source can display ING). Contract-before/ended/non-TK/zero-contract or unsupported forecast targets return a persisted NO_ANS and no result tables. Missing costs on an otherwise eligible forecast remain unknown rather than fabricated zero.
- Real before/after HTTP/SSE, saved-detail and common monthly-cost checks confirm `QA_MISS`: revenue/profit NULL and cost 400,000; `QA_ZERO`: revenue/cost/profit zero; `QA_NOPAY`: known revenue 444,444 with NULL cost/profit. PRE returns NO_ANS without tables. Twelve business-table content/count fingerprints remain unchanged across these analysis/export checks. The prior 65-row XLSX, two GE60 word orders, unsupported 7/4-month requests and known MO actual cost were replayed successfully.
- The expanded backend package run reports **328 tests: 303 passed, 25 skipped, zero failures/errors**. The 25 are the three pre-existing monthly-cost baseline DB suites (24 tests) and one project-cost baseline DB test, not successful executions. All seven analysis PostgreSQL tests execute in isolated schemas. The added clock test crosses Seoul midnight/year/quarter using an injected clock, verifies different relative quarters and new QNA IDs while preserving explicit October and the old answer; future-dated test records roll back so they cannot pollute another test's keyset pages. It is not a live server-clock change or a business-calculation fixture. Ordinary personal-query evidence no longer claims the unapplied `profit < 0` filter; only the actual loss query includes that basis, with a separate regression and live check.
- The candidate consent control now follows the contact inputs before job/skill fields in both DOM and keyboard order, matching the reference and retaining manual-only consent. A new focused layout assertion plus the shared extraction/SSE/navigation/conversation suites report **17 passing tests across six files**; the production TypeScript/Vite build also passes. Original legacy assertions are preserved.
- The completed full frontend rerun reports **2,524 passing and seven failing tests across 198 files**. The same seven retained old preparation-screen/provider/“저장” expectations fail; they are not hidden by the focused run. The subsequently added consent-order assertion passes separately in the six-file run above.
- A fresh Chromium run confirms one POST while sharing a result with the header, NULL/missing-price presentation, the native 2026 personal query plus the existing developer drawer, project `month=2026-11&projectId=QA_MO` navigation, actual multipart extraction with manual name and unchecked consent preserved, both resume entry points, no 390px horizontal overflow and zero page exceptions. Native developer navigation uses a genuinely registered `DEV-000001`/`K-0001`; the numerical verifier's `QA_MISS` is intentionally not rewritten into a valid native developer identifier.
