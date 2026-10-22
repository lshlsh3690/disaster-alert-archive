package com.disaster.alert.alertapi.domain.disasteralert.tool;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 특정 과거 기간에 누락된 재난문자를 공공데이터포털에서 다시 받아와 채우는 백필 도구.
 *
 * <p><b>활성 조건</b>: Spring Profile {@code alert-missing-backfill} 활성 시에만 실행.
 * 일반 dev / 운영 부팅에서는 빈 등록 자체가 안 됨.
 *
 * <p><b>배경</b>: 2026-10-18 실측 — 맥북 셀프호스팅 전환·장애 대응 과정에서 2026-08-31 10:28~22:18
 * 구간(SN 268356~268450, 80건)이 수집되지 않은 채 비어있던 것을 공공API 원본과 DB를 SN 기준으로
 * 대조해 발견했다(docs/runbooks/ec2-to-macbook-migration.md 참고 — 이 기간은 맥북 이관 이전,
 * 원인은 특정하지 않음).
 *
 * <p>{@link DisasterAlertService#saveData(String)} 만 호출하고 알림 발송(FCM)·번역·클러스터링은
 * 절대 트리거하지 않는다 — 해당 side effect는 전부 {@code DisasterFetchScheduler}가 스케줄러
 * 호출 시점에만 따로 붙이는 것이고, {@code triggerNotification} 에는 "오래된 알림이면 건너뛴다"는
 * 가드가 전혀 없어 수 주 전 알림이라도 그대로 실사용자에게 푸시가 나간다. saveData 의 SN unique
 * 제약 기반 dedup 덕분에 겹치는 기간을 다시 돌려도 이미 저장된 건 조용히 건너뛰어 안전하다.
 *
 * <p><b>사용법</b> (SNS_FROM_DATE 는 실제 누락 구간보다 하루 여유를 두고 지정)
 * <pre>
 * export SPRING_PROFILES_ACTIVE=alert-missing-backfill
 * export BACKFILL_FROM_DATE=20260830
 * cd backend && set -a && source ../.env.dev && set +a
 * ./gradlew bootRun
 * </pre>
 */
@Component
@Profile("alert-missing-backfill")
@RequiredArgsConstructor
@Slf4j
public class DisasterAlertMissingDateBackfillTool implements ApplicationRunner {

    private final DisasterOpenApiClient disasterOpenApiClient;
    private final DisasterAlertService disasterAlertService;
    private final ObjectMapper objectMapper;

    @Value("${backfill.from-date}")
    private String fromDate; // yyyyMMdd

    private static final int NUM_OF_ROWS = 1000;
    // totalCount/totalSeen 계산이 어떤 이유로든 어긋나도 무한루프에 빠지지 않도록 하는 안전장치.
    private static final int MAX_PAGES = 50;

    @Override
    public void run(ApplicationArguments args) {
        log.info("DisasterAlertMissingDateBackfillTool 시작: fromDate={}", fromDate);

        int pageNo = 1;
        int totalSaved = 0;
        int totalSeen = 0;

        while (pageNo <= MAX_PAGES) {
            String raw = disasterOpenApiClient.fetchData(fromDate, pageNo, NUM_OF_ROWS);
            if (raw == null || raw.isBlank()) {
                log.warn("page {} 응답 없음, 중단", pageNo);
                break;
            }

            // 엄격한 DTO 바인딩 대신 트리 파싱으로 totalCount/건수만 가볍게 확인한다 — body가
            // 배열이 아니라 {"items":[...]}로 오는 페이지도 있어(실제 저장은 saveData 내부의
            // parseBodyElements가 두 형태 다 처리한다), 여기서도 둘 다 건수를 셀 수 있어야 한다.
            int bodySize;
            int totalCount;
            try {
                JsonNode root = objectMapper.readTree(raw);
                JsonNode body = root.path("body");
                if (!body.isArray() && body.path("items").isArray()) {
                    body = body.path("items");
                }
                bodySize = body.isArray() ? body.size() : 0;
                totalCount = root.path("totalCount").asInt(0);
            } catch (Exception e) {
                log.error("page {} 응답 파싱 실패, 중단", pageNo, e);
                break;
            }

            if (bodySize == 0) {
                log.info("page {} 응답 0건, 종료", pageNo);
                break;
            }

            List<Long> savedIds = disasterAlertService.saveData(raw);
            totalSaved += savedIds.size();
            totalSeen += bodySize;
            log.info("page {} 처리: 응답 {}건 중 신규 저장 {}건 (누적 조회 {}/{}, 누적 저장 {})",
                    pageNo, bodySize, savedIds.size(), totalSeen, totalCount, totalSaved);

            if (totalSeen >= totalCount) {
                break;
            }
            pageNo++;
        }

        log.info("DisasterAlertMissingDateBackfillTool 완료: 조회 {}건 중 신규 저장 {}건", totalSeen, totalSaved);
    }
}
