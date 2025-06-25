package com.disaster.alert.alertapi.domain.disasteralert.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DisasterApiResponse {
    private Header header;
    private Body body;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Header {
        private String resultMsg;
        private String resultCode;
        private String errorMsg;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Body {
        private int numOfRows;
        private int pageNo;
        private int totalCount;
        private List<DisasterAlertDto> items;
    }
}

