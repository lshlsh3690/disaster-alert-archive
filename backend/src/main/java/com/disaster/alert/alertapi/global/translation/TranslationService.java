package com.disaster.alert.alertapi.global.translation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlertTranslation;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertTranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private static final String LANG_EN = "EN";

    private final DisasterAlertRepository disasterAlertRepository;
    private final DisasterAlertTranslationRepository translationRepository;
    private final DeepLTranslationClient deepLClient;
    private final TranslationProperties properties;

    @Async("translationExecutor")
    @Transactional
    public void translateAndSaveAsync(Long alertId) {
        if (!properties.isEnabled()) {
            return;
        }

        if (translationRepository.findByIdAlertIdAndIdLanguageCode(alertId, LANG_EN).isPresent()) {
            return;
        }

        try {
            var alert = disasterAlertRepository.findById(alertId).orElse(null);
            if (alert == null || alert.getMessage() == null || alert.getMessage().isBlank()) {
                return;
            }

            String translatedMessage = deepLClient.translate(alert.getMessage(), LANG_EN);

            String translatedType = null;
            if (alert.getDisasterType() != null && !alert.getDisasterType().isBlank()) {
                try {
                    translatedType = deepLClient.translate(alert.getDisasterType(), LANG_EN);
                } catch (Exception e) {
                    log.warn("유형 번역 실패: alertId={}, reason={}", alertId, e.getMessage());
                }
            }

            String translatedRegion = null;
            List<String> regionNames = disasterAlertRepository.legalDistrictNamesByAlertId(alertId);
            if (!regionNames.isEmpty()) {
                try {
                    translatedRegion = deepLClient.translate(String.join(", ", regionNames), LANG_EN);
                } catch (Exception e) {
                    log.warn("지역 번역 실패: alertId={}, reason={}", alertId, e.getMessage());
                }
            }

            translationRepository.save(
                    DisasterAlertTranslation.of(alertId, LANG_EN, translatedMessage, translatedType, translatedRegion)
            );
            log.info("번역 완료: alertId={}", alertId);

        } catch (Exception e) {
            log.warn("번역 실패: alertId={}, reason={}", alertId, e.getMessage());
        }
    }
}
