package com.disaster.alert.alertapi.domain.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByDisasterAlert_IdAndIsDeletedFalse(Long alertId, Pageable pageable);
    Page<Comment> findByUserDisasterAlert_IdAndIsDeletedFalse(Long userAlertId, Pageable pageable);

    Page<Comment> findByIsDeletedFalse(Pageable pageable);
}


