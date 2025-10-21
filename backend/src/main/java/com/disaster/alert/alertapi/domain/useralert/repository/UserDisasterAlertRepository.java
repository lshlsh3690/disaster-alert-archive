package com.disaster.alert.alertapi.domain.useralert.repository;

import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserDisasterAlertRepository extends JpaRepository<UserDisasterAlert, Long> {
    Optional<UserDisasterAlert> findByIdAndIsDeletedFalse(Long id);
    /**
     * 특정 ID와 작성자 ID로 조회
     */
    Optional<UserDisasterAlert> findByIdAndCreatedById(Long id, Long createdById);

    /**
     * 특정 작성자의 제보 목록 조회
     */
    @Query("SELECT ua FROM UserDisasterAlert ua WHERE ua.createdById = :createdById AND ua.isDeleted = false")
    Page<UserDisasterAlert> findByCreatedById(Pageable pageable, Long createdById);

    /**
     * 전체 제보 목록 조회 (Regions 포함)
     * N+1 문제 방지를 위한 fetch join
     */
    @Query("SELECT DISTINCT ua FROM UserDisasterAlert ua LEFT JOIN FETCH ua.regions WHERE ua.isDeleted = false")
    Page<UserDisasterAlert> findAllWithRegions(Pageable pageable);

    @Query("select count(ua) from UserDisasterAlert ua where ua.isDeleted = false")
    long countAllActive();

    @Query("select count(ua) from UserDisasterAlert ua where ua.isDeleted = false and ua.createdAt >= CURRENT_DATE")
    long countTodayActive();
}
