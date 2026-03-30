#!/usr/bin/env python3
"""
Print the judge prompt for a case, built from cases.yaml.
Used by the Copilot CLI agent during 'evaluate skills' to avoid hardcoding rubrics.

Usage:
  python3 eval/judge_prompt.py <case-id> <generated-code>
"""

import sys
from pathlib import Path
import yaml

ROOT = Path(__file__).parent.parent
CASES_FILE = ROOT / "eval" / "cases.yaml"


def load_case(case_id: str) -> dict:
    data = yaml.safe_load(CASES_FILE.read_text())
    for c in data["cases"]:
        if c["id"] == case_id:
            return c
    raise SystemExit(f"Unknown case: {case_id}. Available: {[c['id'] for c in data['cases']]}")


def build_prompt(case: dict, generated_code: str) -> str:
    rubric_lines = []
    for i, c in enumerate(case["rubric"], 1):
        rubric_lines.append(f'{i}. id: "{c["id"]}"')
        rubric_lines.append(f'   criterion: "{c["criterion"]}"')
        rubric_lines.append(f'   weight: {c["weight"]}')
        rubric_lines.append(f'   pass_when: {c["pass_when"].strip()}')
        rubric_lines.append("")

    return f"""You are a strict code reviewer scoring generated Kotlin code against a rubric.

For each criterion, return PASS or FAIL with a one-line justification. Be precise and literal.

Return ONLY a JSON array, no prose. Format:
[{{"id": "...", "passed": true/false, "notes": "..."}}]

--- GENERATED CODE ---

{generated_code}

--- RUBRIC ---

{chr(10).join(rubric_lines)}"""


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 eval/judge_prompt.py <case-id> [generated-code-or-file]")
        sys.exit(1)

    case_id = sys.argv[1]
    case = load_case(case_id)

    if len(sys.argv) >= 3:
        arg = sys.argv[2]
        path = Path(arg)
        if path.exists() and path.is_file():
            generated_code = path.read_text()
        elif path.exists() and path.is_dir():
            parts = []
            for f in sorted(path.rglob("*")):
                if f.is_file():
                    try:
                        parts.append(f"FILE: {f.name}\n```\n{f.read_text()}\n```")
                    except Exception:
                        pass
            generated_code = "\n\n".join(parts)
        else:
            generated_code = arg
    else:
        generated_code = "<paste generated code here>"

    print(build_prompt(case, generated_code))


if __name__ == "__main__":
    main()
