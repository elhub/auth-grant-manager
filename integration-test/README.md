# Document Flow Integration Tests

The Bash/Playwright image runs two independent flows sequentially:

- **v0:** move-in document, BankID signing, active grant, and metering-point scope.
- **v1:** energy-supplier framework agreement, BankID signing, approved document,
  and one persistent grant with the expected parties, scope, source, and validity.

`run.sh` validates configuration, runs both scenarios even if one fails, and returns
nonzero if any flow fails. Each scenario has an isolated temporary directory and
shares HTTP/signing helpers in `lib/common.sh`. Logs show key stages, resource IDs,
and final results, not full responses, PDFs, tokens, credentials, or browser call logs.
Keep shell tracing and Playwright debug/protocol logging disabled.

## Configuration

Set the shared variables and those for each selected flow. Running both flows is the default.

### Shared Variables

| Variable | Description |
| --- | --- |
| `USER_AGENT` | Test client's User-Agent header. |
| `SENDER_GLN` | Supplier GLN authorized by the token. |
| `TEST_TOKEN_URL` | Maskinporten test-token endpoint returning a raw JWT. |
| `MASKINPORTEN_ORG_NUMBER` | Supplier token's organization number. |
| `MASKINPORTEN_SCOPE` | Token scope, e.g. `elhub:balancesupplier`. |
| `BANKID_TOKEN_API` | BankID OAuth token endpoint. |
| `BANKID_CLIENT_ID`, `BANKID_CLIENT_SECRET` | BankID client credentials. |
| `BANKID_SCOPE` | `esign/nnin esign esign/qtsa`. |
| `BANKID_SIGNING_API` | BankID PAdES signing-order endpoint. |
| `BANKID_SIGN_BASE_URL` | Browser signing-site base URL. |
| `BANKID_REDIRECT_URL` | HTTPS signing return URL. |
| `NATIONAL_IDENTITY_NUMBER` | BankID test person and v0 document subject. |
| `BANKID_TEST_OTP`, `BANKID_TEST_PASSWORD` | BankID test person's credentials. |

### Flow-Specific Variables

| Variable | Required For | Description |
| --- | --- | --- |
| `ELHUB_BASE` | v0 | Grant Manager API base ending in `/access/v0`. |
| `METERING_POINT_ID` | v0 | Test metering point authorized by the move-in grant. |
| `ELHUB_V1_BASE` | v1 | Grant Manager API base ending in `/access/v1`. |
| `FRAMEWORK_PRINCIPAL_ORG_NUMBER` | v1 | Principal organization for the framework agreement. |
| `FRAMEWORK_OF_ORG_NUMBER` | v1 | Organization on whose behalf the framework agreement is signed. |

## Run

With all required variables populated in your local `.env`, run from the repository root:

```bash
docker build -f integration-test/Dockerfile -t integration-test:local .
docker run --rm --env-file .env integration-test:local
```

Select one flow by overriding the container command with
`bash integration-test/run.sh v0` or `bash integration-test/run.sh v1`.
