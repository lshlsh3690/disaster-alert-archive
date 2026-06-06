package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertTranslationRepository;
import com.disaster.alert.alertapi.domain.event.dto.EventAlertItem;
import com.disaster.alert.alertapi.domain.event.dto.EventDetailResponse;
import com.disaster.alert.alertapi.domain.event.dto.EventListResponse;
import com.disaster.alert.alertapi.domain.event.dto.EventSearchRequest;
import com.disaster.alert.alertapi.domain.event.dto.EventTimelineRow;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventTranslationRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import com.disaster.alert.alertapi.domain.legaldistrict.service.LegalDistrictTranslationService;
import com.disaster.alert.alertapi.global.translation.SupportedLanguage;
import com.disaster.alert.alertapi.global.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 이벤트 조회 서비스.
 *
 * <p>{@code active}(진행중/지난사건)는 저장값이 아니라 조회 시점 KST 기준 파생값.
 * <p>{@code lang} 지정 시 제목은 {@link EventTranslationService}(DeepL), 상세 타임라인의 알림은
 * 기존 alert 번역({@link TranslationService} + 법정동 번역)을 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class EventQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DisasterEventRepository disasterEventRepository;
    private final EventAlertMappingRepository eventAlertMappingRepository;
    private final DisasterAlertRepository disasterAlertRepository;

    // 번역
    private final EventTranslationService eventTranslationService;
    private final DisasterEventTranslationRepository eventTranslationRepository;
    private final TranslationService alertTranslationService;
    private final DisasterAlertTranslationRepository alertTranslationRepository;
    private final LegalDistrictTranslationService legalDistrictTranslationService;

    /**
     * 이벤트 목록 (유형/지역/기간/키워드 + active 필터). 정렬은 last_alert_at DESC 고정.
     *
     * @param req  검색 조건 ({@link EventSearchRequest}). 모든 필드 선택적(null=조건 스킵)
     * @param lang ko(기본)/en/ja/zh — 제목 번역
     */
    @Transactional
    public Page<EventListResponse> list(EventSearchRequest req, Pageable pageable, String lang) {
        LocalDateTime now = LocalDateTime.now(KST);
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        // 기간 경계: start=그날 00:00, end=그날 23:59:59.999999999 (alerts dateGoe/dateLoe 관례)
        LocalDateTime start = req.getStartDate() == null ? null : req.getStartDate().atStartOfDay();
        LocalDateTime end = req.getEndDate() == null ? null : req.getEndDate().atTime(LocalTime.MAX);

        Page<DisasterEvent> page = disasterEventRepository.search(
                req.getActive(), now,
                req.getType(), req.getRegion(), req.getDistrictCode(),
                start, end, req.getKeyword(),
                pageOnly);

        Optional<SupportedLanguage> langOpt = SupportedLanguage.fromRequestParam(lang);
        if (langOpt.isEmpty()) {
            return page.map(e -> EventListResponse.of(e, now));
        }

        SupportedLanguage language = langOpt.get();
        List<Long> eventIds = page.getContent().stream().map(DisasterEvent::getId).toList();
        eventTranslationService.ensureTranslatedBatch(eventIds, language);
        Map<Long, String> titleMap = eventTranslationRepository
                .findByIdEventIdInAndIdLanguageCode(eventIds, language.getDbCode())
                .stream()
                .collect(Collectors.toMap(t -> t.getId().getEventId(), t -> t.getTranslatedTitle()));

        return page.map(e -> EventListResponse.of(e, now, titleMap.get(e.getId()), language.getCode()));
    }

    /**
     * 이벤트 상세 + 타임라인.
     *
     * @param lang ko(기본)/en/ja/zh — 제목 + 타임라인 알림 번역
     */
    @Transactional
    public EventDetailResponse detail(Long id, String lang) {
        LocalDateTime now = LocalDateTime.now(KST);
        DisasterEvent event = disasterEventRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.EVENT_NOT_FOUND, "id=" + id));

        List<EventTimelineRow> rows = eventAlertMappingRepository.findTimelineRows(id);
        Map<Long, List<String>> regionNamesByAlert = loadRegionNames(rows);

        Optional<SupportedLanguage> langOpt = SupportedLanguage.fromRequestParam(lang);
        if (langOpt.isEmpty()) {
            List<EventAlertItem> timeline = buildTimeline(rows, regionNamesByAlert, null, null, null, null);
            return EventDetailResponse.of(event, now, timeline);
        }

        SupportedLanguage language = langOpt.get();

        // 제목 번역
        eventTranslationService.ensureTranslated(id, language);
        String translatedTitle = eventTranslationRepository
                .findByIdEventIdAndIdLanguageCode(id, language.getDbCode())
                .map(t -> t.getTranslatedTitle())
                .orElse(null);

        // 타임라인 알림 번역 — 기존 alert 번역 재사용
        List<Long> alertIds = rows.stream().map(EventTimelineRow::alertId).toList();
        alertTranslationService.ensureTranslatedBatch(alertIds, language);
        Map<Long, DisasterAlertTranslation> alertTrMap = alertTranslationRepository
                .findByIdAlertIdInAndIdLanguageCode(alertIds, language.getDbCode())
                .stream()
                .collect(Collectors.toMap(t -> t.getId().getAlertId(), t -> t));

        Map<Long, List<String>> alertIdToCodes = loadRegionCodes(alertIds);
        List<String> allCodes = alertIdToCodes.values().stream().flatMap(List::stream).distinct().toList();
        Map<String, String> codeToTranslated = legalDistrictTranslationService
                .getTranslatedNamesWithEnglishFallback(allCodes, language.getDbCode());

        List<EventAlertItem> timeline = buildTimeline(
                rows, regionNamesByAlert, alertTrMap, alertIdToCodes, codeToTranslated, language.getCode());
        return EventDetailResponse.of(event, now, timeline, translatedTitle, language.getCode());
    }

    /**
     * 타임라인 항목 빌드. 번역 맵이 null 이면 한국어만(translated*=null).
     */
    private List<EventAlertItem> buildTimeline(
            List<EventTimelineRow> rows,
            Map<Long, List<String>> regionNamesByAlert,
            Map<Long, DisasterAlertTranslation> alertTrMap,
            Map<Long, List<String>> alertIdToCodes,
            Map<String, String> codeToTranslated,
            String languageCode) {

        boolean translate = alertTrMap != null;
        List<EventAlertItem> timeline = new ArrayList<>(rows.size());
        for (EventTimelineRow r : rows) {
            String translatedMessage = null;
            String translatedType = null;
            List<String> translatedRegionNames = null;
            String language = languageCode;

            if (translate) {
                DisasterAlertTranslation t = alertTrMap.get(r.alertId());
                if (t != null) {
                    translatedMessage = t.getTranslatedMessage();
                    translatedType = t.getTranslatedDisasterType();
                }
                List<String> codes = alertIdToCodes.getOrDefault(r.alertId(), List.of());
                translatedRegionNames = codes.stream()
                        .map(c -> codeToTranslated.getOrDefault(c, c))
                        .toList();
            }

            timeline.add(new EventAlertItem(
                    r.alertId(),
                    r.sequenceNo(),
                    r.message(),
                    r.disasterType(),
                    r.emergencyLevel() == null ? null : r.emergencyLevel().getDescription(),
                    r.createdAt(),
                    regionNamesByAlert.getOrDefault(r.alertId(), List.of()),
                    translatedMessage,
                    translatedType,
                    translatedRegionNames,
                    null
            ));
        }
        return timeline;
    }

    /** 타임라인 알림들의 지역명(한국어)을 batch 로 조회해 alertId → names 로 묶음. */
    private Map<Long, List<String>> loadRegionNames(List<EventTimelineRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> alertIds = rows.stream().map(EventTimelineRow::alertId).toList();
        Map<Long, List<String>> map = new HashMap<>();
        for (Object[] pair : disasterAlertRepository.findAlertIdAndRegionNamePairs(alertIds)) {
            Long alertId = (Long) pair[0];
            String name = (String) pair[1];
            map.computeIfAbsent(alertId, k -> new ArrayList<>()).add(name);
        }
        return map;
    }

    /** 타임라인 알림들의 법정동 코드를 batch 로 조회 (지역명 번역용). */
    private Map<Long, List<String>> loadRegionCodes(List<Long> alertIds) {
        if (alertIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> map = new HashMap<>();
        for (Object[] pair : disasterAlertRepository.findAlertIdAndCodePairs(alertIds)) {
            Long alertId = (Long) pair[0];
            String code = (String) pair[1];
            map.computeIfAbsent(alertId, k -> new ArrayList<>()).add(code);
        }
        return map;
    }
}
