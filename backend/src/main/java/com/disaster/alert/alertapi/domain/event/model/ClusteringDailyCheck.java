package com.disaster.alert.alertapi.domain.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 클러스터링 일일 구조 검사 결과 - 날짜당 1줄.
 *
 * <p>그 날짜의 사건(INCIDENT) 알림이 몇 건이고, 그 중 이벤트에 못 들어간 것(미배정)과 임베딩이 없는 것이
 * 몇 건인지 센 값이다. 같은 날짜를 다시 검사하면 {@link #update}로 값을 덮어쓴다.
 */
@Entity
@Table(name = "clustering_daily_check")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusteringDailyCheck {

    @Id
    @Column(name = "check_date")
    private LocalDate checkDate;

    @Column(name = "incident_count", nullable = false)
    private int incidentCount;

    @Column(name = "unassigned_count", nullable = false)
    private int unassignedCount;

    @Column(name = "no_embedding_count", nullable = false)
    private int noEmbeddingCount;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    public static ClusteringDailyCheck of(LocalDate checkDate, int incidentCount, int unassignedCount,
                                          int noEmbeddingCount, LocalDateTime checkedAt) {
        ClusteringDailyCheck check = new ClusteringDailyCheck();
        check.checkDate = checkDate;
        check.update(incidentCount, unassignedCount, noEmbeddingCount, checkedAt);
        return check;
    }

    /** 같은 날짜를 다시 검사한 결과로 건수와 검사 시각을 덮어쓴다. */
    public void update(int incidentCount, int unassignedCount, int noEmbeddingCount, LocalDateTime checkedAt) {
        this.incidentCount = incidentCount;
        this.unassignedCount = unassignedCount;
        this.noEmbeddingCount = noEmbeddingCount;
        this.checkedAt = checkedAt;
    }
}
