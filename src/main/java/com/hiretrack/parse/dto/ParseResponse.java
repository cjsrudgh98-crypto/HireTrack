package com.hiretrack.parse.dto;

import java.util.List;

import com.hiretrack.parse.ParseSource;
import com.hiretrack.parse.ParsedPosting;

/**
 * 추출 결과. 저장하지 않고 폼을 채우는 데만 쓴다.
 *
 * @param missingFields 찾지 못한 항목 — 프론트에서 "직접 입력 필요"로 강조
 * @param message       실패 사유 등 사용자 안내 문구 (성공 시 null)
 */
public record ParseResponse(
        boolean success,
        ParseSource source,
        ParsedPosting data,
        List<String> missingFields,
        String message
) {

    public static ParseResponse success(ParseSource source, ParsedPosting data) {
        return new ParseResponse(true, source, data, data.missingFields(), null);
    }

    public static ParseResponse failure(String message) {
        return new ParseResponse(false, ParseSource.NONE, ParsedPosting.EMPTY,
                ParsedPosting.EMPTY.missingFields(), message);
    }
}
