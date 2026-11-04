#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
set -Eeuo pipefail

TEST_NAME=v0
# shellcheck source=lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
require_env ELHUB_BASE METERING_POINT_ID
initialize

log "Creating move-in authorization document"
jq -n --arg gln "$SENDER_GLN" --arg nin "$NATIONAL_IDENTITY_NUMBER" --arg mp "$METERING_POINT_ID" '{
  data: {
    type: "AuthorizationDocument",
    attributes: {documentType: "MoveInAndChangeOfBalanceSupplierForPerson"},
    meta: {
      requestedBy: {idType: "GlobalLocationNumber", idValue: $gln},
      requestedFrom: {idType: "NationalIdentityNumber", idValue: $nin},
      requestedTo: {idType: "NationalIdentityNumber", idValue: $nin},
      requestedFromName: "Ola Normann",
      requestedForMeteringPointId: $mp,
      requestedForMeteringPointAddress: "Dream Address 123",
      balanceSupplierName: "Greatest Balance Supplier of All",
      balanceSupplierContractName: "Spot price plus some hidden cost"
    }
  }
}' > "$WORK_DIR/create-request.json"
BASE="${ELHUB_BASE%/}"
api_request POST "$BASE/authorization-documents" 201 "$WORK_DIR/created.json" \
  -H 'Content-Type: application/json' --data-binary "@$WORK_DIR/create-request.json"
jq -e '.data.type == "AuthorizationDocument" and .data.attributes.status == "Pending"' \
  "$WORK_DIR/created.json" > /dev/null || fail 'Created document must be pending'
DOCUMENT_ID=$(json_id "$WORK_DIR/created.json" '.data.id')
log "Created document=$DOCUMENT_ID"

sign_and_confirm "$BASE" "$DOCUMENT_ID"
api_request GET "$BASE/authorization-documents/$DOCUMENT_ID" 200 "$WORK_DIR/approved.json"
jq -e '.data.id == $id and .data.attributes.status == "Signed"' --arg id "$DOCUMENT_ID" \
  "$WORK_DIR/approved.json" > /dev/null || fail 'Document must be signed after confirmation'
GRANT_ID=$(json_id "$WORK_DIR/approved.json" '.data.relationships.authorizationGrant.data.id')
api_request GET "$BASE/authorization-grants/$GRANT_ID" 200 "$WORK_DIR/grant.json"
jq -e '
  .data.id == $grant and .data.type == "AuthorizationGrant" and .data.attributes.status == "Active" and
  .data.relationships.source.data == {type: "AuthorizationDocument", id: $document}
' --arg grant "$GRANT_ID" --arg document "$DOCUMENT_ID" "$WORK_DIR/grant.json" > /dev/null \
  || fail 'Expected an active grant sourced from the signed document'
api_request GET "$BASE/authorization-grants/$GRANT_ID/scopes" 200 "$WORK_DIR/scopes.json"
jq -e '
  (.data | length) == 1 and .data[0].type == "AuthorizationScope" and
  .data[0].attributes.permissionType == "MoveInAndChangeOfBalanceSupplierForPerson" and
  .data[0].relationships.authorizedResources.data == [{type: "MeteringPoint", id: $mp}]
' --arg mp "$METERING_POINT_ID" "$WORK_DIR/scopes.json" > /dev/null \
  || fail 'Grant must have the expected metering-point scope'
log "PASS: signed document; active grant=$GRANT_ID; metering-point scope verified"
