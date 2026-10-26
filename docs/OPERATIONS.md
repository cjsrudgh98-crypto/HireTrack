# 운영 가이드 — 배포 · 모니터링 · 백업/복구

## 구성

서버 1대에 Docker Compose 로 4개 컨테이너 (`deploy/docker-compose.prod.yml`).

| 서비스 | 이미지 | 역할 | 외부 노출 |
|---|---|---|---|
| `web` | `ghcr.io/<owner>/hiretrack-web` | Nginx: HTTPS 종료, React 정적 파일, `/api` → app, `/health` → Actuator | 80, 443 |
| `app` | `ghcr.io/<owner>/hiretrack-app` | Spring Boot (`prod` 프로필) — API 8080, Actuator 9090 | 없음 |
| `db` | `postgres:17-alpine` | 데이터 (볼륨 `db-data`) | 없음 |
| `certbot` | `certbot/certbot` | Let's Encrypt 인증서 12시간마다 갱신 시도 | 없음 |

설정은 Spring 프로필로 나눕니다: `local`(기본, H2 파일 DB) / `prod`(PostgreSQL, 비밀값은 모두 환경변수, 기본값 없음 → 빠지면 기동 실패).

## 최초 서버 준비 (1회)

1. 소형 클라우드 인스턴스(2 vCPU / 2~4GB RAM 정도면 충분), Docker 설치, 도메인 A 레코드 연결, 방화벽 22·80·443 만 허용.
   무료 크레딧·프리티어 종료 후 월 비용을 미리 확인해 두세요.
2. `/opt/hiretrack/` 에 `deploy/docker-compose.prod.yml`, `deploy/scripts/` 복사, `deploy/.env.example` → `.env` 로 채우기.
   `.env` 는 저장소에 절대 커밋하지 않습니다(`chmod 600 .env`).
3. GHCR 이미지를 받기 위해 `docker login ghcr.io` (read:packages 권한 토큰) — 또는 패키지를 public 으로.
4. HTTPS: `./scripts/init-letsencrypt.sh` — 임시 인증서로 Nginx 를 띄우고 진짜 인증서를 받은 뒤 reload. 안내되는 nginx reload cron 등록.
5. 배포용 SSH 키: 서버에 배포 전용 사용자 + 공개키, GitHub Secrets 에 개인키.

## CI/CD (GitHub Actions)

```
PR ─▶ ci.yml ─┬─ Backend: ./mvnw verify (단위·통합·Testcontainers PostgreSQL, JaCoCo 요약)
              ├─ Frontend: lint → test(Vitest) → build
              ├─ E2E: jar + vite preview 띄우고 Playwright
              └─ Docker: 두 이미지 빌드 확인

main ─▶ deploy.yml ─▶ ci.yml 재사용 ─▶ 이미지 빌드·GHCR 푸시(태그 = 커밋 SHA) ─▶ SSH: scripts/deploy.sh <SHA>
                                                                                   └─ 헬스체크 실패 → 직전 태그로 롤백 + job 실패
```

- **브랜치 보호** (Settings → Branches → `main`): "Require status checks" 에 `Backend …`, `Frontend …`, `E2E …`, `Docker images build` 지정, 직접 push 금지.
- **Secrets**: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`(`ssh-keyscan <host>` 결과). 앱 비밀값(DB·JWT·메일)은 GitHub 이 아니라 **서버 `.env` 에만** 둡니다.
- **Variables**: 서버 준비가 끝나면 `DEPLOY_ENABLED=true` 를 추가합니다. 그 전에는 main push 로 배포가 돌지 않고, Actions 탭에서 수동 실행만 됩니다.
- **Environment** `production` 을 만들고 필요하면 승인자를 지정하면 배포 전 수동 승인이 걸립니다.
- **DB 마이그레이션**: 앱 기동 시 Flyway 가 `db/migration` 의 새 버전을 자동 적용합니다. 한 번 배포된 마이그레이션 파일은 수정하지 않고 새 버전을 추가합니다.

### 배포 스크립트 동작 (`deploy/scripts/deploy.sh`)

1. `.env` 의 `TAG` 를 새 SHA 로 바꾸고 `pull` → `up -d`
2. 최대 120초 동안 ① app 컨테이너 안 `localhost:9090/actuator/health` 가 UP ② Nginx 를 거친 `https://<도메인>/health` 가 200
3. 실패하면 이전 `TAG` 로 되돌려 `up -d`, 로그 100줄 출력, exit 1 (→ GitHub Actions job 실패로 알림)

## 모니터링

| 무엇을 | 어떻게 |
|---|---|
| 서버 생존 | 외부 업타임 체크(UptimeRobot·Better Stack 등 무료 플랜)에 `https://<도메인>/health` 등록 → 다운 시 메일/앱 알림 |
| 예외 | `SENTRY_DSN` 설정 시 Sentry 로 전송 (비우면 비활성). 개인정보 미전송(`send-default-pii: false`) |
| 파서 깨짐 | 지표 `hiretrack.parser.results{source,result}` / 로그 `parser-failure source=SARAMIN failureRate=…` |
| 알림 발송 | 지표 `hiretrack.reminders{result=sent\|failed\|duplicate}` / 로그 `reminder-sent`, `reminder-failed` |
| 비밀번호 대입 시도 | 로그 `login-locked`(이메일 해시), `auth-rate-limited ip=…` — 같은 IP 가 계속 보이면 방화벽에서 차단 |
| 지표 조회 | Actuator 는 외부 비공개(9090) → `docker compose exec app wget -qO- localhost:9090/actuator/metrics/hiretrack.reminders` |

> 메일 서버 장애는 health 에 넣지 않았습니다. 넣으면 SMTP 문제로 앱 전체가 DOWN 이 되어 배포가 롤백됩니다(리허설에서 실제로 발생).

## 백업

`deploy/scripts/backup.sh` — `pg_dump -Fc` → 덤프 목차 읽기로 무결성 확인 → (선택) `rclone` 으로 서버 밖 저장소 복사 → 7일 지난 것 삭제.

```cron
0 4 * * * /opt/hiretrack/scripts/backup.sh >> /var/log/hiretrack-backup.log 2>&1
```

서버 밖 저장소: `.env` 의 `BACKUP_REMOTE` 에 rclone 원격(예: Cloudflare R2·S3 버킷)을 지정. 서버 디스크에만 두면 서버를 잃을 때 백업도 함께 잃습니다.

## 복구

```bash
cd /opt/hiretrack
./scripts/restore.sh /var/backups/hiretrack/hiretrack-YYYYMMDD-HHMMSS.dump --yes
```

앱 중지 → DB 재생성 → `pg_restore` → 앱 시작(덤프 이후 추가된 마이그레이션이 있으면 Flyway 가 적용).

### 복구 리허설 기록 — 2026-10-26

운영과 같은 이미지·compose·스크립트를 로컬 Docker 에서 그대로 실행했습니다(호스트 포트만 8081/8443, 자체 서명 인증서).

| 단계 | 결과 |
|---|---|
| `deploy.sh` 로 배포 → Nginx(HTTPS) 경유 `/health` UP, SPA 라우트 200, HTTP→HTTPS 301, 보안 헤더 | ✔ |
| 깨진 이미지(기동 즉시 종료) 배포 → 헬스체크 실패 → 직전 태그로 **자동 롤백**, exit 1 | ✔ |
| 데이터 준비(공고 2·상태 이력·자소서 제출본·면접) → `backup.sh` (32KB 덤프, 무결성 확인) | ✔ |
| 장애 재현: `docker compose down -v` 로 **DB 볼륨 삭제** → 새 DB 에서 로그인 401 | ✔ |
| `restore.sh` → 공고·상태·제출본 고정·면접·이력·통계가 백업 전 스냅샷과 **완전히 동일** | ✔ |

리허설 중 발견해 고친 문제: PostgreSQL 에서 목록 검색 500(null 파라미터 타입), 메일 health 로 인한 상시 DOWN, Nginx upstream 이름 충돌.
→ 같은 문제가 다시 생기지 않도록 모든 API 통합 테스트를 PostgreSQL(Testcontainers)에서도 돌리게 했습니다.

## 다중 서버로 갈 때

- 스케줄러: ShedLock(JDBC)으로 한 인스턴스만 실행 (지금도 유니크 제약으로 중복 발송은 없음)
- 추출 요청 제한(`ParseRateLimiter`)·로그인 시도 제한(`LoginThrottle`): 메모리 → Redis
- 통계: 사용자별 짧은 캐시 + 상태 변경 이벤트로 무효화
