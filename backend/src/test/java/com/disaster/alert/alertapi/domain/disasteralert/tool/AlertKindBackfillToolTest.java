package com.disaster.alert.alertapi.domain.disasteralert.tool;

import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.service.AlertKindService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 분류 백필은 알림마다 LLM 을 호출해 돈이 든다. 그래서
 * "정한 만큼만, 같은 알림을 두 번 과금하지 않고 쓰는가(limit + 동시 실행)"만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AlertKindBackfillToolTest {

    @Mock
    private DisasterAlertRepository disasterAlertRepository;
    @Mock
    private AlertKindService alertKindService;
    @InjectMocks
    private AlertKindBackfillTool tool;

    @Test
    @DisplayName("동시 4개로 돌려도 limit 만큼만, 앞에서부터, 알림마다 정확히 한 번씩 분류한다")
    void limit_동시실행_정확히_한번씩() {
        List<Long> unclassified = LongStream.rangeClosed(1, 50).boxed().toList();
        when(disasterAlertRepository.findUnclassifiedIds()).thenReturn(unclassified);
        Map<Long, AtomicInteger> calls = new ConcurrentHashMap<>();
        doAnswer(inv -> {
            calls.computeIfAbsent(inv.getArgument(0), k -> new AtomicInteger()).incrementAndGet();
            return null;
        }).when(alertKindService).classifyAndSave(anyLong());

        tool.run(new DefaultApplicationArguments("--alert-kind.limit=30", "--alert-kind.threads=4"));

        // 1~30 번만, 각각 정확히 1번 — 31번 이후는 상한 밖이라 과금하면 안 된다
        assertThat(calls.keySet()).containsExactlyInAnyOrderElementsOf(unclassified.subList(0, 30));
        assertThat(calls.values()).allMatch(c -> c.get() == 1);
    }
}
