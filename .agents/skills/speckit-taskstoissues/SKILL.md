---
name: speckit-taskstoissues
description: Convert existing tasks into actionable, dependency-ordered GitHub issues for the feature based on available
  design artifacts.
metadata:
  author: github-spec-kit
  source: templates/commands/taskstoissues.md
  compatibility: Requires spec-kit project structure with .specify/ directory
---

## 출력 언어

생성하는 GitHub 이슈의 제목과 본문은 한글로 작성한다. 코드 식별자, 파일 경로, 기술
용어는 원문(영문) 그대로 둔다.

## User Input

`USER_REQUEST` means the user request attached to this invocation, including any arguments. It is not a shell environment variable.

```text
USER_REQUEST
```

You **MUST** consider the user input before proceeding (if not empty).

## Pre-Execution Checks

**Check for extension hooks (before tasks-to-issues conversion)**:
- Check if `.specify/extensions.yml` exists in the project root.
- If it exists, read it and look for entries under the `hooks.before_taskstoissues` key
- If the YAML cannot be parsed or is invalid, skip hook checking silently and continue normally
- Filter out hooks where `enabled` is explicitly `false`. Treat hooks without an `enabled` field as enabled by default.
- For each remaining hook, do **not** attempt to interpret or evaluate hook `condition` expressions:
  - If the hook has no `condition` field, or it is null/empty, treat the hook as executable
  - If the hook defines a non-empty `condition`, skip the hook and leave condition evaluation to the HookExecutor implementation
- When resolving skill names from hook command names, replace dots (`.`) with hyphens (`-`). For example, `speckit.git.commit` → `speckit-git-commit`.
- For each executable hook, output the following based on its `optional` flag:
  - **Optional hook** (`optional: true`):
    ```
    ## Extension Hooks

    **Optional Pre-Hook**: {extension}
    Command: `{command}`
    Description: {description}

    Prompt: {prompt}
    To execute: `{command}`
    ```
  - **Mandatory hook** (`optional: false`):
    ```
    ## Extension Hooks

    **Automatic Pre-Hook**: {extension}
    Executing: `{command}`
    EXECUTE_COMMAND: {command}

    Wait for the result of the hook command before proceeding to the Outline.
    ```
    After emitting the block above you MUST actually invoke the hook and wait for it to finish before continuing. Resolve the skill name using the current tool's project guide, execute its instructions with actual tools, and wait for completion. Emitting the block alone does not run the hook.
- If no hooks are registered or `.specify/extensions.yml` does not exist, skip silently

## Outline

1. Run `bash .agents/scripts/run-speckit.sh check-prerequisites --json --require-tasks --include-tasks` from repo root and parse FEATURE_DIR and AVAILABLE_DOCS list. All paths must be absolute. For single quotes in args like "I'm Groot", use escape syntax: e.g 'I'\''m Groot' (or double-quote if possible: "I'm Groot").
1. **IF EXISTS**: Load `.specify/memory/constitution.md` for project principles and governance constraints.
1. From the executed script, extract the path to **tasks**.
1. Get the Git remote by running:

```bash
git config --get remote.origin.url
```

> [!CAUTION]
> ONLY PROCEED TO NEXT STEPS IF THE REMOTE IS A GITHUB URL

1. **Fetch existing issues for deduplication**: Extract the task IDs from tasks.md using `\bT\d{3}\b`. Discover the connected GitHub tools and their actual schemas. Fetch both open and closed issues, paging until all task IDs have matches or the repository's issues are exhausted. Respect the actual tool's state values and cursor or page-number pagination; do not assume MCP tool names or arguments exist. If the connector cannot do this, use authenticated `gh api --paginate` against the remote repository's issues endpoint with `state=all&per_page=100`, excluding pull requests. Match task IDs in issue titles with word boundaries.
1. For each task without a matching issue, strip the checkbox and `[P]` / `[US#]` markers and create the canonical title `T001: <description>` exactly once, using a connected GitHub tool or `gh issue create --repo <owner/repo> --title <title> --body-file <UTF-8 file>`. Update the in-memory ID set after creation, skip existing IDs, and report created and skipped issues. Only create issues when the user has requested this conversion, and only in the repository matching the remote.

> [!CAUTION]
> UNDER NO CIRCUMSTANCES EVER CREATE ISSUES IN REPOSITORIES THAT DO NOT MATCH THE REMOTE URL

## Post-Execution Checks

**Check for extension hooks (after tasks-to-issues conversion)**:
Check if `.specify/extensions.yml` exists in the project root.
- If it exists, read it and look for entries under the `hooks.after_taskstoissues` key
- If the YAML cannot be parsed or is invalid, skip hook checking silently and continue normally
- Filter out hooks where `enabled` is explicitly `false`. Treat hooks without an `enabled` field as enabled by default.
- For each remaining hook, do **not** attempt to interpret or evaluate hook `condition` expressions:
  - If the hook has no `condition` field, or it is null/empty, treat the hook as executable
  - If the hook defines a non-empty `condition`, skip the hook and leave condition evaluation to the HookExecutor implementation
- When resolving skill names from hook command names, replace dots (`.`) with hyphens (`-`). For example, `speckit.git.commit` → `speckit-git-commit`.
- For each executable hook, output the following based on its `optional` flag:
  - **Optional hook** (`optional: true`):
    ```
    ## Extension Hooks

    **Optional Hook**: {extension}
    Command: `{command}`
    Description: {description}

    Prompt: {prompt}
    To execute: `{command}`
    ```
  - **Mandatory hook** (`optional: false`):
    ```
    ## Extension Hooks

    **Automatic Hook**: {extension}
    Executing: `{command}`
    EXECUTE_COMMAND: {command}
    ```
    After emitting the block above you MUST actually invoke the hook and wait for it to finish before continuing. Resolve the skill name using the current tool's project guide, execute its instructions with actual tools, and wait for completion. Emitting the block alone does not run the hook.
- If no hooks are registered or `.specify/extensions.yml` does not exist, skip silently
