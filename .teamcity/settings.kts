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
echo "Do the things!"

# Posts SonarQube metrics to OpsLevel via a custom event integration.
#
# Required environment variables:
#   OPSLEVEL_TOKEN                                   — Bearer token for OpsLevel integration
#   SONAR_TOKEN, SONAR_HOST_URL                      — required for SonarQube metrics
#   SKIP_CODE_COVERAGE=true                           — omit unit test coverage metric
set -eu
# Constants
readonly OPSLEVEL_FILE="opslevel.yml"
readonly CURL_RETRY_ARGS=(--retry 3 --retry-delay 1 --connect-timeout 10 --max-time 30)
readonly OPSLEVEL_INTEGRATION_URL="https://app.opslevel.com/"
readonly OPSLEVEL_TOKEN="${'$'}{OPSLEVEL_TOKEN}"

# Logging
log() {
  echo "$(date '+%%Y-%%m-%%d %%H:%%M:%%S') - $*" >&2
}

# Validation
validate_required_env_vars() {
  local -a missing=()
  [[ -z "${'$'}{OPSLEVEL_TOKEN:-}" ]]           && missing+=(OPSLEVEL_TOKEN)
  [[ -z "${'$'}{SONAR_TOKEN:-}" ]]             && missing+=(SONAR_TOKEN)
  [[ -z "${'$'}{SONAR_HOST_URL:-}" ]]          && missing+=(SONAR_HOST_URL)

  if (( ${'$'}{#missing[@]} > 0 )); then
    log "Missing required environment variables: ${'$'}{missing[*]}"
    exit 1
  fi
}

validate_dependencies() {
  local -a missing=()
  local cmd

  for cmd in curl jq awk grep sed; do
    command -v "${'$'}cmd" >/dev/null 2>&1 || missing+=("${'$'}cmd")
  done

  if (( ${'$'}{#missing[@]} > 0 )); then
    log "Missing required commands: ${'$'}{missing[*]}"
    exit 1
  fi
}

# OpsLevel helpers
post_opslevel_event() {
  local payload="$1"
  local response

  if ! response=$(curl --silent --show-error --fail-with-body "${'$'}{CURL_RETRY_ARGS[@]}" \
    --request POST \
    --url "${'$'}OPSLEVEL_INTEGRATION_URL" \
    --header 'Accept: application/json' \
    --header 'Content-Type: application/json' \
    --header "Authorization: Bearer ${'$'}OPSLEVEL_TOKEN" \
    --data "${'$'}payload"); then
    log "Failed to post event to OpsLevel. Response: ${'$'}response"
    exit 1
  fi

  echo "${'$'}response"
}

read_opslevel_alias() {
  if [[ ! -f "${'$'}OPSLEVEL_FILE" ]]; then
    log "${'$'}OPSLEVEL_FILE not found — cannot identify service."
    exit 1
  fi

  local alias

  # Try the first entry under 'aliases:' (component or service section)
  alias=$(awk '
    /^[[:space:]]*aliases:/ { in_aliases=1; next }
    in_aliases && /^[[:space:]]*-[[:space:]]/ {
      sub(/^[[:space:]]*-[[:space:]]*/, "")
      gsub(/[[:space:]]+$/, "")
      gsub(/["\x27]/, "")
      print; exit
    }
    in_aliases && !/^[[:space:]]/ { in_aliases=0 }
    in_aliases && /^[[:space:]]*[^-]/ { in_aliases=0 }
  ' "${'$'}OPSLEVEL_FILE")

  # Fall back to 'name:' under component: or service:
  if [[ -z "${'$'}alias" ]]; then
    alias=$(awk '
      /^[[:space:]]*(component|service):[[:space:]]*$/ { in_section=1; next }
      in_section && /^[[:space:]]*name:[[:space:]]/ {
        sub(/^[[:space:]]*name:[[:space:]]*/, "")
        gsub(/[[:space:]]+$/, "")
        gsub(/["\x27]/, "")
        print; exit
      }
      in_section && /^[^[:space:]]/ { in_section=0 }
    ' "${'$'}OPSLEVEL_FILE")
  fi

  if [[ -z "${'$'}alias" ]]; then
    log "Could not determine service alias from ${'$'}OPSLEVEL_FILE."
    exit 1
  fi

  echo "${'$'}alias"
}

# SonarQube helpers
read_sonar_properties() {
  local file=".scannerwork/report-task.txt"

  if [[ ! -f "${'$'}file" ]]; then
    log "${'$'}file not found — skipping SonarQube metrics."
    return 1
  fi

  local ce_task_url project_key
  ce_task_url=$(grep '^ceTaskUrl=' "${'$'}file" | cut -d'=' -f2-)
  project_key=$(grep '^projectKey=' "${'$'}file" | cut -d'=' -f2-)

  if [[ -z "${'$'}ce_task_url" || -z "${'$'}project_key" ]]; then
    log "ceTaskUrl or projectKey missing from ${'$'}file."
    exit 1
  fi

  printf '%%s|%%s' "${'$'}ce_task_url" "${'$'}project_key"
}

poll_sonarqube_task() {
  local ce_task_url="$1"
  local max_wait=30
  local start_time status elapsed response

  start_time=$(date +%%s)
  log "Polling SonarQube task: ${'$'}ce_task_url"

  while true; do
    response=$(curl -s "${'$'}ce_task_url" --header "Authorization: Bearer ${'$'}SONAR_TOKEN") \
      || { log "Request to ${'$'}ce_task_url failed."; exit 1; }

    status=$(echo "${'$'}response" | jq -r '.task.status')
    log "Task status: ${'$'}status"

    [[ "${'$'}status" == "SUCCESS" ]] && { log "SonarQube task succeeded."; break; }

    elapsed=$(( $(date +%%s) - start_time ))
    if (( elapsed >= max_wait )); then
      log "Timeout: task did not succeed within ${'$'}{max_wait}s."
      exit 1
    fi

    sleep 1
  done
}

get_sonar_metrics() {
  local project_key="$1"
  local metric_keys="coverage,complexity,software_quality_security_issues"
  metric_keys+=",software_quality_security_rating,software_quality_reliability_rating"
  metric_keys+=",software_quality_maintainability_rating,duplicated_lines_density"
  metric_keys+=",security_hotspots,security_review_rating"

  local url="${'$'}{SONAR_HOST_URL}/api/measures/component?component=${'$'}{project_key}&metricKeys=${'$'}{metric_keys}"
  local result

  result=$(curl -s "${'$'}url" --header "Authorization: Bearer ${'$'}SONAR_TOKEN")

  if [[ -z "${'$'}result" ]]; then
    log "Empty response from SonarQube metrics endpoint."
    exit 1
  fi

  echo "${'$'}result"
}

extract_sonar_metric() {
  local json="$1" key="$2"
  echo "${'$'}json" | jq -r --arg k "${'$'}key" \
    '(.component.measures // [])[] | select(.metric == ${'$'}k) | .value'
}

compute_quality_gate() {
  local reliability_rating="$1"
  local maintainability_rating="$2"
  local duplicated_lines_density="$3"
  local percentage=0

  # Reliability rating: (5 - rating) * 10 when present
  if [[ -n "${'$'}reliability_rating" && "${'$'}reliability_rating" != "null" ]]; then
    percentage=$((percentage + ((5 - ${'$'}{reliability_rating%%%%.*}) * 10)))
  fi

  # Maintainability rating: (5 - rating) * 10 when present
  if [[ -n "${'$'}maintainability_rating" && "${'$'}maintainability_rating" != "null" ]]; then
    percentage=$((percentage + ((5 - ${'$'}{maintainability_rating%%%%.*}) * 10)))
  fi

  # Duplicated lines density: missing => +20, <3 => +20, <10 => +10, otherwise +0
  if [[ -z "${'$'}duplicated_lines_density" || "${'$'}duplicated_lines_density" == "null" ]]; then
    percentage=$((percentage + 20))
  else
    if awk "BEGIN { exit !(${'$'}duplicated_lines_density < 3) }"; then
      percentage=$((percentage + 20))
    elif awk "BEGIN { exit !(${'$'}duplicated_lines_density < 10) }"; then
      percentage=$((percentage + 10))
    fi
  fi

  echo "${'$'}percentage"
}

compute_security_gate() {
  local security_rating="$1"
  local security_review_rating="$2"
  local percentage=0

  # Security rating: (5 - rating) * 20 when present
  if [[ -n "${'$'}security_rating" && "${'$'}security_rating" != "null" ]]; then
    percentage=$((percentage + ((5 - ${'$'}{security_rating%%%%.*}) * 20)))
  fi

  # Security review rating: 1 => +20, 2 => +10, above 2 => +0
  if [[ -n "${'$'}security_review_rating" && "${'$'}security_review_rating" != "null" ]]; then
    case "${'$'}{security_review_rating%%%%.*}" in
      1) percentage=$((percentage + 20)) ;;
      2) percentage=$((percentage + 10)) ;;
    esac
  fi

  echo "${'$'}percentage"
}

# Main
main() {
  log "Starting OpsLevel metrics update"
  validate_required_env_vars
  validate_dependencies

  local skip_coverage=false
  if [[ "${'$'}{SKIP_CODE_COVERAGE:-}" == "true" ]]; then
    skip_coverage=true
    log "Skipping code coverage metric."
  fi

  local service_alias
  service_alias=$(read_opslevel_alias)
  log "Service alias: ${'$'}service_alias"

  # SonarQube (skipped if report-task.txt is absent)
  local coverage="" cyclomatic_complexity="" security_issues=""
  local security_rating="" reliability_rating="" maintainability_rating=""
  local duplicated_lines_density="" security_hotspots="" security_review_rating=""
  local quality_gate="" security_gate=""

  local sonar_properties
  if sonar_properties=$(read_sonar_properties); then
    local ce_task_url project_key
    IFS='|' read -r ce_task_url project_key <<< "${'$'}sonar_properties"

    poll_sonarqube_task "${'$'}ce_task_url"

    local raw_metrics
    raw_metrics=$(get_sonar_metrics "${'$'}project_key")

    coverage=$(extract_sonar_metric "${'$'}raw_metrics" "coverage")
    cyclomatic_complexity=$(extract_sonar_metric "${'$'}raw_metrics" "complexity")
    security_issues=$(extract_sonar_metric "${'$'}raw_metrics" "software_quality_security_issues")
    security_rating=$(extract_sonar_metric "${'$'}raw_metrics" "software_quality_security_rating")
    reliability_rating=$(extract_sonar_metric "${'$'}raw_metrics" "software_quality_reliability_rating")
    maintainability_rating=$(extract_sonar_metric "${'$'}raw_metrics" "software_quality_maintainability_rating")
    duplicated_lines_density=$(extract_sonar_metric "${'$'}raw_metrics" "duplicated_lines_density")
    security_hotspots=$(extract_sonar_metric "${'$'}raw_metrics" "security_hotspots")
    security_review_rating=$(extract_sonar_metric "${'$'}raw_metrics" "security_review_rating")
    quality_gate=$(compute_quality_gate \
      "${'$'}reliability_rating" "${'$'}maintainability_rating" \
      "${'$'}duplicated_lines_density")
    security_gate=$(compute_security_gate \
      "${'$'}security_rating" "${'$'}security_review_rating")

    log "Coverage:               ${'$'}{coverage:-n/a}"
    log "Cyclomatic complexity:  ${'$'}{cyclomatic_complexity:-n/a}"
    log "Security issues:        ${'$'}{security_issues:-n/a}"
    log "Security rating:        ${'$'}{security_rating:-n/a}"
    log "Reliability rating:     ${'$'}{reliability_rating:-n/a}"
    log "Maintainability rating: ${'$'}{maintainability_rating:-n/a}"
    log "Duplicated lines:       ${'$'}{duplicated_lines_density:-n/a}%%"
    log "Security hotspots:      ${'$'}{security_hotspots:-n/a}"
    log "Security review rating: ${'$'}{security_review_rating:-n/a}"
    log "Quality gate score:     ${'$'}{quality_gate}%%"
    log "Security gate score:    ${'$'}{security_gate}%%"
  fi

  # Build JSON payload
  # All numeric fields use jq --argjson to preserve number types;
  # absent values are omitted via 'if ${'$'}x != "" then ... else empty end'.
  local timestamp
  timestamp=$(date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ)

  local payload
  payload=$(jq -n \
    --arg  service              "${'$'}service_alias" \
    --arg  timestamp            "${'$'}timestamp" \
    --arg  coverage             "${'$'}{coverage:-}" \
    --arg  cyclomatic_complexity "${'$'}{cyclomatic_complexity:-}" \
    --arg  security_issues      "${'$'}{security_issues:-}" \
    --arg  quality_gate         "${'$'}{quality_gate:-}" \
    --arg  security_gate        "${'$'}{security_gate:-}" \
    --arg  security_hotspots    "${'$'}{security_hotspots:-}" \
    --argjson skip_coverage     "$( [[ "${'$'}skip_coverage" == "true" ]] && echo true || echo false )" \
    '{
      service:   ${'$'}service,
      timestamp: ${'$'}timestamp
    }
    + (if ${'$'}coverage             != "" and ${'$'}skip_coverage == false then { coverage:             (${'$'}coverage             | tonumber) } else {} end)
    + (if ${'$'}cyclomatic_complexity != "" then { cyclomatic_complexity: (${'$'}cyclomatic_complexity | tonumber) } else {} end)
    + (if ${'$'}security_issues       != "" then { security_issues:       (${'$'}security_issues       | tonumber) } else {} end)
    + (if ${'$'}quality_gate          != "" then { quality_gate:          (${'$'}quality_gate          | tonumber) } else {} end)
    + (if ${'$'}security_gate         != "" then { security_gate:         (${'$'}security_gate         | tonumber) } else {} end)
    + (if ${'$'}security_hotspots     != "" then { security_hotspots:     (${'$'}security_hotspots     | tonumber) } else {} end)
    ')

  log "Posting metrics to OpsLevel"
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
