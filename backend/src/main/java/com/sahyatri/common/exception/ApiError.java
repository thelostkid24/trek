package com.sahyatri.common.exception;

import java.util.Map;

/** Error body returned by every failing endpoint: {code, message, details}. */
public record ApiError(String code, String message, Map<String, Object> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }
}
