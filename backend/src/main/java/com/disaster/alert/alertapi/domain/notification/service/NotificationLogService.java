package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.notification.dto.NotificationLogDtos;
import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;
import com.disaster.alert.alertapi.domain.notification.repository.UserNotificationLogRepository;
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

    private final UserNotificationLogRepository userNotificationLogRepository;

    public NotificationLogDtos.PageResponse getMyNotifications(Long memberId, Pageable pageable) {
        Page<UserNotificationLog> page = userNotificationLogRepository
                .findByMemberIdWithAlertOrderByCreatedAtDesc(memberId, pageable);

        long unreadCount = userNotificationLogRepository.countByMemberIdAndIsReadFalse(memberId);

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
        UserNotificationLog log = userNotificationLogRepository
                .findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_LOG_NOT_FOUND));
        log.markAsRead();
    }
}
