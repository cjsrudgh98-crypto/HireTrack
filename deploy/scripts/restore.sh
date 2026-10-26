#!/usr/bin/env bash
# 백업 덤프로 DB 를 되돌린다. 현재 데이터는 모두 덮어쓰므로 --yes 를 붙여야 실행된다.
#   사용: scripts/restore.sh /var/backups/hiretrack/hiretrack-20261026-040000.dump --yes
set -euo pipefail

cd "$(dirname "$0")/.."
DUMP="${1:?덤프 파일 경로를 넘겨 주세요}"
[[ "${2:-}" == "--yes" ]] || { echo "현재 DB 를 덮어씁니다. 확인했다면 --yes 를 붙여 다시 실행하세요." >&2; exit 2; }
[[ -f "$DUMP" ]] || { echo "파일 없음: $DUMP" >&2; exit 1; }

COMPOSE="docker compose -f ${COMPOSE_FILE:-docker-compose.prod.yml} --env-file ${ENV_FILE:-.env}"
DB_USERNAME="$(grep -E '^DB_USERNAME=' "${ENV_FILE:-.env}" | cut -d= -f2-)"

echo "▶ 앱 중지 (복구 중 쓰기 방지)"
$COMPOSE stop app

echo "▶ DB 초기화 후 복구: $DUMP"
$COMPOSE exec -T db psql -U "$DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS hiretrack WITH (FORCE);" -c "CREATE DATABASE hiretrack OWNER \"$DB_USERNAME\";"
$COMPOSE exec -T db pg_restore -U "$DB_USERNAME" -d hiretrack --no-owner --exit-on-error < "$DUMP"

echo "▶ 앱 시작 (Flyway 가 덤프 이후 추가된 마이그레이션이 있으면 적용)"
$COMPOSE start app
echo "✔ 복구 완료"
