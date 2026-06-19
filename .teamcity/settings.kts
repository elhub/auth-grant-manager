import jetbrains.buildServer.configs.kotlin.ArtifactRule
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.ReuseBuilds
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.toId
import no.elhub.devxp.build.configuration.pipeline.constants.AgentScope
import no.elhub.devxp.build.configuration.pipeline.constants.Group
import no.elhub.devxp.build.configuration.pipeline.dsl.elhubProject
import no.elhub.devxp.build.configuration.pipeline.extensions.addPrFeature
import no.elhub.devxp.build.configuration.pipeline.jobs.customJob
import no.elhub.devxp.build.configuration.pipeline.jobs.gradleVerify

val imageRepo = "auth/auth-grant-manager"
val dbDirectory = "./db"
val liquiEntryPoint = "db-changelog.yaml"
val gitOpsRepo = "https://github.com/elhub/auth"

elhubProject(group = Group.AUTH, name = "auth-grant-manager") {
    pipeline {
        sequential {
            val verify = gradleVerify {
                lintImage = "docker.jfrog.elhub.cloud/oxsecurity/megalinter:v8"
                enablePublishMetrics = false
            }
            customJob(AgentScope.LinuxAgentContext) {
                dependencies {
                    artifacts(buildTypeId = verify.sonarScanBuildType!!.id!!) {
                        buildRule = lastFinished()
                        rules = listOf(ArtifactRule.include("report-task.zip!**", ".scannerwork"))
                        cleanDestination = true
                    }
                    snapshot(verify.sonarScanBuildType!!) {
                        onDependencyFailure = FailureAction.FAIL_TO_START
                        reuseBuilds = ReuseBuilds.NO
                    }
                }

                addPrFeature()
                name = "Publish metrics to Opslevel"
                id(name.toId())
                steps {
                    script {
                        name = "OpsLevel Push"
                        scriptContent = """
#!/bin/bash
set -euo pipefail

echo "Collecting SonarQube metrics and posting to OpsLevel"

# Required environment variables:
#   OPSLEVEL_TOKEN
#   SONAR_TOKEN
# Optional environment variables:
#   SONAR_HOST_URL         (default: https://sonar.elhub.cloud)
#   SONAR_PROJECT_KEY      (optional override)
#   SONAR_CE_TASK_URL      (optional override)
#   SKIP_CODE_COVERAGE=true

readonly OPSLEVEL_FILE="opslevel.yml"
readonly CURL_RETRY_ARGS=(--retry 3 --retry-delay 1 --connect-timeout 10 --max-time 30)
readonly OPSLEVEL_INTEGRATION_URL="https://upload.opslevel.com/integrations/custom_event/0b4b4bdf-21fb-473d-9a35-1fcdae67ef39"
readonly OPSLEVEL_TOKEN="${'$'}{OPSLEVEL_TOKEN}"
readonly SONAR_HOST_URL="${'$'}{SONAR_HOST_URL:-https://sonar.elhub.cloud}"

log() {
  echo "$(date -u -Iseconds) - ${'$'}*" >&2
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

  for cmd in curl jq awk grep cut; do
    command -v "${'$'}cmd" >/dev/null 2>&1 || missing+=("${'$'}cmd")
  done

  if (( ${'$'}{#missing[@]} > 0 )); then
    log "Missing required commands: ${'$'}{missing[*]}"
    exit 1
  fi
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

read_opslevel_alias() {
  if [[ ! -f "${'$'}OPSLEVEL_FILE" ]]; then
    log "${'$'}OPSLEVEL_FILE not found."
    exit 1
  fi

  local alias
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

  printf '%s|%s' "${'$'}ce_task_url" "${'$'}project_key"
}

poll_sonarqube_task() {
  local ce_task_url="$1"
  local max_wait=30
  local start_time status elapsed response

  start_time=$(date +%%s)
  log "Polling SonarQube task: ${'$'}ce_task_url"

  while true; do
    response=$(curl -s "${'$'}ce_task_url" --header "Authorization: Bearer ${'$'}SONAR_TOKEN") \
      || { log "Request failed: ${'$'}ce_task_url"; exit 1; }

    status=$(echo "${'$'}response" | jq -r '.task.status')
    log "Task status: ${'$'}status"

    [[ "${'$'}status" == "SUCCESS" ]] && break

    elapsed=$(( $(date +%%s) - start_time ))
    if (( elapsed >= max_wait )); then
      log "Timeout waiting for SonarQube task."
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

to_int_or_empty() {
  local value="$1"
  if [[ -z "${'$'}value" || "${'$'}value" == "null" ]]; then
    echo ""
  else
    awk -v v="${'$'}value" 'BEGIN { print int(v) }'
  fi
}

compute_quality_gate() {
  local reliability_rating="$1"
  local maintainability_rating="$2"
  local duplicated_lines_density="$3"
  local percentage=0
  local r m

  r=$(to_int_or_empty "${'$'}reliability_rating")
  [[ -n "${'$'}r" ]] && percentage=$((percentage + ((5 - r) * 10)))

  m=$(to_int_or_empty "${'$'}maintainability_rating")
  [[ -n "${'$'}m" ]] && percentage=$((percentage + ((5 - m) * 10)))

  if [[ -z "${'$'}duplicated_lines_density" || "${'$'}duplicated_lines_density" == "null" ]]; then
    percentage=$((percentage + 20))
  else
    if awk -v d="${'$'}duplicated_lines_density" 'BEGIN { exit !(d < 3) }'; then
      percentage=$((percentage + 20))
    elif awk -v d="${'$'}duplicated_lines_density" 'BEGIN { exit !(d < 10) }'; then
      percentage=$((percentage + 10))
    fi
  fi

  echo "${'$'}percentage"
}

compute_security_gate() {
  local security_rating="$1"
  local security_review_rating="$2"
  local percentage=0
  local s sr

  s=$(to_int_or_empty "${'$'}security_rating")
  [[ -n "${'$'}s" ]] && percentage=$((percentage + ((5 - s) * 20)))

  sr=$(to_int_or_empty "${'$'}security_review_rating")
  case "${'$'}sr" in
    1) percentage=$((percentage + 20)) ;;
    2) percentage=$((percentage + 10)) ;;
  esac

  echo "${'$'}percentage"
}

main() {
  log "Starting SonarQube metrics update"
  validate_required_env_vars
  validate_dependencies

  local skip_coverage=false
  [[ "${'$'}{SKIP_CODE_COVERAGE:-}" == "true" ]] && skip_coverage=true

  local service_alias
  service_alias=$(read_opslevel_alias)
  log "Service alias: ${'$'}service_alias"

  local sonar_properties ce_task_url project_key
  sonar_properties=$(read_sonar_properties) || {
    log "Sonar properties unavailable, skipping."
    exit 0
  }

  IFS='|' read -r ce_task_url project_key <<< "${'$'}sonar_properties"

  poll_sonarqube_task "${'$'}ce_task_url"

  local raw_metrics
  raw_metrics=$(get_sonar_metrics "${'$'}project_key")

  local coverage cyclomatic_complexity security_issues
  local security_rating reliability_rating maintainability_rating
  local duplicated_lines_density security_hotspots security_review_rating
  local quality_gate security_gate

  coverage=$(extract_sonar_metric "${'$'}raw_metrics" "coverage")
  cyclomatic_complexity=$(extract_sonar_metric "${'$'}raw_metrics" "complexity")
  security_issues=$(extract_sonar_metric "${'$'}raw_metrics" "software_quality_security_issues")
  security_rating=$(extract_sonar_metric "${'$'}raw_metrics" "software_quality_security_rating")
  reliability_rating=$(extract_sonar_metric "${'$'}raw_metrics" "software_quality_reliability_rating")
  maintainability_rating=$(extract_sonar_metric "${'$'}raw_metrics" "software_quality_maintainability_rating")
  duplicated_lines_density=$(extract_sonar_metric "${'$'}raw_metrics" "duplicated_lines_density")
  security_hotspots=$(extract_sonar_metric "${'$'}raw_metrics" "security_hotspots")
  security_review_rating=$(extract_sonar_metric "${'$'}raw_metrics" "security_review_rating")
  quality_gate=$(compute_quality_gate "${'$'}reliability_rating" "${'$'}maintainability_rating" "${'$'}duplicated_lines_density")
  security_gate=$(compute_security_gate "${'$'}security_rating" "${'$'}security_review_rating")

  local timestamp
  timestamp=$(date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ)

  local payload
  payload=$(jq -n \
    --arg service "${'$'}service_alias" \
    --arg timestamp "${'$'}timestamp" \
    --arg coverage "${'$'}{coverage:-}" \
    --arg cyclomatic_complexity "${'$'}{cyclomatic_complexity:-}" \
    --arg security_issues "${'$'}{security_issues:-}" \
    --arg quality_gate "${'$'}{quality_gate:-}" \
    --arg security_gate "${'$'}{security_gate:-}" \
    --arg security_hotspots "${'$'}{security_hotspots:-}" \
    --argjson skip_coverage "$( [[ "${'$'}skip_coverage" == "true" ]] && echo true || echo false )" \
    '{
      service: ${'$'}service,
      timestamp: ${'$'}timestamp
    }
    + (if ${'$'}coverage != "" and ${'$'}skip_coverage == false then { coverage: (${'$'}coverage | tonumber) } else {} end)
    + (if ${'$'}cyclomatic_complexity != "" then { cyclomatic_complexity: (${'$'}cyclomatic_complexity | tonumber) } else {} end)
    + (if ${'$'}security_issues != "" then { security_issues: (${'$'}security_issues | tonumber) } else {} end)
    + (if ${'$'}quality_gate != "" then { quality_gate: (${'$'}quality_gate | tonumber) } else {} end)
    + (if ${'$'}security_gate != "" then { security_gate: (${'$'}security_gate | tonumber) } else {} end)
    + (if ${'$'}security_hotspots != "" then { security_hotspots: (${'$'}security_hotspots | tonumber) } else {} end)
    ')


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
