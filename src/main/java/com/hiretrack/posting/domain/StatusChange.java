package com.hiretrack.posting.domain;

/**
 * 상태 변경 결과.
 *
 * @param changed       실제로 상태가 바뀌었는지
 * @param firstApplied  이번 변경으로 처음 지원(APPLIED)했는지 — 자소서 제출본 고정 시점
 * @param applyReverted 실수로 본 첫 지원을 되돌렸는지 — 제출본 고정 해제 시점
 */
public record StatusChange(boolean changed, boolean firstApplied, boolean applyReverted) {

    public static final StatusChange NONE = new StatusChange(false, false, false);
}
