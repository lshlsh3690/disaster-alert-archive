package com.disaster.alert.alertapi.global.logtrace;

import java.util.UUID;

// 한 요청(스레드) 안에서 호출 깊이가 깊어질 때마다 level+1로 파생되는 트랜잭션 ID.
// id는 그대로 유지되므로 로그를 트랜잭션 단위로 grep 가능하고, level은 들여쓰기 폭을 결정한다.
public class TraceId {

    private final String id;
    private final int level;

    public TraceId() {
        this.id = createId();
        this.level = 0;
    }

    private TraceId(String id, int level) {
        this.id = id;
        this.level = level;
    }

    private String createId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public TraceId createNextId() {
        return new TraceId(id, level + 1);
    }

    public TraceId createPreviousId() {
        return new TraceId(id, level - 1);
    }

    public boolean isFirstLevel() {
        return level == 0;
    }

    public String getId() {
        return id;
    }

    public int getLevel() {
        return level;
    }
}
