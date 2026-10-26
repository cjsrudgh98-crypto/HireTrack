package com.hiretrack.posting.domain;

/**
 * 지원 상태. 불합격은 어느 단계에서든, 나머지도 자유롭게 이동할 수 있고 모든 변경은 이력으로 남긴다.
 */
public enum ApplicationStatus {
    INTERESTED(0),
    APPLIED(1),
    DOCUMENT_PASSED(2),
    INTERVIEW(3),
    ACCEPTED(4),
    /** 단계가 아니라 결과. 어느 단계에서 떨어졌는지는 이력의 최고 도달 단계로 판단한다. */
    REJECTED(-1);

    private final int stage;

    ApplicationStatus(int stage) {
        this.stage = stage;
    }

    /** 퍼널 단계 순서. 뒤 단계에 도달했다면 앞 단계는 통과한 것으로 본다. */
    public int stage() {
        return stage;
    }
}
