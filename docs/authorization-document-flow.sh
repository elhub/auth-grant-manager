#!/usr/bin/env bash
# Demo document flow client script with BankID test WYSIWYS
# Bash/cURL equivalent of authorization-document-flow.http
#
# Quick start:
# 1. Fill in the required variables below (or export them before running).
# 2. Run the script: ./authorization-document-flow.sh

set -euo pipefail

# ---------------------------------------------------------------------------
# Required variables – fill in or export before running
# ---------------------------------------------------------------------------
USER_AGENT="${USER_AGENT:-}"
SENDER_GLN="${SENDER_GLN:-}"
MASKINPORTEN_TOKEN="${MASKINPORTEN_TOKEN:-}"
NATIONAL_IDENTITY_NUMBER="${NATIONAL_IDENTITY_NUMBER:-}"
BANKID_CLIENT_ID="${BANKID_CLIENT_ID:-}"
BANKID_CLIENT_SECRET="${BANKID_CLIENT_SECRET:-}"

METERING_POINT_ID="${METERING_POINT_ID:-}"

ELHUB_ENV="${ELHUB_ENV:-test9}"
BANKID_SIGNING_API="https://api.preprod.esign-stoetest.cloud/v1/signdoc/pades"
BANKID_TOKEN_API="https://auth.current.bankid.no/auth/realms/current/protocol/openid-connect/token"

ELHUB_BASE="https://auth-grant-manager.${ELHUB_ENV}.elhub.cloud/access/v0"

for var in USER_AGENT SENDER_GLN MASKINPORTEN_TOKEN NATIONAL_IDENTITY_NUMBER \
           BANKID_CLIENT_ID BANKID_CLIENT_SECRET METERING_POINT_ID; do
  if [[ -z "${!var}" ]]; then
    echo "ERROR: \$${var} is not set." >&2
    exit 1
  fi
done

# ---------------------------------------------------------------------------
# Step 1: Create authorization document
# ---------------------------------------------------------------------------
echo "==> Creating authorization document..."
CREATE_RESPONSE=$(curl --silent \
  -X POST "${ELHUB_BASE}/authorization-documents" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${MASKINPORTEN_TOKEN}" \
  -H "SenderGln: ${SENDER_GLN}" \
  -H "User-Agent: ${USER_AGENT}" \
  -d '{
    "data": {
      "type": "AuthorizationDocument",
      "attributes": {
        "documentType": "MoveInAndChangeOfBalanceSupplierForPerson"
      },
      "meta": {
        "requestedBy": {
          "idType": "GlobalLocationNumber",
          "idValue": "'"${SENDER_GLN}"'"
        },
        "requestedFrom": {
          "idType": "NationalIdentityNumber",
          "idValue": "'"${NATIONAL_IDENTITY_NUMBER}"'"
        },
        "requestedTo": {
          "idType": "NationalIdentityNumber",
          "idValue": "'"${NATIONAL_IDENTITY_NUMBER}"'"
        },
        "requestedFromName": "Ola Normann",
        "requestedForMeteringPointId": "'"${METERING_POINT_ID}"'",
        "requestedForMeteringPointAddress": "Dream Address 123",
        "balanceSupplierName": "Greatest Balance Supplier of All",
        "balanceSupplierContractName": "Spot price plus some hidden cost"
      }
    }
  }')

echo $CREATE_RESPONSE
DOCUMENT_ID=$(echo "${CREATE_RESPONSE}" | jq -r '.data.id')
echo "    documentId = ${DOCUMENT_ID}"

# ---------------------------------------------------------------------------
# Step 2: Download the PDF authorization document (Elhub-signed)
# ---------------------------------------------------------------------------
echo "==> Downloading Elhub-signed PDF..."
curl --silent --fail \
  -X GET "${ELHUB_BASE}/authorization-documents/${DOCUMENT_ID}.pdf" \
  -H "Authorization: Bearer ${MASKINPORTEN_TOKEN}" \
  -H "SenderGln: ${SENDER_GLN}" \
  -o elhub-signed.pdf
echo "    Saved to elhub-signed.pdf"
# Verify Elhub signature: pdfsig -nocert elhub-signed.pdf

# ---------------------------------------------------------------------------
# Step 3: Obtain BankID token
# ---------------------------------------------------------------------------
echo "==> Fetching BankID token..."
TOKEN_RESPONSE=$(curl --silent --fail \
  -X POST "${BANKID_TOKEN_API}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "client_id=${BANKID_CLIENT_ID}" \
  --data-urlencode "client_secret=${BANKID_CLIENT_SECRET}" \
  --data-urlencode "scope=esign/nnin esign esign/qtsa")

SIGNATURES_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
SIGNATURES_TOKEN_TYPE=$(echo "${TOKEN_RESPONSE}" | jq -r '.token_type // "Bearer"')
echo "    Token type: ${SIGNATURES_TOKEN_TYPE}"

# ---------------------------------------------------------------------------
# Step 4: Upload sign order to BankID
# Base64-encode the downloaded PDF
# ---------------------------------------------------------------------------
echo "==> Uploading sign order to BankID..."
BASE64_PDF=$(base64 -w 0 elhub-signed.pdf)

SIGN_RESPONSE=$(curl --silent --fail \
  -X POST "${BANKID_SIGNING_API}" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "Authorization: ${SIGNATURES_TOKEN_TYPE} ${SIGNATURES_TOKEN}" \
  -d '{
    "signProperties": {
      "orderName": "Authorization document",
      "timeoutSeconds": 1800,
      "redirectUrl": "https://example.test/signing/return"
    },
    "padesSignProperties": {
      "addVisualSeals": false,
      "extendToLTA": true,
      "useConversion": false
    },
    "documents": [
      {
        "description": "Authorization document",
        "pdf": "'"${BASE64_PDF}"'"
      }
    ],
    "resultContent": {
      "requestSignerInfo": false
    }
  }')

SIGN_ORDER_ID=$(echo "${SIGN_RESPONSE}" | jq -r '.sign_id')
echo "    signOrderId = ${SIGN_ORDER_ID}"
echo "    Approve URL (preprod): https://web.preprod.esign-stoetest.cloud/${SIGN_ORDER_ID}"

read -p "Sign via the above URL, and hit Enter when done."
# ---------------------------------------------------------------------------
# Step 5: Check status of the sign order
# Run manually / in a poll loop after the end user has signed.
# ---------------------------------------------------------------------------
echo ""
echo "==> Checking sign order status "
curl --silent --fail \
  -X GET "${BANKID_SIGNING_API}?sign_id=${SIGN_ORDER_ID}" \
  -H "Accept: application/json" \
  -H "Authorization: ${SIGNATURES_TOKEN_TYPE} ${SIGNATURES_TOKEN}" \
  | jq .

# ---------------------------------------------------------------------------
# Step 6: Download the signed document from BankID (DELETE = download+delete)
# ---------------------------------------------------------------------------
echo ""
echo "==> Downloading signed document from BankID..."
curl --silent --fail \
  -X DELETE "${BANKID_SIGNING_API}?sign_id=${SIGN_ORDER_ID}" \
  -H "Accept: application/json" \
  -H "Authorization: ${SIGNATURES_TOKEN_TYPE} ${SIGNATURES_TOKEN}" \
  -o sign-order-response.json
echo "    Saved to sign-order-response.json"

# Decode the base64-encoded signed PDF
jq -r '.signingResults[0].padesSignedPdf' sign-order-response.json | base64 --decode > bankid-signed.pdf
echo "    Decoded signed PDF to bankid-signed.pdf"
# Inspect signature: pdfsig -nocert bankid-signed.pdf

# ---------------------------------------------------------------------------
# Step 7: Upload the BankID-signed PDF back to Auth Grant Manager
# ---------------------------------------------------------------------------
echo "==> Uploading BankID-signed PDF to Auth Grant Manager..."
curl --silent --fail \
  -X PUT "${ELHUB_BASE}/authorization-documents/${DOCUMENT_ID}.pdf" \
  -H "Authorization: Bearer ${MASKINPORTEN_TOKEN}" \
  -H "SenderGln: ${SENDER_GLN}" \
  -H "Content-Type: application/pdf" \
  --data-binary @bankid-signed.pdf

# ---------------------------------------------------------------------------
# Step 8: Fetch the document to get the created AuthorizationGrant
# ---------------------------------------------------------------------------
echo "==> Fetching authorization document..."
DOC_RESPONSE=$(curl --silent --fail \
  -X GET "${ELHUB_BASE}/authorization-documents/${DOCUMENT_ID}" \
  -H "Authorization: Bearer ${MASKINPORTEN_TOKEN}" \
  -H "SenderGln: ${SENDER_GLN}")

GRANT_ID=$(echo "${DOC_RESPONSE}" | jq -r '.data.relationships.authorizationGrant.data.id')
echo "    grantId = ${GRANT_ID}"

# ---------------------------------------------------------------------------
# Step 9: Fetch the grant
# ---------------------------------------------------------------------------
echo "==> Fetching authorization grant..."
curl --silent --fail \
  -X GET "${ELHUB_BASE}/authorization-grants/${GRANT_ID}" \
  -H "Authorization: Bearer ${MASKINPORTEN_TOKEN}" \
  -H "SenderGln: ${SENDER_GLN}" \
  | jq .

# ---------------------------------------------------------------------------
# Step 10: Fetch the grant scopes
# ---------------------------------------------------------------------------
echo "==> Fetching grant scopes..."
curl --silent --fail \
  -X GET "${ELHUB_BASE}/authorization-grants/${GRANT_ID}/scopes" \
  -H "Authorization: Bearer ${MASKINPORTEN_TOKEN}" \
  -H "SenderGln: ${SENDER_GLN}" \
  | jq .

echo ""
echo "Done."
