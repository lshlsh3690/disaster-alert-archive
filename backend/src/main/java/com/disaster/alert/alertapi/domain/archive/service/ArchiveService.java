package com.disaster.alert.alertapi.domain.archive.service;

import com.disaster.alert.alertapi.domain.archive.dto.ArchiveEventResolveResponse;
import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSnLookup;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사건 영구 링크 — 사건을 바뀌지 않는 주소로 가리키게 하는 조회 전용 서비스.
 *
 * <p>사건 번호는 재클러스터링 백필 때 지우고 1부터 다시 매기므로 링크에 쓸 수 없다. 대신 원 출처 고유번호(sn)로 열고,
 * 그 알림이 지금 속한 사건으로 해석한다. 뉴스 기사를 사건에 연결할 때도 이 키를 쓴다(로드맵).
 * 2026-11-19 "기록관" 화면(연도별 주요 사건·원문 기록·인용문)은 폐지했고 이 해석만 남겼다(specs/007).
 * 리포지토리를 직접 쓰지 않고 맥락별 조회 서비스를 거친다(헌법 VII).
 */
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final DisasterAlertQueryService disasterAlertQueryService;

    /**
     * sn 을 현재 사건으로 해석한다. 사건에 묶였으면 bound=true + 사건 ID + 대표 sn, 아니면 bound=false + 알림 요약.
     *
     * @throws CustomException 수집된 적 없는 sn (DISASTER_ALERT_NOT_FOUND, 4xx 라 warn 이하로만 기록된다)
     */
    public ArchiveEventResolveResponse resolveEvent(Long sn) {
        // 1) 없는 sn 은 404 — 링크가 잘못됐거나 수집된 적 없는 문자
        AlertSnLookup alert = disasterAlertQueryService.findBySn(sn)
                .orElseThrow(() -> new CustomException(ErrorCode.DISASTER_ALERT_NOT_FOUND));

        // 2) 사건에 안 묶인 알림은 오류가 아니라 정상 상태(비사건 분류 알림은 원래 묶이지 않는다) — 원문 요약만 준다
        if (alert.eventId() == null) {
            return new ArchiveEventResolveResponse(sn, false, null, null, new ArchiveEventResolveResponse.Alert(
                    alert.alertId(), alert.sn(), alert.message(), alert.createdAt(),
                    alert.disasterType(), alert.emergencyLevel()));
        }

        // 3) 묶였으면 그 알림이 대표가 아니어도(사건이 합쳐진 경우) 지금 속한 사건으로 해석한다.
        //    화면에 보일 영구 링크는 항상 현재 대표 sn 으로 만든다. 대표 sn 은 알림과 같은 조회 문장에서 읽어 왔으므로
        //    백필 도중이라도 eventId 와 서로 다른 시점의 값이 섞이지 않는다
        return new ArchiveEventResolveResponse(sn, true, alert.eventId(), alert.representativeSn(), null);
    }
}
