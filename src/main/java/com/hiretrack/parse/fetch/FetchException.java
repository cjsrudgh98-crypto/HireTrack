package com.hiretrack.parse.fetch;

/** 페이지를 가져오지 못한 경우. 사용자에게는 "자동 추출 실패"로 안내하고 수동 입력으로 넘긴다. */
public class FetchException extends RuntimeException {

    public FetchException(String message) {
        super(message);
    }

    public FetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
