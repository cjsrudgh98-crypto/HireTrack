package com.hiretrack.posting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hiretrack.posting.domain.StatusHistory;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByApplicationIdOrderByChangedAtAscIdAsc(Long applicationId);
}
