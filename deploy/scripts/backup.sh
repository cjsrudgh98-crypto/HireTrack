#!/usr/bin/env bash
# PostgreSQL 백업: 매일 덤프 → 서버 밖 저장소로 복사 → 최근 7일치만 보관
#   cron 예: 0 4 * * * /opt/hiretrack/scripts/backup.sh >> /var/log/hiretrack-backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f ${COMPOSE_FILE:-docker-compose.prod.yml} --env-file ${ENV_FILE:-.env}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/hiretrack}"
KEEP_DAYS="${KEEP_DAYS:-7}"
DB_USERNAME="$(grep -E '^DB_USERNAME=' "${ENV_FILE:-.env}" | cut -d= -f2-)"
BACKUP_REMOTE="$(grep -E '^BACKUP_REMOTE=' "${ENV_FILE:-.env}" | cut -d= -f2- || true)"

mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/hiretrack-$(date +%Y%m%d-%H%M%S).dump"

# -Fc: 압축된 custom 포맷 (pg_restore 로 테이블 단위 복구도 가능)
$COMPOSE exec -T db pg_dump -U "$DB_USERNAME" -d hiretrack -Fc --no-owner > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"

# 덤프가 정상인지 목차를 읽어 확인 (깨진 백업을 모르고 쌓아 두지 않도록)
$COMPOSE exec -T db pg_restore --list < "$FILE" > /dev/null
echo "✔ 백업 $(du -h "$FILE" | cut -f1) $FILE"

# 서버가 통째로 날아가도 복구할 수 있게 외부 저장소로
if [[ -n "$BACKUP_REMOTE" ]]; then
  rclone copy "$FILE" "$BACKUP_REMOTE/" && echo "✔ 외부 저장소 복사: $BACKUP_REMOTE"
  rclone delete "$BACKUP_REMOTE/" --min-age "${KEEP_DAYS}d" || true
fi

find "$BACKUP_DIR" -name 'hiretrack-*.dump' -mtime +"$KEEP_DAYS" -delete
