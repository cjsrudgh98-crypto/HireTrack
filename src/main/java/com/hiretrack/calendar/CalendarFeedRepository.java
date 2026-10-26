package com.hiretrack.calendar;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarFeedRepository extends JpaRepository<CalendarFeed, Long> {

    Optional<CalendarFeed> findByToken(String token);
}
