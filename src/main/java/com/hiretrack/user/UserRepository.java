package com.hiretrack.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 사용자당 한 줄인 설정(알림·캘린더)을 처음 만들 때 동시 요청이 둘 다 INSERT 하지 않도록 사용자 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdateById(@Param("id") Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
