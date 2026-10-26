package com.hiretrack.posting;

/**
 * 공고 모듈이 발행하는 이벤트. 같은 트랜잭션 안에서 동기로 처리된다(@EventListener).
 * 자소서 모듈이 공고 모듈에 직접 의존하지 않고 제출본 고정·정리를 할 수 있게 한다.
 */
public final class PostingEvents {

    private PostingEvents() {
    }

    /** 처음 APPLIED 로 바뀜 → 각 문항의 최신 답변을 제출본으로 고정 */
    public record FirstApplied(Long postingId) {
    }

    /** 실수로 본 첫 지원을 되돌림 → 자동 고정했던 제출본 해제 */
    public record ApplyReverted(Long postingId) {
    }

    /** 공고 삭제 직전 → 딸린 자소서 데이터 정리 */
    public record Deleting(Long postingId) {
    }
}
