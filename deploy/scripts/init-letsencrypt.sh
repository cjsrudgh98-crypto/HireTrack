#!/usr/bin/env bash
# 최초 1회: Let's Encrypt 인증서 발급.
# Nginx 는 인증서 파일이 없으면 뜨지 않으므로 ① 임시(자체 서명) 인증서로 Nginx 를 띄우고
# ② webroot 방식으로 진짜 인증서를 받은 뒤 ③ Nginx 를 reload 한다. 이후 갱신은 certbot 컨테이너가 자동으로.
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env"
DOMAIN="$(grep -E '^DOMAIN=' .env | cut -d= -f2-)"
EMAIL="$(grep -E '^LETSENCRYPT_EMAIL=' .env | cut -d= -f2-)"
LIVE="certbot/conf/live/$DOMAIN"

mkdir -p "$LIVE" certbot/www
if [[ ! -f "$LIVE/fullchain.pem" ]]; then
  echo "▶ 임시 인증서 생성"
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 -subj "/CN=$DOMAIN" \
    -keyout "$LIVE/privkey.pem" -out "$LIVE/fullchain.pem"
fi

$COMPOSE up -d web

echo "▶ 진짜 인증서 요청: $DOMAIN"
rm -rf "$LIVE" "certbot/conf/archive/$DOMAIN" "certbot/conf/renewal/$DOMAIN.conf"
$COMPOSE run --rm --entrypoint "certbot certonly --webroot -w /var/www/certbot -d $DOMAIN \
  --email $EMAIL --agree-tos --no-eff-email --rsa-key-size 4096" certbot

$COMPOSE exec web nginx -s reload
echo "✔ HTTPS 준비 완료: https://$DOMAIN"
echo "  인증서 갱신 후 Nginx reload 를 위해 crontab 에 추가:"
echo "  0 5 * * * cd $(pwd) && $COMPOSE exec web nginx -s reload"
