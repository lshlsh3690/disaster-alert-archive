package com.disaster.alert.alertapi.domain.event.controller;

import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyCheckAlertsResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyChecksResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyScoresResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDisagreementsResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardHardPairGradesResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardJudgeVersionsResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringPairLabelRequest;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringPairLabelResponse;
import com.disaster.alert.alertapi.domain.event.dto.EvalLabelResponse;
import com.disaster.alert.alertapi.domain.event.dto.EvalLabelUpdateRequest;
import com.disaster.alert.alertapi.domain.event.dto.EvalLabelsResponse;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;
import com.disaster.alert.alertapi.domain.event.service.ClusteringDailyCheckService;
import com.disaster.alert.alertapi.domain.event.service.ClusteringDashboardService;
import com.disaster.alert.alertapi.domain.event.service.ClusteringPairLabelService;
import com.disaster.alert.alertapi.domain.event.service.EvaluationLabelService;
import com.disaster.alert.alertapi.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 클러스터링 운영 검증 관리자 API(조회 계약: specs/008 contracts/admin-clustering-api.md,
 * 불일치 쌍 사람 라벨링 계약: specs/009 contracts/labeling-api.md).
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
    private final ClusteringPairLabelService pairLabelService;
    private final ClusteringDailyCheckService dailyCheckService;
    private final EvaluationLabelService evaluationLabelService;

    /** 날짜별 일일 채점. from·to 는 yyyy-MM-dd(양 끝 포함). */
    @GetMapping("/daily-scores")
    public ResponseEntity<ApiResponse<ClusteringDashboardDailyScoresResponse>> dailyScores(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.dailyScores(from, to)));
    }

    /**
     * 불일치 쌍(과병합·과소병합). kind 생략 시 두 종류 모두, size 는 1~100(기본 20),
     * unlabeledOnly=true 면 사람 라벨이 없는 쌍만(기본 false).
     */
    @GetMapping("/disagreements")
    public ResponseEntity<ApiResponse<ClusteringDashboardDisagreementsResponse>> disagreements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Disagreement kind,
            @RequestParam(defaultValue = "false") boolean unlabeledOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.disagreements(from, to, kind, unlabeledOnly, page, size)));
    }

    /** 불일치 쌍에 사람 라벨을 저장한다(쌍마다 하나, 다시 저장하면 덮어쓴다). 회원 id 는 로그인 정보에서 온다. */
    @PutMapping("/disagreement-labels")
    public ResponseEntity<ApiResponse<ClusteringPairLabelResponse>> saveLabel(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @Valid @RequestBody ClusteringPairLabelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pairLabelService.save(memberId, request)));
    }

    /** 어려운 쌍(지금 불일치 + 사람 라벨 있음)에 대한 판사 버전별 성적. */
    @GetMapping("/hard-pair-grades")
    public ResponseEntity<ApiResponse<ClusteringDashboardHardPairGradesResponse>> hardPairGrades() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.hardPairGrades()));
    }

    /** 날짜별 구조 검사(미배정·임베딩 없음). */
    @GetMapping("/daily-checks")
    public ResponseEntity<ApiResponse<ClusteringDashboardDailyChecksResponse>> dailyChecks(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.dailyChecks(from, to)));
    }

    /** 구조 검사 표의 한 날짜 행을 펼쳤을 때 보이는 그날의 문제 알림(미배정 또는 임베딩 없음). date 는 yyyy-MM-dd. */
    @GetMapping("/daily-checks/{date}/alerts")
    public ResponseEntity<ApiResponse<ClusteringDashboardDailyCheckAlertsResponse>> dailyCheckAlerts(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(dailyCheckService.problemAlerts(date)));
    }

    /** 평가 셋 사람 라벨 목록. dataset·label 은 선택 필터, size 는 1~100(기본 20). */
    @GetMapping("/eval-labels")
    public ResponseEntity<ApiResponse<EvalLabelsResponse>> evalLabels(
            @RequestParam(required = false) String dataset,
            @RequestParam(required = false) Label label,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(evaluationLabelService.list(dataset, label, page, size)));
    }

    /** 평가 셋 라벨을 고친다. 사유 필수, 같은 라벨이면 아무것도 바꾸지 않는다. 회원 id 는 로그인 정보에서 온다. */
    @PutMapping("/eval-labels/{id}")
    public ResponseEntity<ApiResponse<EvalLabelResponse>> updateEvalLabel(
            @AuthenticationPrincipal(expression = "id") Long memberId,
            @PathVariable long id,
            @Valid @RequestBody EvalLabelUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(evaluationLabelService.update(memberId, id, request)));
    }

    /** 판사 버전 이력. */
    @GetMapping("/judge-versions")
    public ResponseEntity<ApiResponse<ClusteringDashboardJudgeVersionsResponse>> judgeVersions() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.judgeVersions()));
    }
}
