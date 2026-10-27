package com.disaster.alert.alertapi.global.translation;

import java.util.concurrent.Callable;

/**
 * 같은 키로 동시에 들어온 호출을 하나로 묶어(single-flight) 중복 실행을 막는 범용 유틸리티.
 *
 * <p>번역 캐시 미스가 동시에 여러 요청에서 발생하면({@code TranslationService.ensureTranslated})
 * 저장 충돌(PK 위반)은 upsert(ON CONFLICT DO NOTHING)로 이미 막혀 있지만, 외부 번역 API 자체가
 * 같은 (alertId, languageCode) 키로 여러 번 불리는 낭비는 막지 못한다. 이 클래스는 그 낭비를
 * 막기 위해 {@link TranslationService}가 필드로 들고 있는 순수 유틸리티로 설계됐다 — Spring 빈이
 * 아니다 (Spring 빈으로 만들면 싱글턴 생명주기와 결합돼 테스트에서 매번 새 인스턴스로 격리하기
 * 어려워진다).
 *
 * <p><b>Red 단계 뼈대 — 구현은 Green 에서.</b> 지금은 병합 없이 매번 {@code work}를 그냥 실행한다.
 * 그래서 같은 키로 동시에 들어온 두 호출이 각각 work를 실행해 실행 횟수가 2가 되고, 이를
 * 1이어야 한다고 기대하는 테스트가 실패한다.
 */
public class TranslationRequestCoalescer<K, V> {

    /**
     * 같은 key로 동시에 들어온 호출을 병합해 work를 한 번만 실행하고 결과를 공유한다.
     *
     * @param key  병합 기준 키 (예: alertId + languageCode 조합)
     * @param work 실제로 수행할 작업 (예: 번역 API 호출)
     * @return work의 실행 결과
     * @throws Exception work가 던진 예외를 그대로 전파
     */
    public V coalesce(K key, Callable<V> work) throws Exception {
        // Red: 뼈대. 병합 없이 매번 그냥 실행한다 (틀린 기본값 — Green에서 고친다).
        return work.call();
    }
}
