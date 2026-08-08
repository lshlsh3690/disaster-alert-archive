#!/usr/bin/env node
// PostToolUse(Bash) 훅 전용 스크립트. (구 이름: ui-check-trigger.mjs — 범위가
// UI 점검 외에 코드 리뷰까지 넓어져 이름을 바꿨다.)
//
// git commit 커맨드가 지나갈 때마다 실행되지만, 실제로 새 커밋이 생겼을
// 때만 그 커밋 자신의 부모와 diff해서 무엇이 바뀌었는지 본다. 그 외에는
// 아무 출력 없이 즉시 종료 — 커밋 자체를 막거나 지연시키지 않는다.
//
// 여기서는 스크린샷/시각 분석/코드 리뷰를 직접 하지 않는다(순수 셸
// 스크립트는 그럴 능력이 없다) — 변경 파일 목록만 Claude의 다음 턴
// 컨텍스트에 끼워 넣고, 실제 agent 실행 여부/대상 판단은 Claude가 한다.

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
// 뒤에 하이픈이 오는 git commit-tree 같은 plumbing 커맨드는 제외.
if (!/\bgit\s+commit(?:\s|$)/.test(command)) process.exit(0);

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
let lastSha = "";
try {
  lastSha = existsSync(stateFile) ? readFileSync(stateFile, "utf8").trim() : "";
} catch {
  // 상태 파일을 못 읽어도 훅 자체는 계속 진행 — 최초 실행처럼 취급.
}

if (headSha === lastSha) process.exit(0);

const isFirstRun = lastSha === "";

try {
  writeFileSync(stateFile, headSha);
} catch {
  // 상태 파일을 못 써도(권한 등) 감지 자체는 계속 진행 — 다음 실행에서
  // 중복 보고될 수 있지만 커밋을 막는 것보단 낫다.
}

// 최초 실행(상태 파일이 아직 없음)은 비교 기준이 없으므로 건너뛴다 —
// 그렇지 않으면 "git commit"이 매칭됐지만 실제로 새 커밋이 생기지 않은
// 경우(거부된 커밋 등)에도 기존 HEAD를 "방금 커밋"으로 오인할 수 있다.
if (isFirstRun) process.exit(0);

let diffOutput;
try {
  diffOutput = execSync(`git diff --name-only ${headSha}~1 ${headSha}`, { cwd: repoRoot, encoding: "utf8" });
} catch {
  // 부모가 없는 최초 커밋(루트 커밋) 등 — 조용히 종료.
  process.exit(0);
}

const changedFiles = diffOutput
  .split("\n")
  .map((f) => f.trim())
  .filter(Boolean);

const frontendFiles = changedFiles.filter((f) => f.startsWith("frontend/src/"));
// backend/src/test는 제외 — test-writer의 Red-Green 검증 흐름이 이미 따로
// 있고, dev-reviewer는 실제 운영 코드의 컨벤션 준수 여부를 보는 용도다.
const reviewableFiles = changedFiles.filter(
  (f) => f.startsWith("backend/src/main/") || f.startsWith("frontend/src/")
);

if (frontendFiles.length === 0 && reviewableFiles.length === 0) process.exit(0);

const sections = [`[자동 감지 — PostToolUse(Bash) 훅] 방금 커밋(${headSha.slice(0, 7)})에 다음 변경이 포함되어 있다:`];

if (frontendFiles.length > 0) {
  sections.push(
    `\n프론트엔드 변경 (반응형 UI 점검 대상):\n${frontendFiles.map((f) => `- ${f}`).join("\n")}\n\n` +
      `1. 위 변경 파일들을 보고 영향받을 만한 페이지 경로(App Router \`page.tsx\` 기준)를 추론해라. 공용 컴포넌트(components/ui, components/layout 등)가 바뀌었다면 그 컴포넌트를 쓰는 페이지가 여러 개일 수 있으니 폭넓게 잡아라.\n` +
      `2. 사용자에게 먼저 물어보지 말고 바로 \`ui-checker\` agent를 백그라운드(run_in_background: true)로 실행해서 그 페이지(들)의 반응형 UI(줄바꿈/글자깨짐/겹침 등)를 점검해라.\n` +
      `3. 결과가 오면, 확정 버그가 있어도 \`ui-fixer\`에게 자동으로 넘기지 마라 — 이번엔 이 자동 트리거 경로이므로 점검 결과를 사용자에게 보고만 하고, 고칠지 여부는 항상 승인을 받아라. (사용자가 직접 "UI 확인해줘"라고 요청했을 때의 기존 ui-checker→ui-fixer 자동 위임과는 다르게 처리해야 한다.)\n` +
      `4. 확정/확인필요 버그가 하나도 없으면 그 사실만 짧게 알리면 된다.`
  );
}

if (reviewableFiles.length > 0) {
  sections.push(
    `\n코드 리뷰 대상(백엔드 운영 코드/프론트엔드):\n${reviewableFiles.map((f) => `- ${f}`).join("\n")}\n\n` +
      `1. 사용자에게 먼저 물어보지 말고 바로 \`dev-reviewer\` agent를 백그라운드(run_in_background: true)로 실행해서 이 커밋(${headSha.slice(0, 7)})의 diff(\`git show ${headSha}\`)를 이 저장소 컨벤션(CLAUDE.md) 기준으로 검토해라.\n` +
      `2. \`dev-reviewer\`는 읽기 전용이라 스스로 코드를 고치지 못한다 — 결과가 오면 발견사항을 사용자에게 그대로 보고만 하고, 실제 수정은 사용자 승인을 받은 뒤에만 진행해라.\n` +
      `3. 발견사항이 없으면 그 사실만 짧게 알리면 된다.`
  );
}

console.log(
  JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "PostToolUse",
      additionalContext: sections.join("\n"),
    },
  })
);
