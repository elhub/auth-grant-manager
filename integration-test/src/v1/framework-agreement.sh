#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
set -Eeuo pipefail

TEST_NAME=v1
# shellcheck source=../lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"

require_env ELHUB_V1_BASE FRAMEWORK_PRINCIPAL_ORG_NUMBER FRAMEWORK_OF_ORG_NUMBER
[[ "${ELHUB_V1_BASE%/}" == */access/v1 ]] || fail 'ELHUB_V1_BASE must end with /access/v1'
initialize

# Create a unique agreement with a grant that expires after 24 hours.
CURRENT_STEP='creating framework agreement'
log 'Creating energy-supplier framework agreement'

BASE="${ELHUB_V1_BASE%/}"
RUN_ID="framework-e2e-$(date -u +%Y%m%dT%H%M%SZ)-${WORK_DIR##*.}"
VALID_TO=$(date -u -d '+1 day' +%Y-%m-%dT%H:%M:%SZ)

jq -n \
  --arg principal "$FRAMEWORK_PRINCIPAL_ORG_NUMBER" \
  --arg of "$FRAMEWORK_OF_ORG_NUMBER" \
  --arg reference "$RUN_ID" \
  --arg end "$VALID_TO" '{
  data: {
    type: "AuthorizationDocument",
    attributes: {
      documentType: "EnergySupplierFrameworkAgreement",
      externalReference: $reference,
      requestedGrantValidTo: $end
    },
    meta: {
      requestedPrincipal: $principal,
      requestedOf: $of,
      language: "nb"
    }
  }
}' > "$WORK_DIR/create-request.json"

api_request POST "$BASE/authorization-documents" 201 "$WORK_DIR/created.json" \
  -H 'Content-Type: application/json' --data-binary "@$WORK_DIR/create-request.json"

DOCUMENT_ID=$(json_id "$WORK_DIR/created.json" '.data.id')

# Framework agreements generate this scope; it is not supplied in the request.
EXPECTED_SCOPE='{
  "resourceType": "MeteringPointContract",
  "appliesTo": [],
  "allowedChanges": [
    {"attribute": "type", "value": ["EndUser", "EnergySupplier"]}
  ]
}'

jq -e '
  .data.type == "AuthorizationDocument" and
  .data.attributes.documentType == "EnergySupplierFrameworkAgreement" and
  .data.attributes.status == "Pending" and
  .data.attributes.externalReference == $reference and
  .data.attributes.requestedScopes == [$scope] and
  .data.relationships.requestedBy.data == {type: "MarketEntity", id: $gln} and
  .data.relationships.requestedPrincipal.data == {type: "Organization", id: $principal} and
  .data.relationships.requestedOf.data == {type: "Organization", id: $of}
' --arg reference "$RUN_ID" \
  --argjson scope "$EXPECTED_SCOPE" \
  --arg gln "$SENDER_GLN" \
  --arg principal "$FRAMEWORK_PRINCIPAL_ORG_NUMBER" \
  --arg of "$FRAMEWORK_OF_ORG_NUMBER" "$WORK_DIR/created.json" > /dev/null \
  || fail 'Created document does not match the requested framework agreement'

log "Created document=$DOCUMENT_ID"

# Sign through BankID and confirm the signed PDF with Grant Manager.
APPROVAL_DAY_BEFORE=$(TZ=Europe/Oslo date +%F)
sign_and_confirm "$BASE" "$DOCUMENT_ID"
APPROVAL_DAY_AFTER=$(TZ=Europe/Oslo date +%F)

# Approval must produce exactly one persistent grant and identify the signer.
api_request GET "$BASE/authorization-documents/$DOCUMENT_ID" 200 "$WORK_DIR/approved.json"

jq -e '
  .data.id == $id and
  .data.attributes.status == "Approved" and
  .data.attributes.requestedScopes == [$scope] and
  .data.relationships.approvedBy.data.type == "Person" and
  (.data.relationships.approvedBy.data.id | type == "string" and length > 0) and
  (.data.relationships.authorizationGrants.data | length) == 1 and
  .data.relationships.authorizationGrants.data[0].type == "PersistentGrant"
' --arg id "$DOCUMENT_ID" --argjson scope "$EXPECTED_SCOPE" "$WORK_DIR/approved.json" > /dev/null \
  || fail 'Approved document must identify the signer and one persistent grant'

GRANT_ID=$(json_id "$WORK_DIR/approved.json" '.data.relationships.authorizationGrants.data[0].id')

# Check the grant's permissions, parties, and link back to the agreement.
api_request GET "$BASE/authorization-grants/$GRANT_ID" 200 "$WORK_DIR/grant.json"

jq -e '
  .data.id == $grant and
  .data.type == "PersistentGrant" and
  .data.attributes.status == "Active" and
  .data.attributes.scopes == [$scope + {capability: "Write"}] and
  .data.relationships.principal == $created[0].data.relationships.requestedPrincipal and
  .data.relationships.grantedBy == $created[0].data.relationships.requestedOf and
  .data.relationships.grantedTo == $created[0].data.relationships.requestedBy and
  .data.relationships.approvedBy == $approved[0].data.relationships.approvedBy and
  .data.relationships.source.data == {type: "AuthorizationDocument", id: $document}
' --arg grant "$GRANT_ID" \
  --arg document "$DOCUMENT_ID" \
  --argjson scope "$EXPECTED_SCOPE" \
  --slurpfile created "$WORK_DIR/created.json" \
  --slurpfile approved "$WORK_DIR/approved.json" "$WORK_DIR/grant.json" > /dev/null \
  || fail 'Persistent grant has unexpected permissions, parties, or document source'

# Compare instants regardless of response offset; allow signing across midnight.
ACTUAL_END=$(jq -er '.data.attributes.validTo' "$WORK_DIR/grant.json")
[[ "$(date -d "$ACTUAL_END" +%s)" == "$(date -d "$VALID_TO" +%s)" ]] || fail 'Unexpected grant expiry'

ACTUAL_START=$(jq -er '.data.attributes.validFrom' "$WORK_DIR/grant.json")
START_DAY=$(TZ=Europe/Oslo date -d "$ACTUAL_START" +%F)

[[ "$START_DAY" == "$APPROVAL_DAY_BEFORE" || "$START_DAY" == "$APPROVAL_DAY_AFTER" ]] \
  || fail 'Unexpected grant start day'
[[ "$(TZ=Europe/Oslo date -d "$ACTUAL_START" +%T)" == '00:00:00' ]] \
  || fail 'Grant must start at Oslo midnight'

log "PASS: approved framework agreement; persistent grant=$GRANT_ID; parties, scope and validity verified"
