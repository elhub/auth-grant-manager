import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.toId
import no.elhub.devxp.build.configuration.pipeline.constants.AgentScope
import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.dsl.elhubProject
import no.elhub.devxp.build.configuration.pipeline.jobs.customJob
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleVerify

val imageRepo = "auth/auth-grant-manager"
val dbDirectory = "./db"
val liquiEntryPoint = "db-changelog.yaml"
val gitOpsRepo = "https://github.com/elhub/auth"

elhubProject(group = Group.AUTH, name = "auth-grant-manager") {
    pipeline {

        sequential {
            gradleVerify {
                lintImage = "docker.jfrog.elhub.cloud/oxsecurity/megalinter:v8"
                enablePublishMetrics = false
            }
            customJob(AgentScope.LinuxAgentContext) {
                name = "Publish metrics to Opslevel"
                id(name.toId())
                steps {
                    script {
                        name = "OpsLevel Push"
                        scriptContent = """
#!/bin/bash
set -eu

echo "Fetching Sonar quality gate and forwarding it to OpsLevel"

# Required environment variables:
#   OPSLEVEL_TOKEN                                   — Bearer token for OpsLevel integration
#   SONAR_TOKEN                                      — Bearer token for SonarQube API
# Optional environment variables:
#   SONAR_HOST_URL                                   — defaults to https://sonar.elhub.cloud
#   SONAR_PROJECT_KEY                                — defaults to no.elhub.auth.auth-grant-manager

readonly CURL_RETRY_ARGS=(--retry 3 --retry-delay 1 --connect-timeout 10 --max-time 30)
readonly OPSLEVEL_INTEGRATION_URL="https://upload.opslevel.com/integrations/custom_event/88240eea-7f30-4f77-a2c7-a830c4777ce4"
readonly OPSLEVEL_TOKEN="${'$'}{OPSLEVEL_TOKEN}"
readonly SONAR_HOST_URL="${'$'}{SONAR_HOST_URL:-https://sonar.elhub.cloud}"
readonly SONAR_PROJECT_KEY="${'$'}{SONAR_PROJECT_KEY:-no.elhub.auth.auth-grant-manager}"

log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') - ${'$'}*" >&2
}

validate_required_env_vars() {
  local -a missing=()
  [[ -z "${'$'}{OPSLEVEL_TOKEN:-}" ]] && missing+=(OPSLEVEL_TOKEN)
  [[ -z "${'$'}{SONAR_TOKEN:-}" ]] && missing+=(SONAR_TOKEN)

  if (( ${'$'}{#missing[@]} > 0 )); then
    log "Missing required environment variables: ${'$'}{missing[*]}"
    exit 1
  fi
}

validate_dependencies() {
  local -a missing=()
  local cmd

  for cmd in curl jq; do
    command -v "${'$'}cmd" >/dev/null 2>&1 || missing+=("${'$'}cmd")
  done

  if (( ${'$'}{#missing[@]} > 0 )); then
    log "Missing required commands: ${'$'}{missing[*]}"
    exit 1
  fi
}

fetch_sonar_quality_gate() {
  local url="${'$'}{SONAR_HOST_URL}/api/qualitygates/project_status?projectKey=${'$'}{SONAR_PROJECT_KEY}"
  local result

  result=$(curl --silent --show-error --fail-with-body "${'$'}{CURL_RETRY_ARGS[@]}" \
    --url "${'$'}url" \
    --header "Authorization: Bearer ${'$'}SONAR_TOKEN")

  if [[ -z "${'$'}result" ]]; then
    log "Empty response from SonarQube quality gate endpoint."
    exit 1
  fi

  echo "${'$'}result" | jq . >&2
  echo "${'$'}result"
}

post_opslevel_event() {
  local payload="$1"
  local response

  response=$(curl --silent --show-error --fail-with-body "${'$'}{CURL_RETRY_ARGS[@]}" \
    --request POST \
    --url "${'$'}OPSLEVEL_INTEGRATION_URL" \
    --header 'Accept: application/json' \
    --header 'Content-Type: application/json' \
    --header "Authorization: Bearer ${'$'}OPSLEVEL_TOKEN" \
    --data "${'$'}payload")

  echo "${'$'}response"
}

main() {
  log "Starting OpsLevel quality gate update"
  validate_required_env_vars
  validate_dependencies

  local payload
  payload=$(fetch_sonar_quality_gate)

  log "Posting Sonar quality gate JSON to OpsLevel"
  local response
  response=$(post_opslevel_event "${'$'}payload")
  log "OpsLevel response: ${'$'}response"
  log "Done"
}

main "$@"

                        """.trimIndent()
                    }
                }
            }
        }
    }
}

