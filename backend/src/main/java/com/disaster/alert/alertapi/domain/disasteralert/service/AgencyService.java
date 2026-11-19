package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.Agency;
import com.disaster.alert.alertapi.domain.disasteralert.model.AgencyAlias;
import com.disaster.alert.alertapi.domain.disasteralert.model.SenderLabel;
import com.disaster.alert.alertapi.domain.disasteralert.repository.AgencyAliasRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.AgencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 재난문자 본문의 발신 기관 표기를 기관으로 해석한다(specs/007 R11).
 *
 * <p>수집 스케줄러가 새 알림을 저장하기 직전({@code DisasterAlertService#saveData})에 배치당 한 번 부른다.
 * 표기 → 기관 연결은 {@code agency_alias} 에 남고, 처음 보는 표기는 기관과 함께 자동 등록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyService {

    /** agency.name / agency_alias.label 컬럼 길이(V140). 넘으면 INSERT 가 실패해 호출한 트랜잭션이 롤백 전용이 된다. */
    private static final int LABEL_MAX_LENGTH = 100;

    private final AgencyRepository agencyRepository;
    private final AgencyAliasRepository agencyAliasRepository;

    /**
     * 본문 목록에서 표기를 뽑아 표기 → 기관 Map 으로 돌려준다(배치 1회 호출).
     * 표기가 없는(또는 컬럼 길이를 넘는) 본문은 결과에 항목이 없다 — 호출한 쪽이 그 알림의 기관을 null 로 둔다.
     * 호출한 트랜잭션에 합류하므로, 저장된 기관·표기는 그 트랜잭션이 커밋될 때 함께 확정된다.
     */
    @Transactional
    public Map<String, Agency> resolveByMessages(Collection<String> messages) {
        // 1) 표기만 중복 없이 모은다 — 같은 표기가 여러 본문에 나와도 기관은 하나여야 한다
        Set<String> labels = collectLabels(messages);
        Map<String, Agency> resolved = new HashMap<>();
        if (labels.isEmpty()) {
            return resolved;
        }

        // 2) 이미 등록된 표기는 한 번의 조회로 기존 기관에 연결한다(표기마다 조회하면 배치 크기만큼 쿼리가 나간다)
        for (AgencyAlias alias : agencyAliasRepository.findAllById(labels)) {
            resolved.put(alias.getLabel(), alias.getAgency());
        }

        // 3) 남은 표기는 처음 보는 것 — 표기 그대로를 이름으로 기관을 만들고 표기를 연결한다(정규화는 나중 일)
        for (String label : labels) {
            if (!resolved.containsKey(label)) {
                Agency created = agencyRepository.save(Agency.named(label));
                agencyAliasRepository.save(AgencyAlias.of(label, created));
                resolved.put(label, created);
            }
        }
        return resolved;
    }

    private Set<String> collectLabels(Collection<String> messages) {
        Set<String> labels = new LinkedHashSet<>();
        for (String message : messages) {
            if (message == null) {
                continue;
            }
            SenderLabel.extract(message).ifPresent(label -> {
                if (label.length() > LABEL_MAX_LENGTH) {
                    // 실측 최대 23자라 정상 흐름에서는 나오지 않는다. 그대로 넣으면 DB 에서 실패해 수집 트랜잭션까지 못 쓰게 되므로 건너뛴다
                    // 앞 20자만 남긴다 — 나중에 백필 대상을 찾을 단서면 충분하고, 본문 전체는 로그에 싣지 않는다
                    log.warn("발신 기관 표기가 {}자를 넘어 기관 지정을 건너뜁니다: {}자, 앞 20자 '{}'",
                            LABEL_MAX_LENGTH, label.length(), label.substring(0, 20));
                    return;
                }
                labels.add(label);
            });
        }
        return labels;
    }
}
