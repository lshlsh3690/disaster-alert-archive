#!/usr/bin/env node
// PostToolUse(Bash) 훅 전용 스크립트.
// git commit 커맨드가 지나갈 때마다 실행되지만, 실제로 새 커밋이 생겼고
// 그 커밋에 frontend/src 변경이 포함된 경우에만 additionalContext를 출력한다.
// 그 외에는 아무 출력 없이 즉시 종료 — 커밋 자체를 막거나 지연시키지 않는다.
//
// 여기서는 스크린샷/시각 분석을 하지 않는다(순수 셸 스크립트는 그럴 능력이
// 없다) — 변경 파일 목록만 Claude의 다음 턴 컨텍스트에 끼워 넣고,
// 실제 ui-checker 실행 여부/대상 페이지 추론은 Claude가 판단한다.

import { execSync } from "node:child_process";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import path from "node:path";

let input = "";
process.stdin.setEncoding("utf8");
for await (const chunk of process.stdin) input += chunk;

let payload;
try {
  payload = JSON.parse(input || "{}");
} catch {
  process.exit(0);
}

const command = payload?.tool_input?.command ?? "";
if (!/\bgit\s+commit\b/.test(command)) process.exit(0);

let repoRoot;
try {
  repoRoot = execSync("git rev-parse --show-toplevel", { encoding: "utf8" }).trim();
} catch {
  process.exit(0);
}

const stateFile = path.join(repoRoot, ".git", "ui-check-hook-last-sha");

let headSha;
try {
  headSha = execSync("git rev-parse HEAD", { cwd: repoRoot, encoding: "utf8" }).trim();
} catch {
  process.exit(0);
}

// lastSha는 "이 HEAD를 이미 보고했는지" 중복 방지용일 뿐, diff 기준으로
// 쓰지 않는다 — 브랜치를 넘나들면 마지막 확인 시점의 sha가 지금과 무관한
// 브랜치의 커밋일 수 있어, 그 둘을 직접 diff하면 "이 커밋이 바꾼 파일"이
// 아니라 "두 브랜치의 차이"가 나와버려 오탐이 난다. 대신 항상 이 커밋
// 자신의 첫 부모와만 비교한다.
const lastSha = existsSync(stateFile) ? readFileSync(stateFile, "utf8").trim() : "";

if (headSha === lastSha) process.exit(0);
writeFileSync(stateFile, headSha);

let diffOutput;
try {
  diffOutput = execSync(`git diff --name-only ${headSha}^ ${headSha}`, { cwd: repoRoot, encoding: "utf8" });
} catch {
  // 부모가 없는 최초 커밋(루트 커밋) 등 — 조용히 종료.
  process.exit(0);
}

const frontendFiles = diffOutput
  .split("\n")
  .map((f) => f.trim())
  .filter((f) => f.startsWith("frontend/src/"));

if (frontendFiles.length === 0) process.exit(0);

const context = `[자동 감지 — PostToolUse(Bash) 훅] 방금 커밋(${headSha.slice(0, 7)})에 프론트엔드 변경이 포함되어 있다:
${frontendFiles.map((f) => `- ${f}`).join("\n")}

지침:
1. 위 변경 파일들을 보고 영향받을 만한 페이지 경로(App Router \`page.tsx\` 기준)를 추론해라. 공용 컴포넌트(components/ui, components/layout 등)가 바뀌었다면 그 컴포넌트를 쓰는 페이지가 여러 개일 수 있으니 폭넓게 잡아라.
2. 사용자에게 먼저 물어보지 말고 바로 \`ui-checker\` agent를 백그라운드(run_in_background: true)로 실행해서 그 페이지(들)의 반응형 UI(줄바꿈/글자깨짐/겹침 등)를 점검해라.
3. 결과가 오면, 확정 버그가 있어도 \`ui-fixer\`에게 자동으로 넘기지 마라 — 이번엔 이 자동 트리거 경로이므로 점검 결과를 사용자에게 보고만 하고, 고칠지 여부는 항상 승인을 받아라. (사용자가 직접 "UI 확인해줘"라고 요청했을 때의 기존 ui-checker→ui-fixer 자동 위임과는 다르게 처리해야 한다.)
4. 확정/확인필요 버그가 하나도 없으면 그 사실만 짧게 알리면 된다.`;

console.log(
  JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "PostToolUse",
      additionalContext: context,
    },
  })
);
