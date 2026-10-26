package com.hiretrack.essay.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 제출본 고정 규칙. 문항당 제출본은 최대 1개이고, 한 번 고정되면 새 버전을 쌓을 수 없다. */
public final class Submission {

    private Submission() {
    }

    /** 최신 버전을 제출본으로 고정한다. 이미 제출본이 있거나 답변이 없으면 아무것도 하지 않는다. */
    public static Optional<EssayAnswer> freezeLatest(List<EssayAnswer> versions) {
        if (versions.stream().anyMatch(EssayAnswer::isSubmitted)) return Optional.empty();
        Optional<EssayAnswer> latest = versions.stream().max(Comparator.comparingInt(EssayAnswer::getVersion));
        latest.ifPresent(EssayAnswer::markSubmitted);
        return latest;
    }

    public static void freeze(EssayAnswer answer) {
        answer.markSubmitted();
    }

    public static void unfreeze(EssayAnswer answer) {
        answer.unmarkSubmitted();
    }
}
