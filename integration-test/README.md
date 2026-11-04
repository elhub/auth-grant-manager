# Document Flow Integration Tests

The existing Bash/Playwright image runs two independent flows sequentially:

- **v0:** move-in document, BankID signing, active grant, and metering-point scope.
- **v1:** energy-supplier framework agreement, BankID signing, approved document,
  and one persistent grant with the expected parties, scope, source, and validity.

`run.sh` validates configuration, runs both scenarios even if one fails, and returns
nonzero if any flow fails. Each scenario has an isolated temporary directory and
shares HTTP/signing helpers in `lib/common.sh`. Logs show key stages, resource IDs,
and final results, not full responses, PDFs, tokens, credentials, or browser call logs.
Keep shell tracing and Playwright debug/protocol logging disabled.

## Configuration

Reuse the existing v0 variables from `.env` or Kubernetes:

- `ELHUB_BASE`: v0 API base.
- `USER_AGENT`, `SENDER_GLN`, `METERING_POINT_ID`, `NATIONAL_IDENTITY_NUMBER`.
- `TEST_TOKEN_URL`, `MASKINPORTEN_ORG_NUMBER`, `MASKINPORTEN_SCOPE`.
- `BANKID_SIGNING_API`, `BANKID_TOKEN_API`, `BANKID_SIGN_BASE_URL`.
- `BANKID_CLIENT_ID`, `BANKID_CLIENT_SECRET`, `BANKID_TEST_OTP`, `BANKID_TEST_PASSWORD`.

Additional configuration:

- `ELHUB_V1_BASE`: API base ending in `/access/v1`.
- `FRAMEWORK_PRINCIPAL_ORG_NUMBER`, `FRAMEWORK_OF_ORG_NUMBER`: framework parties.
  The agreed test9 fixture uses `MASKINPORTEN_ORG_NUMBER` for both.
- `BANKID_REDIRECT_URL`: signing-order return URL, previously fixed in the script.
- `BANKID_SCOPE`: signing token scopes, previously fixed in the script.

Endpoints and party fixtures have no hardcoded defaults. Document-specific demo
text, Norwegian language, and expected permission values are fixed scenario inputs.
Both flows use the same BankID credentials and the existing Kubernetes Secret.

## Run

From the repository root:

```bash
set -a
source .env
set +a
export ELHUB_V1_BASE="${ELHUB_BASE%/v0}/v1"
export FRAMEWORK_PRINCIPAL_ORG_NUMBER="$MASKINPORTEN_ORG_NUMBER"
export FRAMEWORK_OF_ORG_NUMBER="$MASKINPORTEN_ORG_NUMBER"
export BANKID_REDIRECT_URL=https://example.test/signing/return
export BANKID_SCOPE='esign/nnin esign esign/qtsa'

docker build -f integration-test/Dockerfile -t integration-test:local .
docker run --rm --env-file .env \
  -e ELHUB_V1_BASE -e FRAMEWORK_PRINCIPAL_ORG_NUMBER -e FRAMEWORK_OF_ORG_NUMBER \
  -e BANKID_REDIRECT_URL -e BANKID_SCOPE integration-test:local
```

Select one flow by overriding the container command with
`bash integration-test/run.sh v0` or `bash integration-test/run.sh v1`.
For host execution, install the Node dependencies with `npm ci` in this directory,
install matching Chromium/native dependencies, and run `bash integration-test/run.sh`.
`curl`, `jq`, Bash, GNU coreutils, and Node are required on the host.
Local runs can bypass proxies; Kubernetes continues using the chart's proxy settings.

## Deployment and Data

The same TeamCity Docker build, image repository, PostSync Job, and CronJob are
used for both flows. Prepared changes in `../auth` provide the five additional
configuration values. No JVM, Gradle module, separate test image, registry, or new
Kubernetes Job is needed.

Requests have bounded timeouts, validate exact HTTP statuses, and do not retry
resource-creating operations. BankID orders are deleted on download, with best-effort
cleanup on failure and a 10-minute expiry. Local temporary files are always removed.
The container uses Tini to forward termination signals to the runner and browser.
Documents/grants remain in test9. V1 grants expire after 24 hours; the legacy v0 flow
retains its existing validity behavior. Kubernetes Job TTL does not delete API data.

The v1 test does not prove organization signing authority: that service policy is
currently a placeholder. Avoid concurrent manual, scheduled, or PostSync runs
because they share BankID fixtures; CronJob `Forbid` only protects its own runs.
