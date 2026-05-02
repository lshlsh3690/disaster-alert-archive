package com.disaster.alert.alertapi.domain.community.repository;

import com.disaster.alert.alertapi.domain.community.model.CommunityPost;
import com.disaster.alert.alertapi.domain.community.model.CommunityPostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    Page<CommunityPost> findByCategoryAndIsDeletedFalse(CommunityPostCategory category, Pageable pageable);
}


