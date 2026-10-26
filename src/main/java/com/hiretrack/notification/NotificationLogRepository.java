package com.hiretrack.notification;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByPostingIdIn(Collection<Long> postingIds);

    List<NotificationLog> findByUserId(Long userId);

    @Modifying
    @Query("delete from NotificationLog l where l.postingId = :postingId")
    void deleteByPostingId(@Param("postingId") Long postingId);
}
