#!/usr/bin/env bash
# Sourced by each scenario so traps and temporary data stay isolated per flow.
INTEGRATION_TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CURRENT_STEP=configuration
WORK_DIR=
SIGN_ORDER_ID=

log() { printf '[%s] %s\n' "$TEST_NAME" "$*"; }
fail() { log "FAIL: $*" >&2; exit 1; }
require_env() {
  local name
  for name in "$@"; do
    [[ -n "${!name:-}" ]] || fail "Missing environment variable $name"
  done
}

cleanup() {
  local status=$?
  trap - EXIT
  if [[ -n "$SIGN_ORDER_ID" ]]; then
    curl -s --connect-timeout 10 --max-time 20 -o /dev/null -X DELETE \
      -H "Authorization: $BANKID_AUTH" "${BANKID_SIGNING_API}?sign_id=$SIGN_ORDER_ID" || true
  fi
  [[ -z "$WORK_DIR" ]] || rm -rf "$WORK_DIR"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
trap 'fail "$CURRENT_STEP (line $LINENO)"' ERR

http_request() {
  local method=$1 url=$2 expected=$3 output=$4 status
  shift 4
  if ! status=$(curl --silent --show-error --connect-timeout 20 --max-time 90 \
    --request "$method" --output "$output" --write-out '%{http_code}' "$@" "$url"); then
    fail "$CURRENT_STEP: HTTP request failed"
  fi
  [[ "$status" == "$expected" ]] || fail "$CURRENT_STEP: expected HTTP $expected, got $status"
}

api_request() {
  http_request "$@" -H "Authorization: Bearer $MASKINPORTEN_TOKEN" \
    -H "SenderGln: $SENDER_GLN" -H "User-Agent: $USER_AGENT" -H 'Accept: application/json'
}

json_id() {
  jq -er "$2 | select(type == \"string\" and test(\"^[A-Za-z0-9_-]+$\"))" "$1"
}

initialize() {
  require_env USER_AGENT SENDER_GLN NATIONAL_IDENTITY_NUMBER TEST_TOKEN_URL \
    MASKINPORTEN_ORG_NUMBER MASKINPORTEN_SCOPE BANKID_CLIENT_ID BANKID_CLIENT_SECRET \
    BANKID_SIGNING_API BANKID_TOKEN_API BANKID_SIGN_BASE_URL BANKID_TEST_OTP BANKID_TEST_PASSWORD \
    BANKID_REDIRECT_URL BANKID_SCOPE
  umask 077
  WORK_DIR=$(mktemp -d)
  CURRENT_STEP='obtaining tokens'
  jq -n --arg scope "$MASKINPORTEN_SCOPE" --arg org "$MASKINPORTEN_ORG_NUMBER" \
    '{scope: $scope, orgNumber: $org}' > "$WORK_DIR/token-request.json"
  http_request POST "$TEST_TOKEN_URL" 200 "$WORK_DIR/maskinporten-token" \
    -H 'Content-Type: application/json' --data-binary "@$WORK_DIR/token-request.json"
  MASKINPORTEN_TOKEN=$(< "$WORK_DIR/maskinporten-token")
  [[ "$MASKINPORTEN_TOKEN" =~ ^[^.[:space:]]+\.[^.[:space:]]+\.[^.[:space:]]+$ ]] || fail 'Token service did not return a JWT'
  http_request POST "$BANKID_TOKEN_API" 200 "$WORK_DIR/bankid-token.json" \
    --data-urlencode 'grant_type=client_credentials' \
    --data-urlencode "client_id=$BANKID_CLIENT_ID" --data-urlencode "client_secret=$BANKID_CLIENT_SECRET" \
    --data-urlencode "scope=$BANKID_SCOPE"
  jq -e '.access_token | type == "string" and length > 0' "$WORK_DIR/bankid-token.json" > /dev/null \
    || fail 'BankID token response has no access token'
  BANKID_AUTH=$(jq -er '(.token_type // "Bearer") + " " + .access_token' "$WORK_DIR/bankid-token.json")
  CURRENT_STEP='creating document'
}

sign_and_confirm() {
  local base=$1 document=$2
  CURRENT_STEP='downloading document PDF'
  api_request GET "$base/authorization-documents/$document.pdf" 200 "$WORK_DIR/original.pdf" -H 'Accept: application/pdf'
  [[ "$(LC_ALL=C head -c 5 "$WORK_DIR/original.pdf")" == '%PDF-' ]] || fail 'Downloaded file is not a PDF'
  CURRENT_STEP='creating BankID signing order'
  base64 -w 0 "$WORK_DIR/original.pdf" > "$WORK_DIR/pdf-base64"
  jq -n --rawfile pdf "$WORK_DIR/pdf-base64" --arg redirect "$BANKID_REDIRECT_URL" '{
    signProperties: {orderName: "Authorization document integration test", timeoutSeconds: 600, redirectUrl: $redirect},
    padesSignProperties: {addVisualSeals: false, extendToLTA: true, useConversion: false},
    documents: [{description: "Authorization document", pdf: $pdf}],
    resultContent: {requestSignerInfo: false}
  }' > "$WORK_DIR/order-request.json"
  http_request POST "$BANKID_SIGNING_API" 201 "$WORK_DIR/order.json" \
    -H "Authorization: $BANKID_AUTH" -H 'Content-Type: application/json' --data-binary "@$WORK_DIR/order-request.json"
  SIGN_ORDER_ID=$(json_id "$WORK_DIR/order.json" '.sign_id')
  CURRENT_STEP='signing with BankID'
  log 'Signing PDF with BankID'
  BANKID_SIGN_URL="${BANKID_SIGN_BASE_URL%/}/$SIGN_ORDER_ID" \
    node "$INTEGRATION_TEST_DIR/node_modules/.bin/tsx" "$INTEGRATION_TEST_DIR/src/lib/bankid-sign.ts"
  CURRENT_STEP='downloading signed PDF'
  http_request DELETE "${BANKID_SIGNING_API}?sign_id=$SIGN_ORDER_ID" 200 "$WORK_DIR/signed-order.json" \
    -H "Authorization: $BANKID_AUTH"
  SIGN_ORDER_ID=
  jq -er '.signingResults | select(length == 1) | .[0].padesSignedPdf | select(type == "string" and length > 0)' \
    "$WORK_DIR/signed-order.json" | base64 --decode > "$WORK_DIR/signed.pdf"
  [[ "$(LC_ALL=C head -c 5 "$WORK_DIR/signed.pdf")" == '%PDF-' ]] || fail 'Signed file is not a PDF'
  CURRENT_STEP='confirming signed document'
  api_request PUT "$base/authorization-documents/$document.pdf" 204 "$WORK_DIR/confirmed" \
    -H 'Content-Type: application/pdf' --data-binary "@$WORK_DIR/signed.pdf"
  log 'BankID signature accepted'
  CURRENT_STEP='verifying document and grant'
}
