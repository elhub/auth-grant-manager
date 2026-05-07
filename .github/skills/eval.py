#!/usr/bin/env python3
"""
Skill evaluator for auth-grant-manager Copilot skills.

Checks each skill file for required patterns (assertions) and forbidden
patterns (anti-patterns) derived from real failures observed during
code generation.

Usage:
    python3 .github/skills/eval.py
    python3 .github/skills/eval.py --skill database-access-exposed-liquibase
    python3 .github/skills/eval.py --verbose
"""

import argparse
import os
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

# ── ANSI colours ──────────────────────────────────────────────────────────────
RESET  = "\033[0m"
BOLD   = "\033[1m"
GREEN  = "\033[32m"
RED    = "\033[31m"
YELLOW = "\033[33m"
CYAN   = "\033[36m"
DIM    = "\033[2m"

def green(s):  return f"{GREEN}{s}{RESET}"
def red(s):    return f"{RED}{s}{RESET}"
def yellow(s): return f"{YELLOW}{s}{RESET}"
def cyan(s):   return f"{CYAN}{s}{RESET}"
def bold(s):   return f"{BOLD}{s}{RESET}"
def dim(s):    return f"{DIM}{s}{RESET}"

PASS = green("✅ PASS")
FAIL = red("❌ FAIL")
WARN = yellow("⚠️  WARN")


# ── Test case model ────────────────────────────────────────────────────────────
@dataclass
class Assertion:
    name: str
    check: Callable[[str], bool]
    severity: str = "fail"   # "fail" or "warn"
    hint: str = ""            # shown on failure to guide skill improvement


@dataclass
class SkillTestCase:
    skill: str                          # directory name under .github/skills/
    assertions: list[Assertion] = field(default_factory=list)


# ── Test cases (derived from real generation failures) ─────────────────────────
TEST_CASES: list[SkillTestCase] = [

    SkillTestCase(
        skill="database-access-exposed-liquibase",
        assertions=[
            Assertion(
                "UUIDTable for UUID PK entities",
                lambda c: "UUIDTable" in c,
                hint="Models default to Table+uuid('id') which uses experimental kotlin.uuid.Uuid"
            ),
            Assertion(
                "Warns against Table + uuid('id')",
                lambda c: 'uuid("id")' in c or "uuid('id')" in c,  # mentioned as anti-pattern
                hint="Should explicitly say never use Table+uuid('id')"
            ),
            Assertion(
                "update() named where= parameter",
                lambda c: "update(where" in c,
                hint="update(where = { ... }) — positional lambda overload does not exist in Exposed v1"
            ),
            Assertion(
                "deleteAll import documented",
                lambda c: "deleteAll" in c,
                hint="org.jetbrains.exposed.v1.jdbc.deleteAll is not auto-imported"
            ),
            Assertion(
                "raise() for null unwrap (not flatMap)",
                lambda c: "raise(" in c,
                hint="Correct pattern: .bind() ?: raise(NotFoundError) — not .flatMap{}"
            ),
            Assertion(
                "withTransaction (not transaction{})",
                lambda c: "withTransaction" in c,
                hint="Always withTransaction from config/Database.kt — never blocking transaction{}"
            ),
            Assertion(
                "id.value for UUIDTable ResultRow mapping",
                lambda c: ".value" in c,
                hint="UUIDTable returns EntityID<UUID> — must call .value to get java.util.UUID"
            ),
            Assertion(
                "No .flatMap anti-pattern in positive examples",
                lambda c: not re.search(r"(?<!NOT\s)(?<!never\s)(?<!not\s)\.flatMap\s*\{", c, re.IGNORECASE),
                hint=".flatMap{} does not exist on Either — remove from any positive code examples"
            ),
            Assertion(
                "No newSuspendedTransaction",
                lambda c: "newSuspendedTransaction" not in c,
                hint="Use withTransaction — newSuspendedTransaction is the wrong API"
            ),
            Assertion(
                "DI binds to interface not impl",
                lambda c: "provide<" in c,
                hint="provide<Repository> { ExposedRepository() } — bind to interface"
            ),
        ]
    ),

    SkillTestCase(
        skill="testing-kotest",
        assertions=[
            Assertion(
                "shouldBeRight() returns value (documented)",
                lambda c: "returns the" in c.lower() and "shouldberight" in c.lower(),
                hint="shouldBeRight() returns the right value — models don't know this"
            ),
            Assertion(
                "No positive shouldBeRight{} block example",
                lambda c: not re.search(r"shouldBeRight\s*\{(?!.*[Nn]ever|.*[Nn]ot)", c),
                hint="shouldBeRight { it.x } block form does not exist — remove positive examples"
            ),
            Assertion(
                "coEvery for suspend functions",
                lambda c: "coEvery" in c,
                hint="every{} silently fails for suspend functions"
            ),
            Assertion(
                "clearMocks in beforeTest",
                lambda c: "clearMocks" in c,
                hint="Mocks must be reset between tests"
            ),
            Assertion(
                "deleteAll import noted",
                lambda c: "deleteAll" in c,
                hint="org.jetbrains.exposed.v1.jdbc.deleteAll must be explicitly imported"
            ),
            Assertion(
                "setupAppWith helper documented",
                lambda c: "setupAppWith" in c,
                hint="Route tests use setupAppWith{} from no.elhub.auth.Utils"
            ),
            Assertion(
                "Validate helpers listed",
                lambda c: "validateNotFoundResponse" in c or "validateForbiddenResponse" in c,
                hint="Test helpers like validateNotFoundResponse save boilerplate"
            ),
        ]
    ),

    SkillTestCase(
        skill="testing-layers",
        assertions=[
            Assertion(
                "Summary table present",
                lambda c: "| Route" in c and "| Handler" in c and "| Repository" in c,
                hint="Summary table gives a quick reference for which layer tests what"
            ),
            Assertion(
                "No positive shouldBeRight{} block example",
                lambda c: not re.search(r"shouldBeRight\s*\{(?!.*[Nn]ever|.*[Nn]ot)", c),
                hint="shouldBeRight { it.x } block form does not exist in this codebase"
            ),
            Assertion(
                "What to assert vs not assert (route)",
                lambda c: "do not assert" in c.lower() or "don't assert" in c.lower(),
                hint="Distinguish HTTP concerns from business logic"
            ),
            Assertion(
                "Repository tests use real DB",
                lambda c: "real" in c.lower() and "postgres" in c.lower(),
                hint="Repository tests must run against real Postgres — no mocking the DB"
            ),
        ]
    ),

    SkillTestCase(
        skill="json-api-compliance",
        assertions=[
            Assertion(
                "JsonApiResourceLinks (not JsonApiLinks)",
                lambda c: "JsonApiResourceLinks" in c,
                hint="JsonApiLinks is sealed — links DTOs must extend JsonApiResourceLinks"
            ),
            Assertion(
                "Warns against JsonApiLinks for DTOs",
                lambda c: "JsonApiLinks" in c and ("NOT" in c or "not" in c or "sealed" in c),
                hint="Must warn that JsonApiLinks cannot be extended outside the library"
            ),
            Assertion(
                "toNotFoundApiErrorResponse default message",
                lambda c: "toNotFoundApiErrorResponse" in c,
                hint="Default detail matches validateNotFoundResponse() test helper"
            ),
            Assertion(
                "OpenAPI update requirement",
                lambda c: "openapi" in c.lower() and ("must" in c.lower() or "required" in c.lower() or "non-negotiable" in c.lower()),
                hint="Every route change must update openapi.yaml"
            ),
            Assertion(
                "SingleDocument only for deserialising external responses",
                lambda c: "SingleDocument" in c and ("only" in c or "external" in c),
                hint="Never use SingleDocument<A> to generate app responses"
            ),
            Assertion(
                "No-relationships empty class pattern",
                lambda c: "JsonApiRelationships" in c and ("empty" in c or "class Foo" in c),
                hint="Resources with no relationships use empty class FooRelationships : JsonApiRelationships"
            ),
        ]
    ),

    SkillTestCase(
        skill="vertical-slice-architecture",
        assertions=[
            Assertion(
                "Action checklist present",
                lambda c: re.search(r"\d+\.", c) is not None and "Route" in c,
                hint="Step-by-step checklist prevents missing files"
            ),
            Assertion(
                "application.yaml registration step",
                lambda c: "application.yaml" in c or "application.yml" in c,
                hint="New modules must be registered before common.ModuleKt.module"
            ),
            Assertion(
                "openapi.yaml update step",
                lambda c: "openapi.yaml" in c or "openapi" in c.lower(),
                hint="OpenAPI spec must be updated for every route change"
            ),
            Assertion(
                "Auth method selection documented",
                lambda c: "authorizeMaskinporten" in c and "authorizeEndUser" in c,
                hint="Wrong auth method changes who can call the endpoint"
            ),
            Assertion(
                "Module.kt DI + routing wiring",
                lambda c: "Module.kt" in c,
                hint="Every feature needs a Module.kt to wire DI and routes"
            ),
        ]
    ),

    SkillTestCase(
        skill="functional-error-handling-arrow",
        assertions=[
            Assertion(
                "either { } + bind() pattern",
                lambda c: "either {" in c and "bind()" in c,
                hint="Core Arrow DSL pattern for chaining Either operations"
            ),
            Assertion(
                "raise() for short-circuit",
                lambda c: "raise(" in c,
                hint="raise() short-circuits inside either{} — use instead of returning Left"
            ),
            Assertion(
                "No runBlocking",
                lambda c: "runBlocking" not in c,
                hint="runBlocking is forbidden in production code"
            ),
            Assertion(
                "mapLeft for error transformation",
                lambda c: "mapLeft" in c,
                hint="Repository errors mapped to handler errors via mapLeft before bind()"
            ),
        ]
    ),

    SkillTestCase(
        skill="test-driven-development",
        assertions=[
            Assertion(
                "RED/GREEN/REFACTOR cycle",
                lambda c: "RED" in c and "GREEN" in c and "REFACTOR" in c,
                hint="TDD cycle must be explicit"
            ),
            Assertion(
                "Test must fail first",
                lambda c: "fail" in c.lower() and ("first" in c.lower() or "before" in c.lower()),
                hint="A test that passes immediately without implementation tests nothing"
            ),
            Assertion(
                "coEvery for suspend in examples",
                lambda c: "coEvery" in c,
                hint="Remind that suspend functions need coEvery not every"
            ),
        ]
    ),
]


# ── Runner ─────────────────────────────────────────────────────────────────────
def run_skill(tc: SkillTestCase, skills_root: Path, verbose: bool) -> tuple[int, int]:
    skill_path = skills_root / tc.skill / "SKILL.md"

    print(f"\n{bold(cyan('━' * 60))}")
    print(f"  {bold(tc.skill)}")
    print(f"{bold(cyan('━' * 60))}")

    if not skill_path.exists():
        print(f"  {red('FILE NOT FOUND:')} {skill_path}")
        return 0, len(tc.assertions)

    content = skill_path.read_text()
    passed = 0
    failed = 0

    for assertion in tc.assertions:
        try:
            ok = assertion.check(content)
        except Exception as e:
            ok = False
            assertion.hint = f"Check error: {e}"

        if ok:
            print(f"  {PASS}  {assertion.name}")
            passed += 1
        else:
            icon = FAIL if assertion.severity == "fail" else WARN
            print(f"  {icon}  {assertion.name}")
            if assertion.hint:
                print(f"         {dim('→ ' + assertion.hint)}")
            failed += 1

    return passed, failed


def main():
    parser = argparse.ArgumentParser(description="Evaluate Copilot skill files")
    parser.add_argument("--skill", help="Run only this skill (directory name)")
    parser.add_argument("--verbose", action="store_true", help="Show passing hints too")
    args = parser.parse_args()

    skills_root = Path(__file__).parent
    cases = TEST_CASES
    if args.skill:
        cases = [tc for tc in TEST_CASES if tc.skill == args.skill]
        if not cases:
            print(red(f"Unknown skill: {args.skill}"))
            sys.exit(1)

    print(f"\n{bold('🔍 Skill Evaluator — auth-grant-manager')}")
    print(dim(f"   Skills root: {skills_root}"))

    total_passed = 0
    total_failed = 0
    results: list[tuple[str, int, int]] = []

    for tc in cases:
        p, f = run_skill(tc, skills_root, args.verbose)
        total_passed += p
        total_failed += f
        results.append((tc.skill, p, f))

    # ── Summary ───────────────────────────────────────────────────────────────
    total = total_passed + total_failed
    pct = int(100 * total_passed / total) if total else 0

    print(f"\n{bold(cyan('━' * 60))}")
    print(f"  {bold('SUMMARY')}")
    print(f"{bold(cyan('━' * 60))}")

    for skill, p, f in results:
        t = p + f
        bar_len = 20
        filled = int(bar_len * p / t) if t else 0
        bar = green("█" * filled) + dim("░" * (bar_len - filled))
        status = green(f"{p}/{t}") if f == 0 else red(f"{p}/{t}")
        print(f"  {bar}  {status}  {skill}")

    print()
    if total_failed == 0:
        print(f"  {bold(green(f'All {total} assertions passed ✅'))}")
    else:
        score_colour = green if pct >= 80 else yellow if pct >= 60 else red
        print(f"  Score: {bold(score_colour(f'{pct}%'))}  "
              f"({green(str(total_passed))} passed, {red(str(total_failed))} failed)")

    print()
    sys.exit(0 if total_failed == 0 else 1)


if __name__ == "__main__":
    main()
