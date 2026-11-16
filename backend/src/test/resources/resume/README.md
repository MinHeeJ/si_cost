# Text parser fixtures

These are document fixtures only, never fallback resume contents. Production never reads this directory.

- `empty.doc`: Apache POI `REL_5_3_0/test-data/document/empty.doc` (Apache-2.0). Tests insert their own text with HWPF before reading the actual DOC.
- `hwplib-table.hwp`: `neolord0/hwplib`, `main/sample_hwp/basic/표.hwp` (Apache-2.0), retrieved 2026-11-16. Real HWP5 table fixture.
- `hwpxlib-table.hwpx`: `neolord0/hwpxlib`, `main/testFile/tool/textextractor/Table.hwpx` (Apache-2.0), retrieved 2026-11-16. Real HWPX table fixture; expected cells include `C반 기말고사`, `국어`, `영어`, and `수학`.

Tests also generate DOCX/PPT/PPTX/PDF/RTF documents and synthetic HWP/HWPX control records to exercise table boundaries, encryption, size limits, and XML entity rejection. Generated/control-record tests are distinct from the real HWP/HWPX fixture checks. No installed office converter, OCR service, or external model is involved.
