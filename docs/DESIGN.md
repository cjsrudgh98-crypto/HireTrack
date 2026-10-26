# 설계 노트 — 도메인 규칙과 기술적 결정

## 1. 공고·지원·상태 이력 (1단계)

- 공고(`job_posting`) : 지원(`application`) = 1:1. 공고를 등록하면 지원 레코드가 `INTERESTED` 로 자동 생성됩니다.
- 상태: `INTERESTED`(관심) · `APPLIED`(지원 완료) · `DOCUMENT_PASSED`(서류 합격) · `INTERVIEW`(면접) · `ACCEPTED`(최종 합격) · `REJECTED`(불합격).
  자유롭게 이동할 수 있고 모든 변경은 `status_history` 에 남습니다(최초 등록도 `null → INTERESTED`).
- `applied_at` 은 `APPLIED` 로 **처음** 바뀔 때만 서버에서 기록합니다.
- **권한 체크**: 공고·자소서·면접 API 는 모두 `PostingService.getOwnedPosting()` 을 거칩니다 — 없으면 404, 남의 공고면 403.
- **트랜잭션**: 상태 변경과 이력 추가가 한 트랜잭션(cascade)으로 저장됩니다.
- **동시 상태 변경**: 같은 공고에 변경이 동시에 들어오면(빠른 연속 드래그, 웹·확장 동시 사용) 공고 행을 `SELECT … FOR UPDATE` 로 잠가 한 줄로 세웁니다.
  잠그지 않으면 두 요청이 같은 "이전 상태"를 읽어 이력 체인(`이전 상태 = 직전 이력의 다음 상태`)이 끊기고 통계가 틀어집니다.
  잠금 쿼리는 지원 레코드를 join fetch 하지 않습니다 — PostgreSQL 은 잠금 대기 후 잠근 행만 최신 값으로 다시 읽기 때문에, 함께 조인한 행은 옛 값이 됩니다(H2 에선 드러나지 않고 Testcontainers 테스트에서 잡힘).
- **처음 한 번 만드는 한 줄짜리 데이터**(알림 설정·캘린더 토큰·자소서 임시저장)는 부모 행(사용자·문항)을 잠근 뒤 조회→생성하고, 가입은 이메일 유니크 제약 위반을 409 로 바꿉니다.
  그래도 남는 제약 위반·잠금 실패는 전역 핸들러가 500 대신 `409 CONFLICT` 로 돌려줍니다.
- **낙관적 업데이트**: 카드를 드래그하면 캐시를 먼저 바꾸고, API 실패 시 스냅샷으로 되돌립니다(`frontend/src/hooks/usePostings.ts`, Vitest 로 롤백 검증).

## 2. URL 자동 추출 (2단계) → 확장 프로그램에서 재사용 (4단계)

**파서 체인 (전략 패턴, `parse/parser`)** — `PostingParser` 구현체를 `@Order` 순서로 실행하고, 앞 파서가 못 찾은 항목만 뒤 파서가 채웁니다.

| 순서 | 파서 | 대상 |
|---|---|---|
| 10 | `SaraminParser` | `saramin.co.kr` — 헤더·요약(dt/dd)·접수기간 |
| 20 | `JobKoreaParser` | `jobkorea.co.kr` — 제목 영역·요약 표·마감일 |
| 100 | `JsonLdParser` | 모든 사이트 — schema.org `JobPosting` (`@graph`·배열 포함, `validThrough` 는 서울 날짜로) |
| 200 | `OpenGraphParser` | 모든 사이트 — `og:title`·`og:site_name`·`<title>` |

입력 경로는 둘이고 파서 체인은 하나입니다.

| | 2단계: `POST /api/postings/parse` | 4단계: `POST /api/postings/parse-html` (확장 프로그램) |
|---|---|---|
| HTML 출처 | 서버가 URL 을 1회 요청 | 사용자 브라우저에 **이미 렌더링된** 화면 |
| JS 렌더링 · 로그인 필요 페이지 | 못 읽음 | 읽음 |
| 서버의 외부 요청 | 있음 → SSRF 방어 필요 | 없음 (URL 은 파서 선택에만 사용) |
| 크기 제한 | 응답 2MB 까지만 읽음 | 요청 본문 3MB(Content-Length 필터) · HTML 2MB(검증) |

> ⚠ 사람인·잡코리아 셀렉터와 테스트 픽스처(`src/test/resources/fixtures`)는 사이트 구조를 재현한 **합성 HTML** 입니다.
> 실제 공고 페이지를 저장해 픽스처를 교체하고 셀렉터를 맞춰야 합니다.

**SSRF 방어 (`UrlGuard`)**: http/https 만, 포트 80/443 만, `user@host` 거부. DNS 결과의 **모든 IP** 가 공인 주소여야 통과
(루프백, 사설망, 169.254 메타데이터, CGNAT, IPv6 ULA·링크로컬, IPv4-mapped IPv6 등 거부). 리다이렉트는 hop 마다 재검사.
남은 한계: 검사 후 연결 시 DNS 를 다시 조회하므로 DNS rebinding 은 완전히 막지 못합니다 → 운영에서는 방화벽에서 내부 대역 아웃바운드 차단 권장.

**이용약관·부하**: 사용자가 넣은 URL 만 그때 1회 요청, 목록 순회·주기 수집 없음. 사용자당 분당 10회(`ParseRateLimiter`, 메모리 기반 — 다중 서버면 Redis).

**파서 깨짐 감지**: 사이트 전용 파서 실패 시 누적 실패율과 함께 WARN 로그(`parser-failure source=SARAMIN ...`),
같은 값을 Micrometer 카운터 `hiretrack.parser.results{source,result}` 로 남깁니다.

## 3. 크롬 확장 프로그램 (4단계)

```
extension/
├── manifest.json   # MV3, permissions: activeTab · scripting · storage (호스트 권한 없음)
├── content.js      # 현재 탭에 주입 → 렌더링된 HTML 반환 (스크립트·스타일 제거, JSON-LD 는 유지, 2MB 상한)
├── background.js   # 서비스 워커: API 호출, 토큰 보관(chrome.storage.local)
└── popup/          # 로그인 → 추출 결과 폼 → 확인·수정 → 저장 → "보드에서 보기"
```

- **activeTab**: 사용자가 아이콘을 누른 그 탭에, 누른 그 순간에만 접근합니다. `<all_urls>` 같은 상시 권한이 없어 설치 경고도 없습니다.
- **범위 제한 토큰**: 팝업 로그인은 `POST /api/auth/extension/login` 으로 `scope=EXTENSION` 토큰을 받습니다.
  이 토큰은 `parse-html`, `POST /api/postings`, `GET /api/users/me` 만 허용되고 나머지는 403 — 확장 저장소의 토큰이 새어도 자소서·통계는 보호됩니다.
- **CORS**: `/api/**` 에만, 허용 출처는 설정값(`CORS_ALLOWED_ORIGINS`). `manifest.json` 의 `key` 로 확장 ID 를 고정해 운영에서는 정확히 한 출처만 허용합니다.
  웹앱은 Nginx 뒤 같은 출처라 CORS 대상이 아닙니다(그래서 Nginx·Vite 프록시 모두 `Host` 를 유지).
- 검증: 헤드리스 Edge 에 확장을 로드해 서비스 워커 기동, content.js 추출, 로그인→추출→저장, 범위 밖 API 403 까지 자동 확인.
  툴바 아이콘 클릭 자체는 자동화할 수 없어 수동 확인이 필요합니다.

## 4. 마감 알림 (2단계)

- 스케줄러가 **매시 정각(서울)** 실행되고, 사용자가 고른 시각(`send_hour`, 기본 9시)인 사용자에게만 보냅니다.
- 대상: `INTERESTED` 이고 마감이 D-7/D-3/D-1(사용자 선택)인 공고, 사용자별 **하루 한 통**.
- D-day 는 서버 시간대와 무관하게 **서울 날짜**로 계산(서울 00:30 = UTC 전날 15:30 인 경계를 테스트로 검증).
- 중복 방지: `notification_logs(posting_id, remind_day)` 유니크 + 기록·발송을 한 트랜잭션으로. 메일 실패 시 기록도 롤백 → 다음 실행에서 재시도.
- 다중 서버: 단일 서버라 ShedLock 은 넣지 않았습니다. 여러 대면 유니크 제약으로 중복 발송은 없지만 쿼리가 서버 수만큼 돌므로 그때 ShedLock(JDBC) 도입.
- 지표: `hiretrack.reminders{result=sent|failed|duplicate}`. 메일 서버 장애는 health 에 넣지 않습니다(배포 롤백 사유가 아님).

## 5. 캘린더 구독 피드 (4단계)

- `GET /api/calendar/{token}.ics` — 캘린더 앱은 인증 헤더를 못 보내므로 **URL 의 256비트 랜덤 토큰이 비밀키**입니다. 설정 화면에서 복사·재발급(이전 URL 즉시 무효).
- 포함: 지원 전(관심) 공고의 마감일(종일, `TRANSP:TRANSPARENT`) · 면접(시간·장소·유형, **1시간 전 VALARM**, 설명에 제출본 자소서 링크).
- 고정 UID(`posting-12-deadline@hiretrack`, `interview-7@hiretrack`) → 캘린더 앱이 수정·삭제를 같은 일정으로 인식.
- iCalendar 는 `IcsWriter`(RFC 5545)로 직접 작성: CRLF, **75옥텟 줄 접기(한글 3바이트를 바이트 단위로 계산)**, TEXT 이스케이프, 시각은 UTC(Z).
  ical4j 대신 직접 쓴 이유: 필요한 건 VEVENT/VALARM 몇 줄이라 의존성보다 테스트 가능한 작은 코드가 낫다고 판단.
- 한계: 캘린더 앱이 피드를 새로 읽는 주기(구글은 수 시간)에 따라 반영이 늦습니다. 즉시 반영이 필요하면 구글 캘린더 API(OAuth) 연동이 다음 단계.

## 6. 자기소개서 버전 관리 (3단계)

- **append-only**: `essay_answers` 는 내용을 수정하지 않고 새 행을 쌓습니다. 바뀌는 건 제출본 표시뿐. 임시저장은 `essay_drafts`(문항당 1행)로 분리.
- **제출본 고정**: 처음 `APPLIED` 가 될 때 공고 모듈이 `FirstApplied` 이벤트를 발행 → 자소서 리스너가 **같은 트랜잭션**에서 문항별 최신 버전을 고정.
  고정 후 새 버전·임시저장·문항 수정/삭제는 409 `ANSWER_LOCKED`. 10분 안에 지원을 되돌리면 고정도 풀립니다.
- **버전 번호 동시성** — 두 겹:
  1. `baseVersion`: 클라이언트가 본 최신 버전이 서버와 다르면 409 `VERSION_CONFLICT`(다른 탭에서 먼저 저장).
  2. `(question_id, version)` 유니크: 같은 순간 검사를 통과해도 DB 가 하나만 받음 → 409.
  자동 재시도를 하지 않는 이유: 다른 탭의 내용을 모른 채 그 위에 새 버전이 생기기 때문. 프론트는 쓰던 내용을 유지한 채 비교하게 합니다.
- **템플릿 출처**: `source_template_id`(FK 없음 — 템플릿 삭제 후에도 출처 유지). 템플릿에서 시작한 답변을 고친 버전도 출처를 이어받아 "템플릿별 서류 통과율"을 냅니다.
- 글자 수: 공백 포함·제외·바이트(한글 2, 영문·공백 1, 줄바꿈 2). 비교: `diff-match-patch` 문자 단위 + `cleanupSemantic`.

## 7. 통계 (3단계)

- **"도달 여부"로 집계**: `status_history` 에서 지원별 최고 도달 단계(JPQL 집계). 면접 후 불합격(현재 REJECTED)도 서류 통과, 단계를 건너뛰어도 앞 단계 통과.
- **결과 대기 분리**: "결과 나온 건 기준"과 "전체 기준"을 함께, 항상 분자·분모(`33% (1/3)`), 분모 0 이면 `rate: null`.
- **되돌린 변경**: 직전 변경을 10분 안에 그대로 되돌리면(A→B→A) 두 이력에 `reverted` — 타임라인엔 취소선, 통계에선 제외.
- **응답 소요 기간**: APPLIED 기록 후 첫 결과까지 서울 날짜 기준 일수(DB 별 날짜 함수 차이를 피하려 시각만 조회하고 앱에서 계산).
- **인덱스**: `status_history(application_id, to_status)`, `job_posting(user_id)`, `essay_questions(posting_id)`, `answer_templates(user_id, category)`, `interviews(posting_id)`, `interviews(starts_at)`.
- **캐싱(확장 시)**: 지금은 요청마다 집계. 커지면 사용자별 짧은 캐시 + `PostingEvents` 에서 무효화.

## 8. 테스트 전략

| 계층 | 도구 | 무엇을 |
|---|---|---|
| 단위 | JUnit 5 · Mockito | 상태 변경 규칙·되돌림, 권한 체크, 파서(픽스처), 날짜 해석, SSRF 판정, ICS 줄 접기 |
| 통합 | Spring Boot Test · **Testcontainers PostgreSQL** | Flyway 마이그레이션 + 엔티티 검증, 통계 쿼리, 동시 버전 저장, 알림 중복 방지, 모든 API 시나리오를 운영 DB 에서 |
| API | MockMvc | 인증·에러 형식·권한 거부·CORS preflight·요청 크기 상한·범위 제한 토큰 |
| 프론트 | Vitest · Testing Library | 칸반 낙관적 업데이트와 롤백, URL 추출 폼(채움·누락 표시·실패 안내), 날짜·글자 수 |
| E2E | Playwright | 가입→공고 등록→상태 변경→대시보드, 실제 마우스 드래그 후 새로고침 유지, 자소서 버전→지원→제출본 고정 |

- 같은 테스트 클래스를 H2 와 PostgreSQL 에서 각각 돌립니다(`*PostgresTest extends *Test`). Docker 가 없으면 PostgreSQL 쪽은 자동 건너뜀.
- 커버리지(JaCoCo): 라인 약 91%. 숫자보다 위 표의 핵심 규칙이 모두 테스트돼 있는지를 기준으로 삼았습니다.
