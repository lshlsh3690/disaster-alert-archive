package com.disaster.alert.alertapi.domain.weather.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * ASOS 시간자료 백필의 (월 × 지점) 단위 진행 상태.
 *
 * <p>한 row = (year_month, stn_id) 셀. 백필러는 이 테이블을 보고
 * 미완료 셀만 골라 다시 시도한다 — API 차단/네트워크 오류로 멈춰도 재개 자연 처리.
 *
 * <p>총 셀 수: ASOS 지점 92 × 백필 기간 33개월 ≈ 3,036 셀.
 */
@Entity
@Table(name = "weather_backfill_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WeatherBackfillProgress {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @EmbeddedId
    private WeatherBackfillProgressId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackfillStatus status;

    /** 적재 성공 시 저장한 시군구 row 수 (지점 1개 fan-out 결과). */
    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static WeatherBackfillProgress newInProgress(YearMonth ym, String stnId) {
        return WeatherBackfillProgress.builder()
                .id(WeatherBackfillProgressId.of(ym.toString(), stnId))
                .status(BackfillStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now(KST))
                .build();
    }

    public void markInProgress() {
        this.status = BackfillStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now(KST);
        this.errorMsg = null;
    }

    public void markDone(int rowCount) {
        this.status = BackfillStatus.DONE;
        this.rowCount = rowCount;
        this.finishedAt = LocalDateTime.now(KST);
        this.errorMsg = null;
    }

    public void markFailed(String errorMsg) {
        this.status = BackfillStatus.FAILED;
        this.finishedAt = LocalDateTime.now(KST);
        // 너무 긴 메시지 절단 (DB TEXT 라 크기 제한은 없지만 가독성 위해)
        this.errorMsg = errorMsg == null
                ? null
                : (errorMsg.length() > 1000 ? errorMsg.substring(0, 1000) : errorMsg);
    }
}
