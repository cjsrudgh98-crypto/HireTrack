#!/usr/bin/env bash
# 새 이미지로 배포하고, 헬스체크에 실패하면 직전 이미지로 롤백한다.
#   사용: scripts/deploy.sh <이미지 태그(커밋 SHA)>
# GitHub Actions(deploy.yml)가 SSH 로 이 스크립트를 실행한다.
#   SKIP_PULL=1  : 레지스트리에서 받지 않고 로컬 이미지로 (리허설용)
#   HEALTH_URL   : Nginx 를 거친 외부 헬스체크 주소 (기본 https://localhost/health)
set -euo pipefail

cd "$(dirname "$0")/.."
NEW_TAG="${1:?이미지 태그를 넘겨 주세요}"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"
HEALTH_URL="${HEALTH_URL:-https://localhost/health}"

current_tag() { grep -E '^TAG=' .env | cut -d= -f2-; }
set_tag() { sed -i "s/^TAG=.*/TAG=$1/" .env; }

healthy() {
  # app 컨테이너 안에서 Actuator(9090) 확인 + Nginx 를 거친 외부 경로까지 확인
  $COMPOSE exec -T app wget -qO- http://localhost:9090/actuator/health 2>/dev/null | grep -q '"UP"' &&
    curl -fsS --max-time 5 -k "$HEALTH_URL" -H "Host: $(grep -E '^DOMAIN=' .env | cut -d= -f2-)" >/dev/null
}

wait_healthy() {
  local waited=0
  until healthy; do
    if (( waited >= HEALTH_TIMEOUT )); then return 1; fi
    sleep 5; waited=$((waited + 5))
  done
}

PREV_TAG="$(current_tag)"
echo "▶ 배포: ${PREV_TAG} → ${NEW_TAG}"

set_tag "$NEW_TAG"
[[ "${SKIP_PULL:-}" == 1 ]] || $COMPOSE pull app web
$COMPOSE up -d --remove-orphans

if wait_healthy; then
  echo "✔ 배포 성공 (${NEW_TAG})"
  echo "$PREV_TAG" > .previous_tag
  docker image prune -f >/dev/null
  exit 0
fi

echo "✘ 헬스체크 실패 — ${PREV_TAG} 로 롤백합니다" >&2
$COMPOSE logs --tail=100 app >&2 || true
set_tag "$PREV_TAG"
$COMPOSE up -d --remove-orphans
if wait_healthy; then
  echo "↩ 롤백 완료 (${PREV_TAG})" >&2
else
  echo "‼ 롤백 후에도 비정상 — 수동 확인 필요" >&2
fi
exit 1
