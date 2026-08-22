#!/usr/bin/env bash
#
# Build the BuddyDrop Docker image and push it to Docker Hub.
#
# Usage:
#   DOCKER_USERNAME=you ./scripts/docker-publish.sh [tag]
#
# Environment:
#   DOCKER_USERNAME  Docker Hub user/namespace (required unless DOCKER_IMAGE is set)
#   DOCKER_IMAGE     Full image name (default: $DOCKER_USERNAME/buddydrop)
#   DOCKER_PASSWORD  Docker Hub access token; if set, the script runs `docker login`
#   TAG              Image tag (default: 1st arg, else project version from pom, else "latest")
#   PUSH_LATEST      Also push :latest (default: true)
#   PLATFORMS        e.g. "linux/amd64,linux/arm64" -> multi-arch build+push via buildx
#
# Reads .env if present so it can share config with docker-compose.

set -euo pipefail
cd "$(dirname "$0")/.."

# Load .env if present (without clobbering already-exported vars).
if [[ -f .env ]]; then
  set -a; # shellcheck disable=SC1091
  . ./.env; set +a
fi

# Resolve the image tag: explicit arg > TAG env > pom version > "latest".
pom_version="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null || true)"
TAG="${1:-${TAG:-${pom_version:-latest}}}"

if [[ -z "${DOCKER_IMAGE:-}" ]]; then
  : "${DOCKER_USERNAME:?Set DOCKER_USERNAME (or DOCKER_IMAGE) to your Docker Hub namespace}"
  DOCKER_IMAGE="${DOCKER_USERNAME}/buddydrop"
fi

PUSH_LATEST="${PUSH_LATEST:-true}"
echo "==> Publishing ${DOCKER_IMAGE}:${TAG} (push :latest = ${PUSH_LATEST})"

# Optional non-interactive login with a Docker Hub access token.
if [[ -n "${DOCKER_PASSWORD:-}" && -n "${DOCKER_USERNAME:-}" ]]; then
  echo "==> docker login as ${DOCKER_USERNAME}"
  echo "${DOCKER_PASSWORD}" | docker login -u "${DOCKER_USERNAME}" --password-stdin
fi

latest_tag_args=()
if [[ "${PUSH_LATEST}" == "true" ]]; then
  latest_tag_args=(-t "${DOCKER_IMAGE}:latest")
fi

if [[ -n "${PLATFORMS:-}" ]]; then
  echo "==> Multi-arch build (${PLATFORMS}) via buildx, pushing directly"
  docker buildx build --platform "${PLATFORMS}" \
    -t "${DOCKER_IMAGE}:${TAG}" "${latest_tag_args[@]}" --push .
else
  docker build -t "${DOCKER_IMAGE}:${TAG}" "${latest_tag_args[@]}" .
  docker push "${DOCKER_IMAGE}:${TAG}"
  if [[ "${PUSH_LATEST}" == "true" ]]; then
    docker push "${DOCKER_IMAGE}:latest"
  fi
fi

echo "==> Done: ${DOCKER_IMAGE}:${TAG}"
