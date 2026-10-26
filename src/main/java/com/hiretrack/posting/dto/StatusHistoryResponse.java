package com.hiretrack.posting.dto;

import java.time.OffsetDateTime;

import com.hiretrack.posting.domain.ApplicationStatus;
import com.hiretrack.posting.domain.StatusHistory;

public record StatusHistoryResponse(
        Long id,
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        OffsetDateTime changedAt,
        boolean reverted
) {

    public static StatusHistoryResponse from(StatusHistory history) {
        return new StatusHistoryResponse(history.getId(), history.getFromStatus(), history.getToStatus(),
                Times.toOffset(history.getChangedAt()), history.isReverted());
    }
}
