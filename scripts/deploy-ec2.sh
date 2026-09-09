#!/usr/bin/env bash
#
# Build the BuddyDrop image, push it to Docker Hub, and deploy it to an EC2 host.
#
# Pipeline:
#   1. Build + push the image   -> reuses scripts/docker-publish.sh
#   2. Ship deploy files        -> scp compose + Caddyfile + env to $EC2_DEPLOY_DIR on the host
#   3. Pull + restart the stack -> ssh: docker compose pull && up -d
#   4. Wait for the app to report healthy
#
# Usage:
#   ./scripts/deploy-ec2.sh [tag]
#
# Config comes from the environment and from deploy/deploy.env if present
# (copy deploy/deploy.env.example). Any REQUIRED value that is missing is prompted for
# interactively; in a non-interactive shell (e.g. CI) a missing required value fails instead.
#   EC2_HOST         Public DNS/IP of the EC2 host
#   DOCKER_USERNAME  Docker Hub namespace (or set DOCKER_IMAGE)   [for build + push]
#   In the app env file: BUDDYDROP_DOMAIN, BUDDYDROP_BASE_URL, BUDDYDROP_DB_URL,
#                        BUDDYDROP_DB_PASSWORD, BUDDYDROP_S3_BUCKET
# Common optional (defaults in parentheses):
#   EC2_USER (ubuntu)  EC2_SSH_KEY (ssh-agent)  EC2_PORT (22)  EC2_DEPLOY_DIR (/opt/buddydrop)
#   APP_ENV_FILE (deploy/.env.prod)  TAG (arg -> pom version -> latest)  SKIP_PUSH (false)
#
# The host must have Docker + the compose plugin installed, and its security group must allow
# inbound 22 (SSH), 80 and 443 (Caddy/ACME). For a private Docker Hub image, run `docker login`
# on the host once beforehand — this script does not forward registry credentials to the host.

set -euo pipefail
cd "$(dirname "$0")/.."

DEPLOY_DIR_LOCAL="deploy"
COMPOSE_FILE="${DEPLOY_DIR_LOCAL}/docker-compose.prod.yml"
CADDYFILE="${DEPLOY_DIR_LOCAL}/Caddyfile"
COMPOSE_BASENAME="docker-compose.prod.yml"

# Prompt for a required value when it is missing. An interactive terminal gets a prompt (looped
# until non-empty); without a terminal (e.g. CI) the script fails with guidance instead.
#   prompt_var VARNAME "description" [secret]
prompt_var() {
  local name="$1" desc="$2" secret="${3:-}" val="${!1:-}"
  while [[ -z "${val}" ]]; do
    if [[ ! -t 0 ]]; then
      echo "ERROR: required ${name} (${desc}) is not set, and there is no terminal to prompt on." >&2
      echo "       Set it in the environment, ${DEPLOY_DIR_LOCAL}/deploy.env, or the app env file, then re-run." >&2
      exit 1
    fi
    if [[ -n "${secret}" ]]; then
      read -r -s -p "  ${desc} [${name}]: " val; echo
    else
      read -r -p "  ${desc} [${name}]: " val
    fi
  done
  printf -v "${name}" '%s' "${val}"
  export "${name}"
}

# --- Load deploy config, without clobbering already-exported vars ---
if [[ -f "${DEPLOY_DIR_LOCAL}/deploy.env" ]]; then
  set -a; # shellcheck disable=SC1091
  . "./${DEPLOY_DIR_LOCAL}/deploy.env"; set +a
fi

# --- Resolve config with defaults (prompting for missing required values) ---
[[ -n "${EC2_HOST:-}" ]] || prompt_var EC2_HOST "EC2 host — public DNS or IP"
EC2_USER="${EC2_USER:-ubuntu}"
EC2_PORT="${EC2_PORT:-22}"
EC2_DEPLOY_DIR="${EC2_DEPLOY_DIR:-/opt/buddydrop}"
APP_ENV_FILE="${APP_ENV_FILE:-${DEPLOY_DIR_LOCAL}/.env.prod}"
SKIP_PUSH="${SKIP_PUSH:-false}"

# Resolve image + tag with the same precedence as docker-publish.sh so they stay in sync:
# explicit arg > TAG env > pom version > "latest".
pom_version="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null || true)"
TAG="${1:-${TAG:-${pom_version:-latest}}}"
if [[ -z "${DOCKER_IMAGE:-}" ]]; then
  [[ -n "${DOCKER_USERNAME:-}" ]] || prompt_var DOCKER_USERNAME "Docker Hub namespace (user or org)"
  DOCKER_IMAGE="${DOCKER_USERNAME}/buddydrop"
fi

# --- Preconditions ---
for f in "${COMPOSE_FILE}" "${CADDYFILE}"; do
  [[ -f "$f" ]] || { echo "ERROR: missing ${f}" >&2; exit 1; }
done
if [[ ! -f "${APP_ENV_FILE}" ]]; then
  echo "ERROR: app env file '${APP_ENV_FILE}' not found." >&2
  echo "       Copy ${DEPLOY_DIR_LOCAL}/.env.prod.example to it and fill it in." >&2
  exit 1
fi

# --- Validate the app env; prompt for any missing REQUIRED values ---
# These mirror the ${VAR:?...} guards in deploy/docker-compose.prod.yml. Reading happens in a
# subshell so sourcing the env file can't clobber the script's own resolved config.
app_env_value() { ( set -a; # shellcheck disable=SC1090
  . "${APP_ENV_FILE}" >/dev/null 2>&1 || true; printf '%s' "${!1:-}" ); }

declare -a prompted_env=()
ensure_app_var() {   # ensure_app_var VARNAME "description" [secret]
  local name="$1"
  if [[ -z "$(app_env_value "${name}")" ]]; then
    prompt_var "$@"
    prompted_env+=("${name}=${!name}")
  fi
}

ensure_app_var BUDDYDROP_DOMAIN      "public domain, e.g. drop.example.com"
ensure_app_var BUDDYDROP_BASE_URL    "public base URL, e.g. https://drop.example.com"
ensure_app_var BUDDYDROP_DB_URL      "JDBC database URL, e.g. jdbc:postgresql://host:5432/buddydrop"
ensure_app_var BUDDYDROP_DB_PASSWORD "database password" secret
ensure_app_var BUDDYDROP_S3_BUCKET   "S3 bucket name"

# Build the env file to ship: the operator's file plus any values entered at the prompt. In a
# compose env file later lines win, so appended answers override blanks earlier in the file.
SHIP_ENV="$(mktemp)"; chmod 600 "${SHIP_ENV}"
trap 'rm -f "${SHIP_ENV}"' EXIT
cp "${APP_ENV_FILE}" "${SHIP_ENV}"
if ((${#prompted_env[@]})); then
  printf '\n# --- added interactively by deploy-ec2.sh ---\n' >> "${SHIP_ENV}"
  printf '%s\n' "${prompted_env[@]}" >> "${SHIP_ENV}"
fi

# --- SSH/scp option arrays (quote-safe) ---
ssh_opts=(-p "${EC2_PORT}" -o StrictHostKeyChecking=accept-new)
scp_opts=(-P "${EC2_PORT}" -o StrictHostKeyChecking=accept-new)
if [[ -n "${EC2_SSH_KEY:-}" ]]; then
  key="${EC2_SSH_KEY/#\~/$HOME}"    # expand a leading ~ (arrays don't get tilde expansion)
  ssh_opts+=(-i "${key}"); scp_opts+=(-i "${key}")
fi
remote="${EC2_USER}@${EC2_HOST}"

echo "==> Target : ${remote}:${EC2_DEPLOY_DIR}"
echo "==> Image  : ${DOCKER_IMAGE}:${TAG}"

# --- 1. Build + push (reuse docker-publish.sh) ---
if [[ "${SKIP_PUSH}" == "true" ]]; then
  echo "==> SKIP_PUSH=true — not rebuilding; deploying existing ${DOCKER_IMAGE}:${TAG}"
else
  echo "==> Building and pushing image via scripts/docker-publish.sh"
  DOCKER_IMAGE="${DOCKER_IMAGE}" TAG="${TAG}" ./scripts/docker-publish.sh "${TAG}"
fi

# --- 2. Ship deploy files ---
echo "==> Copying deploy files to the host"
ssh "${ssh_opts[@]}" "${remote}" "mkdir -p '${EC2_DEPLOY_DIR}'"
scp "${scp_opts[@]}" "${COMPOSE_FILE}" "${CADDYFILE}" "${remote}:${EC2_DEPLOY_DIR}/"
# Ship the (merged) app env as `.env` so `docker compose` picks it up automatically.
scp "${scp_opts[@]}" "${SHIP_ENV}" "${remote}:${EC2_DEPLOY_DIR}/.env"

# --- 3. Pull + restart on the host, then health-check ---
# DOCKER_IMAGE/TAG are exported into the remote shell so the tag we just pushed always wins over
# whatever the shipped .env carries.
echo "==> Deploying on the host"
ssh "${ssh_opts[@]}" "${remote}" \
  DOCKER_IMAGE="${DOCKER_IMAGE}" TAG="${TAG}" COMPOSE_BASENAME="${COMPOSE_BASENAME}" \
  DEPLOY_DIR="${EC2_DEPLOY_DIR}" "bash -s" <<'REMOTE'
set -euo pipefail
cd "${DEPLOY_DIR}"

# Prefer docker compose v2 (plugin); fall back to legacy docker-compose v1.
if docker compose version >/dev/null 2>&1; then
  dc() { docker compose "$@"; }
elif command -v docker-compose >/dev/null 2>&1; then
  dc() { docker-compose "$@"; }
else
  echo "ERROR: Docker Compose is not installed on the host." >&2
  exit 1
fi

export DOCKER_IMAGE TAG
dc -f "${COMPOSE_BASENAME}" pull
dc -f "${COMPOSE_BASENAME}" up -d --remove-orphans
docker image prune -f >/dev/null 2>&1 || true
dc -f "${COMPOSE_BASENAME}" ps

echo "==> Waiting for the app to become healthy"
ok=false
for _ in $(seq 1 30); do
  if dc -f "${COMPOSE_BASENAME}" exec -T app \
        wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    ok=true; break
  fi
  sleep 5
done

if [[ "${ok}" == "true" ]]; then
  echo "==> App is healthy."
else
  echo "ERROR: app did not report healthy within ~150s. Recent logs:" >&2
  dc -f "${COMPOSE_BASENAME}" logs --tail=100 app >&2 || true
  exit 1
fi
REMOTE

echo "==> Deploy complete: ${DOCKER_IMAGE}:${TAG} is live at ${BUDDYDROP_BASE_URL:-your domain}"
