package com.disaster.alert.alertapi.global.util;

import org.springframework.boot.ApplicationArguments;

import java.util.List;

/** 수동 실행 도구(ApplicationRunner)의 {@code --이름=값} 옵션 읽기. 평가 도구들이 같은 규칙으로 읽도록 한 곳에 둔다. */
public final class ToolOptions {

    private ToolOptions() {
    }

    /** 같은 옵션을 여러 번 주거나 쉼표로 이어도 하나의 목록으로 읽는다. 없으면 빈 목록, 빈 항목은 뺀다. */
    public static List<String> optionList(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null) {
            return List.of();
        }
        return values.stream().flatMap(v -> List.of(v.split(",")).stream())
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
