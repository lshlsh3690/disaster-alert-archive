package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.notification.dto.NotificationLogDtos;
import com.disaster.alert.alertapi.domain.notification.model.NotificationLog;
import com.disaster.alert.alertapi.domain.notification.repository.NotificationLogRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationLogDtos.PageResponse getMyNotifications(Long memberId, Pageable pageable) {
        Page<NotificationLog> page = notificationLogRepository
                .findByMemberIdWithAlertOrderBySentAtDesc(memberId, pageable);
        long unreadCount = notificationLogRepository.countByMemberIdAndIsReadFalse(memberId);

        List<NotificationLogDtos.ListItem> content = page.getContent().stream()
                .map(NotificationLogDtos.ListItem::from)
                .toList();

        return new NotificationLogDtos.PageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                unreadCount
        );
    }

    @Transactional
    public void markAsRead(Long id, Long memberId) {
        NotificationLog log = notificationLogRepository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new EntityNotFoundException("알림을 찾을 수 없습니다."));
        log.markAsRead();
    }
}
