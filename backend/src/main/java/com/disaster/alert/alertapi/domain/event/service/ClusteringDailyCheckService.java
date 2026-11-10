package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyCheck;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringDailyCheckRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Red 단계 뼈대 - 구현은 Green 에서
/**
 * 하루 한 번, 전날 사건 알림 중 이벤트 미배정 건수를 세어 남기는 일일 구조 검사.
 */
@Service
public class ClusteringDailyCheckService {

    private final JdbcTemplate jdbcTemplate;
    private final ClusteringDailyCheckRepository repository;

    public ClusteringDailyCheckService(JdbcTemplate jdbcTemplate, ClusteringDailyCheckRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    /** 뼈대: 아무것도 세지 않고 저장하지 않으며 세 건수가 모두 0 인 결과를 돌려준다. */
    public ClusteringDailyCheck check(LocalDate date) {
        return ClusteringDailyCheck.of(date, 0, 0, 0, LocalDateTime.now());
    }
}
