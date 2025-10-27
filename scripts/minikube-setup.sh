#!/usr/bin/env bash
set -euo pipefail

# Start minikube with recommended CPU/memory, enable ingress, and optionally switch Docker env
# Usage: ./scripts/minikube-setup.sh [--cpus 4] [--memory 8192] [--driver docker] [--use-docker-env]

CPUS=4
MEMORY=8192
DRIVER="docker"
USE_DOCKER_ENV=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --cpus)
      CPUS="$2"; shift 2;;
    --memory)
      MEMORY="$2"; shift 2;;
    --driver)
      DRIVER="$2"; shift 2;;
    --use-docker-env)
      USE_DOCKER_ENV=true; shift;;
    *)
      echo "Unknown arg: $1"; exit 1;;
  esac
done

if ! command -v minikube >/dev/null 2>&1; then
  echo "[ERROR] minikube not found in PATH" >&2
  exit 1
fi

PROFILE="minikube"

if ! minikube -p "$PROFILE" status >/dev/null 2>&1; then
  echo "[INFO] Starting minikube (cpus=${CPUS}, memory=${MEMORY}Mi, driver=${DRIVER})"
  minikube start -p "$PROFILE" --cpus="$CPUS" --memory="$MEMORY" --driver="$DRIVER"
else
  echo "[INFO] Minikube already running"
fi

echo "[INFO] Enabling ingress addon"
minikube -p "$PROFILE" addons enable ingress

if $USE_DOCKER_ENV; then
  echo "[INFO] Switching to minikube's Docker daemon for image builds"
  # shellcheck disable=SC2046
  eval $(minikube -p "$PROFILE" docker-env)
  echo "[INFO] Docker env set for minikube. You can now run ./scripts/build-images.sh --minikube"
fi

# Show cluster info
kubectl cluster-info
minikube -p "$PROFILE" status
