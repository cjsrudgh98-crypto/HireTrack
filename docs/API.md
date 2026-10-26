# API 요약

전체 명세(요청·응답 스키마)는 실행 중인 서버의 **Swagger UI** `/swagger-ui.html` 에서 볼 수 있습니다(코드에서 자동 생성).

공통: `Authorization: Bearer {token}`, JSON. 에러는 항상 `{"code": "...", "message": "..."}`.

## 인증

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/auth/signup` | 회원가입 (201, 웹앱 토큰 발급) |
| POST | `/api/auth/login` | 로그인 — 웹앱 토큰(`scope=APP`) |
| POST | `/api/auth/extension/login` | 확장 프로그램 로그인 — 범위 제한 토큰(`scope=EXTENSION`) |
| GET | `/api/users/me` | 내 정보 |

## 공고·지원

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/postings?status=&keyword=&employmentType=&location=` | 내 공고 목록 |
| POST | `/api/postings` | 공고 등록 (지원 레코드 `INTERESTED` 자동 생성) |
| GET · PUT · DELETE | `/api/postings/{id}` | 상세 · 수정 · 삭제(이력·자소서·면접 함께) |
| PATCH | `/api/postings/{id}/status` | 상태 변경 (이력 기록, 첫 지원 시 자소서 제출본 고정) |
| PATCH | `/api/postings/{id}/memo` | 메모 |
| GET | `/api/postings/{id}/history` | 상태 이력 (`reverted` 포함) |
| POST | `/api/postings/parse` | URL → 서버가 1회 요청해 추출 (저장 안 함) |
| POST | `/api/postings/parse-html` | URL + 렌더링된 HTML → 추출 (확장 프로그램용, 서버 외부 요청 없음) |

## 자기소개서

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET · POST | `/api/postings/{id}/questions` | 문항 목록(최신 답변·제출본·임시저장 포함) · 추가 |
| PUT · DELETE | `/api/questions/{qid}` | 문항 수정 · 삭제 (제출본 고정 후 409) |
| GET · POST | `/api/questions/{qid}/answers` | 버전 목록 · 새 버전 `{content, baseVersion, sourceTemplateId}` |
| GET | `/api/questions/{qid}/answers/{version}` | 특정 버전 |
| POST | `/api/questions/{qid}/answers/{version}/submit` | 제출본 직접 지정 |
| PUT | `/api/questions/{qid}/draft` | 자동 임시저장 |
| GET · POST · PUT · DELETE | `/api/templates[/{id}]` | 답변 라이브러리 (`?category=`) |
| GET | `/api/answers/search?keyword=&category=` | 과거 답변 검색 |

## 면접·캘린더

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET · POST | `/api/postings/{id}/interviews` | 면접 일정 조회 · 등록 |
| PUT · DELETE | `/api/interviews/{iid}` | 수정 · 삭제 |
| GET | `/api/interviews/upcoming` | 다가오는 면접 |
| GET | `/api/users/me/calendar` | 캘린더 구독 URL (처음 조회 시 발급) |
| POST | `/api/users/me/calendar/regenerate` | 구독 URL 재발급 |
| GET | `/api/calendar/{token}.ics` | iCal 피드 (인증 헤더 없음, 토큰이 비밀키) |

## 알림·통계

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET · PUT | `/api/users/me/notification-settings` | 마감 알림 설정 |
| GET | `/api/stats/summary` · `/funnel` · `/weekly?from=` · `/response-time` · `/breakdown?by=` | 통계 |

## 에러 코드

`400 INVALID_INPUT` / `INVALID_URL` · `401 UNAUTHORIZED` / `INVALID_CREDENTIALS` · `403 FORBIDDEN` ·
`404 POSTING_NOT_FOUND` / `QUESTION_NOT_FOUND` / `ANSWER_NOT_FOUND` / `TEMPLATE_NOT_FOUND` / `INTERVIEW_NOT_FOUND` / `CALENDAR_NOT_FOUND` ·
`405 METHOD_NOT_ALLOWED` · `409 EMAIL_DUPLICATED` / `VERSION_CONFLICT` / `ANSWER_LOCKED` / `SUBMIT_NOT_ALLOWED` / `CONFLICT`(동시 요청 충돌, 다시 시도) · `413 PAYLOAD_TOO_LARGE` · `415 UNSUPPORTED_MEDIA_TYPE` · `429 TOO_MANY_REQUESTS`
