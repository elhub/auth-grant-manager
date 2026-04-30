#!/usr/bin/env python3
"""
Render skill evaluation results.
All rubric data (criteria, weights) comes from cases.yaml.
results.jsonl stores only: case_id, timestamp, path, and per-criterion {id, passed, notes}.

Usage:
  python3 eval/report.py            # full report
  python3 eval/report.py --last N   # last N runs
  python3 eval/report.py --case ID  # single case
"""

import argparse
import json
from pathlib import Path
import yaml

RESET="\033[0m"; BOLD="\033[1m"; DIM="\033[2m"
GREEN="\033[32m"; RED="\033[31m"; YELLOW="\033[33m"

def g(s): return f"{GREEN}{s}{RESET}"
def r(s): return f"{RED}{s}{RESET}"
def y(s): return f"{YELLOW}{s}{RESET}"
def b(s): return f"{BOLD}{s}{RESET}"
def d(s): return f"{DIM}{s}{RESET}"

ROOT = Path(__file__).parent.parent
CASES_FILE = ROOT / "eval" / "cases.yaml"
RESULTS_FILE = ROOT / "eval" / "results.jsonl"


def load_cases() -> dict:
    data = yaml.safe_load(CASES_FILE.read_text())
    return {c["id"]: c for c in data["cases"]}


def load_results(case_filter=None) -> list[dict]:
    if not RESULTS_FILE.exists():
        return []
    runs = [json.loads(l) for l in RESULTS_FILE.read_text().splitlines() if l.strip()]
    return [r for r in runs if not case_filter or r["case_id"] == case_filter]


def score(run: dict, case: dict) -> tuple[int, int]:
    weights = {c["id"]: c["weight"] for c in case["rubric"]}
    earned = sum(weights.get(r["id"], 0) for r in run["results"] if r["passed"])
    total  = sum(weights.values())
    return earned, total


def bar(pct: float, width=24) -> str:
    filled = round(pct * width)
    col = GREEN if pct >= 0.9 else YELLOW if pct >= 0.6 else RED
    return f"{col}{'█'*filled}{'░'*(width-filled)}{RESET} {b(f'{pct*100:.0f}%')}"


def render(runs: list[dict], cases: dict) -> None:
    if not runs:
        print(y("No results. Tell the Copilot CLI agent: evaluate skills"))
        return

    all_earned = sum(score(run, cases[run["case_id"]])[0] for run in runs if run["case_id"] in cases)
    all_total  = sum(score(run, cases[run["case_id"]])[1] for run in runs if run["case_id"] in cases)
    overall = all_earned / all_total if all_total else 0.0

    print(b("\n" + "─"*60))
    print(b("  SKILL EVAL REPORT"))
    print(b("─"*60))
    print(f"\n  Overall  {bar(overall)}   ({len(runs)} run{'s' if len(runs)!=1 else ''})")
    print(b("\n─"*60))

    for run in runs:
        case = cases.get(run["case_id"])
        if not case:
            continue
        earned, total = score(run, case)
        pct = earned / total if total else 0.0
        icon = g("✓") if pct >= 0.9 else r("✗") if pct < 0.6 else y("~")
        weights = {c["id"]: c["weight"] for c in case["rubric"]}
        criteria = {c["id"]: c["criterion"] for c in case["rubric"]}

        print(f"\n  {icon} {b(run['case_id'])}  {bar(pct)}")
        print(f"  {d(run.get('timestamp',''))}  {d(run.get('path',''))}")

        for cr in run["results"]:
            status = g("PASS") if cr["passed"] else r("FAIL") if cr["passed"] is False else y("SKIP")
            w = weights.get(cr["id"], "?")
            label = criteria.get(cr["id"], cr["id"])
            print(f"    {status}  {d(f'w={w}'):<12}  {label}")
            if not cr["passed"] and cr.get("notes"):
                print(f"           {d('→ ' + cr['notes'])}")

        print(f"  {d('Score:')} {earned}/{total}")

    print(b("\n" + "─"*60 + "\n"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--last", type=int)
    parser.add_argument("--case")
    args = parser.parse_args()

    cases = load_cases()
    runs = load_results(args.case)
    if args.last:
        runs = runs[-args.last:]
    render(runs, cases)


if __name__ == "__main__":
    main()
