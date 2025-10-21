package com.disaster.alert.alertapi.domain.comment;

import com.disaster.alert.alertapi.domain.comment.dto.CommentDtos;
import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;
import com.disaster.alert.alertapi.domain.useralert.repository.UserDisasterAlertRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final DisasterAlertRepository disasterAlertRepository;
    private final UserDisasterAlertRepository userDisasterAlertRepository;
    private final EntityManager entityManager;

    public CommentDtos.Response create(Long memberId, CommentDtos.CreateRequest req) {
        String source = req.getSource() == null ? null : req.getSource().trim().toUpperCase();
        if (!"OFFICIAL".equals(source) && !"USER".equals(source)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "source는 OFFICIAL 또는 USER만 허용됩니다.");
        }

        Long targetId = req.getTargetId();
        if (targetId == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "targetId가 필요합니다.");
        }

        Member authorRef = entityManager.getReference(Member.class, memberId);

        Comment comment = new Comment();
        setContent(comment, req.getContent());
        setAuthor(comment, authorRef);

        if ("OFFICIAL".equals(source)) {
            if (!disasterAlertRepository.existsById(targetId)) {
                throw new CustomException(ErrorCode.DISASTER_ALERT_NOT_FOUND, "해당 공식 재난문자를 찾을 수 없습니다.");
            }
            DisasterAlert daRef = entityManager.getReference(DisasterAlert.class, targetId);
            setDisasterAlert(comment, daRef);
        } else {
            if (!userDisasterAlertRepository.findByIdAndIsDeletedFalse(targetId).isPresent()) {
                throw new CustomException(ErrorCode.USER_ALERT_NOT_FOUND, "해당 사용자 제보를 찾을 수 없습니다.");
            }
            UserDisasterAlert udaRef = entityManager.getReference(UserDisasterAlert.class, targetId);
            setUserDisasterAlert(comment, udaRef);
        }

        Comment saved = commentRepository.save(comment);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CommentDtos.Response> list(String source, Long targetId, Pageable pageable) {
        String normalized = source == null ? null : source.trim().toUpperCase();
        if (!"OFFICIAL".equals(normalized) && !"USER".equals(normalized)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "source는 OFFICIAL 또는 USER만 허용됩니다.");
        }
        if (targetId == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "targetId가 필요합니다.");
        }

        Page<Comment> page = "OFFICIAL".equals(normalized)
                ? commentRepository.findByDisasterAlert_IdAndIsDeletedFalse(targetId, pageable)
                : commentRepository.findByUserDisasterAlert_IdAndIsDeletedFalse(targetId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CommentDtos.Response> latest(Pageable pageable) {
        Page<Comment> page = commentRepository.findByIsDeletedFalse(pageable);
        return page.map(this::toResponse);
    }

    public CommentDtos.Response update(Long id, CommentDtos.UpdateRequest req, Long requesterId, MemberRole requesterRole) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다."));

        Long authorId = comment.getAuthor().getId();
        boolean isOwner = authorId != null && authorId.equals(requesterId);
        boolean isAdmin = requesterRole != null && requesterRole.name().equals("ADMIN");
        if (!(isOwner || isAdmin)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "댓글 수정 권한이 없습니다.");
        }
        setContent(comment, req.getContent());
        return toResponse(comment);
    }

    public void delete(Long id, Long requesterId, MemberRole requesterRole) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다."));

        Long authorId = comment.getAuthor().getId();
        boolean isOwner = authorId != null && authorId.equals(requesterId);
        boolean isAdmin = requesterRole != null && requesterRole.name().equals("ADMIN");
        if (!(isOwner || isAdmin)) {
            throw new CustomException(ErrorCode.FORBIDDEN, "댓글 삭제 권한이 없습니다.");
        }
        try {
            java.lang.reflect.Field f = Comment.class.getDeclaredField("isDeleted");
            f.setAccessible(true);
            f.set(comment, true);
        } catch (Exception ignored) {}
    }

    private CommentDtos.Response toResponse(Comment c) {
        return CommentDtos.Response.builder()
                .id(c.getId())
                .content(c.getContent())
                .authorId(c.getAuthor() != null ? c.getAuthor().getId() : null)
                .authorNickname(c.getAuthor() != null ? c.getAuthor().getNickname() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .edited(c.getUpdatedAt() != null && c.getCreatedAt() != null && c.getUpdatedAt().isAfter(c.getCreatedAt()))
                .build();
    }

    private void setContent(Comment target, String content) {
        if (content == null || content.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "댓글 내용이 비어 있습니다.");
        }
        target.getClass(); // NOP to keep style similar
        try {
            java.lang.reflect.Field f = Comment.class.getDeclaredField("content");
            f.setAccessible(true);
            f.set(target, content.trim());
        } catch (Exception ignored) {}
    }

    private void setAuthor(Comment target, Member author) {
        try {
            java.lang.reflect.Field f = Comment.class.getDeclaredField("author");
            f.setAccessible(true);
            f.set(target, author);
        } catch (Exception ignored) {}
    }

    private void setDisasterAlert(Comment target, DisasterAlert da) {
        try {
            java.lang.reflect.Field f = Comment.class.getDeclaredField("disasterAlert");
            f.setAccessible(true);
            f.set(target, da);
        } catch (Exception ignored) {}
    }

    private void setUserDisasterAlert(Comment target, UserDisasterAlert uda) {
        try {
            java.lang.reflect.Field f = Comment.class.getDeclaredField("userDisasterAlert");
            f.setAccessible(true);
            f.set(target, uda);
        } catch (Exception ignored) {}
    }
}


