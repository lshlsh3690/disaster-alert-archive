package com.disaster.alert.alertapi.domain.community.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.community.dto.CommunityDtos;
import com.disaster.alert.alertapi.domain.community.model.CommunityPost;
import com.disaster.alert.alertapi.domain.community.model.CommunityPostCategory;
import com.disaster.alert.alertapi.domain.community.repository.CommunityPostRepository;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommunityService {
    private final CommunityPostRepository repository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<CommunityDtos.Response> list(CommunityPostCategory category, Pageable pageable) {
        return repository.findByCategoryAndIsDeletedFalse(category, pageable).map(CommunityDtos.Response::from);
    }

    public CommunityDtos.Response create(Long memberId, MemberRole role, CommunityDtos.CreateRequest req) {
        if (req.getCategory() == CommunityPostCategory.NOTICE && (role == null || !role.name().equals("ADMIN"))) {
            throw new CustomException(ErrorCode.FORBIDDEN, "공지사항은 관리자만 작성할 수 있습니다.");
        }
        Member authorRef = entityManager.getReference(Member.class, memberId);
        CommunityPost p = new CommunityPost();
        try {
            var f = CommunityPost.class.getDeclaredField("category"); f.setAccessible(true); f.set(p, req.getCategory());
            f = CommunityPost.class.getDeclaredField("author"); f.setAccessible(true); f.set(p, authorRef);
            f = CommunityPost.class.getDeclaredField("title"); f.setAccessible(true); f.set(p, req.getTitle().trim());
            f = CommunityPost.class.getDeclaredField("content"); f.setAccessible(true); f.set(p, req.getContent().trim());
        } catch (Exception ignored) {}
        return CommunityDtos.Response.from(repository.save(p));
    }
}


