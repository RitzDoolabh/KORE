#!/usr/bin/env bash
set -euo pipefail

# Build Docker images for runnable modules. If --minikube is passed, images are built into Minikube's Docker daemon.
# Usage: ./scripts/build-images.sh [--minikube] [--tag vX.Y.Z]

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
TAG="latest"
USE_MINIKUBE=false

for arg in "$@"; do
  case "$arg" in
    --minikube)
      USE_MINIKUBE=true
      shift
      ;;
    --tag)
      TAG="$2"
      shift 2
      ;;
    *)
      ;;
  esac
done

if $USE_MINIKUBE; then
  echo "[INFO] Using Minikube Docker daemon"
  # shellcheck disable=SC2046
  eval $(minikube -p minikube docker-env)
fi

cd "$ROOT_DIR"

# Build all jars first
./gradlew clean :modules:spm:bootJar :modules:irp:bootJar :modules:qpm:bootJar :mgm:bootJar :gateway:bootJar

# Build function: context path and image name
build_image() {
  local module_path=$1
  local image_name=$2
  echo "[INFO] Building image ${image_name}:${TAG} from ${module_path}"
  docker build -t "${image_name}:${TAG}" "$module_path"
}

# You may change repositories as needed
build_image modules/spm    knightmesh/spm
build_image modules/irp    knightmesh/irp
build_image modules/qpm    knightmesh/qpm
build_image mgm            knightmesh/mgm
build_image gateway        knightmesh/gateway

if $USE_MINIKUBE; then
  echo "[INFO] Built images in Minikube daemon. Listing images:"
  # 'minikube image list' requires docker-env sometimes; we already eval'ed above.
  if command -v minikube >/dev/null 2>&1; then
    # Try both newer and older subcommands
    (minikube image list || minikube image ls) || true
  fi
fi

echo "[INFO] Done. Built images with tag: ${TAG}"