package com.disaster.alert.alertapi.domain.event.controller;

import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyChecksResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyScoresResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDisagreementsResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardJudgeVersionsResponse;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;
import com.disaster.alert.alertapi.domain.event.service.ClusteringDashboardService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 클러스터링 운영 검증 관리자 조회 API(읽기 전용, 계약: specs/008 contracts/admin-clustering-api.md).
 *
 * <p><b>인증</b>: {@code SecurityConfig} 는 로그인만 요구하고, ADMIN 여부는 클래스 단위
 * {@code @PreAuthorize("hasRole('ADMIN')")} 가 판정한다(기존 {@code AdminController} 와 같은 방식).
 *
 * <p>Red 단계 뼈대 - 빈 응답을 돌려주는 service 를 부르기만 한다.
 */
@RestController
@RequestMapping("/api/v1/admin/clustering")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ClusteringAdminController {

    private final ClusteringDashboardService dashboardService;

    /** 날짜별 일일 채점. from·to 는 yyyy-MM-dd(양 끝 포함). */
    @GetMapping("/daily-scores")
    public ResponseEntity<ApiResponse<ClusteringDashboardDailyScoresResponse>> dailyScores(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.dailyScores(from, to)));
    }

    /** 불일치 쌍(과병합·과소병합). kind 생략 시 두 종류 모두, size 는 1~100(기본 20). */
    @GetMapping("/disagreements")
    public ResponseEntity<ApiResponse<ClusteringDashboardDisagreementsResponse>> disagreements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Disagreement kind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.disagreements(from, to, kind, page, size)));
    }

    /** 날짜별 구조 검사(미배정·임베딩 없음). */
    @GetMapping("/daily-checks")
    public ResponseEntity<ApiResponse<ClusteringDashboardDailyChecksResponse>> dailyChecks(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.dailyChecks(from, to)));
    }

    /** 판사 버전 이력. */
    @GetMapping("/judge-versions")
    public ResponseEntity<ApiResponse<ClusteringDashboardJudgeVersionsResponse>> judgeVersions() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.judgeVersions()));
    }
}
