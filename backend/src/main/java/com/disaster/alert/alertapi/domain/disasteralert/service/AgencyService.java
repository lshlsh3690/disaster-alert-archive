package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.Agency;
import com.disaster.alert.alertapi.domain.disasteralert.repository.AgencyAliasRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.AgencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * 재난문자 본문의 발신 기관 표기를 기관으로 해석한다(specs/007 R11).
 *
 * <p>Red 단계 뼈대 — 구현은 Green 에서.
 */
@Service
@RequiredArgsConstructor
public class AgencyService {

    private final AgencyRepository agencyRepository;
    private final AgencyAliasRepository agencyAliasRepository;

    /**
     * 수집 배치의 본문 목록에서 표기를 뽑아 표기 → 기관 Map 으로 돌려준다(배치 1회 호출).
     * 표기가 없는 본문은 결과에 항목이 생기지 않는다.
     */
    public Map<String, Agency> resolveByMessages(Collection<String> messages) {
        // Red 단계 뼈대 — 구현은 Green 에서
        return Map.of();
    }
}
