#!/usr/bin/env python3
"""Generate Codex agent definitions from the separate Claude agent definitions.

Requires Python 3.11+ and PyYAML. --check performs no writes.
"""

import argparse
import json
from pathlib import Path
import re
import sys
import tomllib

import yaml


ROOT = Path(__file__).resolve().parents[2]
AGENT_ROOT = ROOT / ".codex" / "agents"


def read_definition(path):
    text = path.read_text(encoding="utf-8")
    match = re.match(r"\A---\n(.*?)\n---\n(.*)\Z", text, re.DOTALL)
    if not match:
        raise ValueError(f"Missing frontmatter: {path.relative_to(ROOT)}")
    metadata = yaml.safe_load(match[1])
    if not isinstance(metadata, dict):
        raise ValueError(f"Invalid frontmatter: {path.relative_to(ROOT)}")
    for key in ("name", "description"):
        if not isinstance(metadata.get(key), str) or not metadata[key].strip():
            raise ValueError(f"Missing {key}: {path.relative_to(ROOT)}")
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", metadata["name"]):
        raise ValueError(f"Invalid name: {path.relative_to(ROOT)}")
    return metadata, match[2].strip()


def portable_text(body):
    body = body.replace("$ARGUMENTS", "USER_REQUEST")
    body = body.replace("/skill:speckit-", "$speckit-")
    body = body.replace("/speckit-", "$speckit-")
    body = body.replace("`/{command}`", "`${command}`")
    body = body.replace("When constructing slash commands", "When constructing Codex skill mentions")
    body = body.replace("`Read`", "파일 읽기 도구")
    body = body.replace("`Grep`", "내용 검색 도구")
    body = body.replace("`Glob`", "파일 경로 검색 도구")
    body = body.replace("`Bash`", "셸 실행 도구")
    body = body.replace("`Edit`", "파일 편집 도구")
    body = body.replace("`Write`", "파일 쓰기 도구")
    body = body.replace("파일 읽기 도구 하지 말고", "읽지 말고")
    body = body.replace("셸 실행 도구 로", "셸 실행 도구로")
    body = body.replace("파일 경로 검색 도구/내용 검색 도구으로", "경로·내용 검색으로")
    body = re.sub(
        r"\.specify/scripts/bash/(check-prerequisites|setup-plan|setup-tasks|create-new-feature)\.sh",
        r"bash .agents/scripts/run-speckit.sh \1",
        body,
    )
    return "\n".join(line.rstrip() for line in body.splitlines())



def agent_content(source):
    metadata, body = read_definition(source)
    name = metadata["name"]
    body = portable_text(body)
    if name == "code-reviewer":
        body = body.replace(
            "이 agent는 셸 실행 도구를 갖지 않는다 — 임의 코드 실행 없이 "
            "파일 읽기 도구/내용 검색 도구/파일 경로 검색 도구만으로 리뷰하며, "
            "리뷰 대상 diff나 변경 파일 목록은 호출하는 쪽(메인 대화)이 프롬프트에 포함해서 넘겨줘야 한다.",
            "리뷰 대상 diff와 변경 범위는 메인 에이전트가 전달한다. "
            "없으면 아래에 허용된 읽기 전용 git 명령으로 확인한다. "
            "셸을 쓰더라도 파일 수정·임의 코드 실행·빌드·테스트·네트워크 호출은 하지 않는다.",
        )
    if name == "docs-drift":
        body = body.replace(
            "이 agent는 셸 실행 도구를 갖지 않는다 — 임의 코드 실행 없이 "
            "파일 읽기 도구/내용 검색 도구/파일 경로 검색 도구만으로 대조한다.",
            "파일 읽기·경로 검색·내용 검색으로만 대조한다. "
            "전용 읽기 도구가 없는 Codex에서는 셸의 `rg`, `rg --files`, "
            "`Get-Content` 같은 읽기 명령을 사용할 수 있지만, "
            "파일 수정·프로젝트 스크립트·빌드·테스트·네트워크 호출은 하지 않는다.",
        )
    if name == "tdd-developer":
        body = body.replace(
            "판단 방법: 스테이징한 뒤 `git stash -k -u` 로 나머지를 치우고 "
            "`./gradlew compileTestJava` 가 통과하는지 보거나, 최소한 "
            "`git diff --cached` 로 테스트가 호출하는 심볼이 이미 HEAD에 존재하는지 확인한다.",
            "판단 방법: `git diff --cached`와 HEAD를 대조해 테스트가 참조하는 "
            "심볼이 HEAD 또는 함께 스테이징한 뼈대에 있는지 확인한다. "
            "다른 작업의 변경을 stash하거나 치우지 않는다.",
        )
        body = body.replace(
            "**네 구현은 반드시 `code-reviewer` 검토를 거친다.**",
            "**로직·보안·마이그레이션 변경이 대략 100줄 이상이면 "
            "메인 에이전트가 `code-reviewer` 검토를 수행한다. "
            "작은 수정·문서·설정 변경은 메인 에이전트가 직접 확인한다.**",
        )
        body = body.replace(
            "- 네 구현은 반드시 `code-reviewer` 검토를 거친다.",
            "- 로직·보안·마이그레이션 변경이 대략 100줄 이상이면 `code-reviewer` 검토를 거친다.",
        )
        body = re.sub(
            r"- 이 저장소는 3개 핵심 도메인.*?(?=\n- 헌법)",
            "- 테스트 현황과 우선순위는 실제 `backend/src/test/` 및 해당 기능의 "
            "`plan.md`를 확인한다. 과거 스냅샷의 테스트 수나 삭제된 도메인 예시를 "
            "현재 사실로 간주하지 않는다.",
            body,
            flags=re.DOTALL,
        )
        body = body.replace(
            "예: `FireAlertClassifier`, `DisasterCooldown`, `MissingPersonIdentity`, "
            "`AnimalIdentity` 같은 `domain/event/service`·`domain/risk` 하위의 정적 유틸/판정 클래스",
            "예: `domain/event/model`의 `DisasterCooldown`, `MissingPersonIdentity`, `AnimalIdentity`",
        )
    if name == "ui-fixer":
        body = body.replace(
            'MSYS_NO_PATHCONV=1 node scripts/ui-screenshot.mjs <path>',
            'MSYS_NO_PATHCONV=1 node scripts/ui-screenshot.mjs <path> "$RUN_DIR"',
        )
    context = (
        "Codex 적용 사항:\n"
        "- 작업 전에 AGENTS.md와 CLAUDE.md를 읽는다. Windows에서는 Bash 예시를 "
        "Git Bash에서 실행하거나 PowerShell 명령으로 바꾼다.\n"
        "- 모델·권한·MCP는 부모 세션을 따른다. Claude의 도구명이나 "
        "permissionMode를 권한 설정으로 사용하지 않는다.\n"
        "- 다른 에이전트의 호출은 메인 에이전트가 담당한다. "
        "후속 작업은 가능한 경우 기존 에이전트에 이어서 전달한다.\n"
        "- 공동 작성자 표기는 실제 사용자·세션 설정을 따른다. "
        "Claude 이름과 이메일을 사용하지 않는다.\n"
    )
    if name.startswith("ui-"):
        context += (
            "- 스크린샷은 이미지 보기 도구로 연다. 실행 시작 시 Bash에서 "
            '`RUN_ID=$(date +%Y%m%d-%H%M%S)-$$; RUN_DIR=.ui-check/$RUN_ID`를 한 번 정하고, '
            '모든 스크린샷 호출에 "$RUN_DIR"를 넘긴다. PowerShell에서는 같은 목적의 '
            "실행별 경로를 만든다. 직접 시작한 서버만 정리한다.\n"
        )
    instructions = context + "\n" + body + "\n"
    if "'''" in instructions:
        raise ValueError(f"TOML literal delimiter in {source}")
    result = (
        f"# Generated from {source.relative_to(ROOT).as_posix()}.\n"
        "# Edit the Claude source or .codex/scripts/sync-claude-agents.py, then regenerate.\n"
        f"name = {json.dumps(name)}\n"
        f"description = {json.dumps(metadata['description'], ensure_ascii=False)}\n"
        "developer_instructions = '''\n" + instructions + "'''\n"
    )
    tomllib.loads(result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check generated files without writing")
    args = parser.parse_args()
    outputs = {}
    agents = sorted((ROOT / ".claude/agents").glob("*.md"))
    if not agents:
        raise ValueError("Claude agent definitions are missing")
    for source in agents:
        outputs[AGENT_ROOT / f"{source.stem}.toml"] = agent_content(source)
    changed = []
    # Generate all content before writing so an invalid source cannot cause a partial conversion.
    for destination, content in outputs.items():
        if destination.exists() and destination.read_text(encoding="utf-8") == content:
            continue
        changed.append(destination.relative_to(ROOT).as_posix())
        if not args.check:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(content, encoding="utf-8", newline="\n")
    if args.check and changed:
        print("Out of date:\n" + "\n".join(changed), file=sys.stderr)
        return 1
    print(f"{'Checked' if args.check else 'Generated'} {len(agents)} agents; "
          f"{len(changed)} {'out of date' if args.check else 'updated'}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
