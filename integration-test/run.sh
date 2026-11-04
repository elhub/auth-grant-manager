#!/usr/bin/env bash
set -Eeuo pipefail
trap 'exit 143' TERM
trap 'exit 130' INT

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
selection="${1:-all}"
case "$selection" in
  all) scenarios=(authorization-document-flow.sh v1-framework-agreement.sh) ;;
  v0) scenarios=(authorization-document-flow.sh) ;;
  v1) scenarios=(v1-framework-agreement.sh) ;;
  *) printf 'Usage: bash integration-test/run.sh [all|v0|v1]\n' >&2; exit 2 ;;
esac

# Validate every selected flow before any remote resources are created.
required=(USER_AGENT SENDER_GLN NATIONAL_IDENTITY_NUMBER TEST_TOKEN_URL MASKINPORTEN_ORG_NUMBER
  MASKINPORTEN_SCOPE BANKID_CLIENT_ID BANKID_CLIENT_SECRET BANKID_SIGNING_API BANKID_TOKEN_API
  BANKID_SIGN_BASE_URL BANKID_TEST_OTP BANKID_TEST_PASSWORD BANKID_REDIRECT_URL BANKID_SCOPE)
[[ "$selection" == v1 ]] || required+=(ELHUB_BASE METERING_POINT_ID)
[[ "$selection" == v0 ]] || required+=(ELHUB_V1_BASE FRAMEWORK_PRINCIPAL_ORG_NUMBER FRAMEWORK_OF_ORG_NUMBER)
for name in "${required[@]}"; do
  [[ -n "${!name:-}" ]] || { printf 'Missing environment variable %s\n' "$name" >&2; exit 1; }
done

failed=0
for scenario in "${scenarios[@]}"; do
  if bash "$TEST_DIR/$scenario"; then
    :
  else
    failed=$((failed + 1))
  fi
done
if ((failed)); then
  printf '[suite] FAIL: %s of %s flows failed\n' "$failed" "${#scenarios[@]}" >&2
  exit 1
fi
printf '[suite] PASS: %s document flows completed\n' "${#scenarios[@]}"
