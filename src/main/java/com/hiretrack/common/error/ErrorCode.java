package com.hiretrack.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "자기소개서 문항을 찾을 수 없습니다."),
    ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 버전의 답변을 찾을 수 없습니다."),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "템플릿을 찾을 수 없습니다."),
    INTERVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "면접 일정을 찾을 수 없습니다."),
    CALENDAR_NOT_FOUND(HttpStatus.NOT_FOUND, "캘린더를 찾을 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JSON 형식으로 보내 주세요."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청 크기가 너무 큽니다."),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 먼저 새 버전이 저장됐습니다. 최신 버전을 확인한 뒤 다시 저장해 주세요."),
    ANSWER_LOCKED(HttpStatus.CONFLICT, "제출본이 고정된 문항은 수정할 수 없습니다."),
    SUBMIT_NOT_ALLOWED(HttpStatus.CONFLICT, "지원한 공고의 문항만, 아직 제출본이 없을 때 제출본으로 지정할 수 있습니다."),
    INVALID_URL(HttpStatus.BAD_REQUEST, "접근할 수 없는 주소입니다. 공개된 http/https 공고 주소를 입력해 주세요."),
    CONFLICT(HttpStatus.CONFLICT, "다른 요청과 동시에 처리되어 저장하지 못했습니다. 다시 시도해 주세요."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인에 여러 번 실패해 잠시 잠겼어요. 15분 뒤에 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
