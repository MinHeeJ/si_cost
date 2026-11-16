import "@testing-library/jest-dom/vitest";
import { cleanup, configure } from "@testing-library/react";
import { afterEach } from "vitest";

// findBy*/waitFor 의 기본 대기 1초는 앱 셸이 커진 뒤 무거운 합성 시험(router.test.tsx)에서 CPU 부하가 있을 때 먼저 끝난다.
// 단독 실행에서는 통과하고 전체 실행에서만 흔들리는 시험을 안정시키려고 대기만 늘린다(단언 자체는 그대로).
configure({ asyncUtilTimeout: 5000 });

afterEach(cleanup);
