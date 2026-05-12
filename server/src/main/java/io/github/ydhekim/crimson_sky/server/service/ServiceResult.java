package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.MessageCode;

public record ServiceResult<T>(
    boolean success,
    MessageCode code,
    T data
) {
    public static <T> ServiceResult<T> success(MessageCode code, T data) {
        return new ServiceResult<>(true, code, data);
    }

    public static ServiceResult<Void> success(MessageCode code) {
        return new ServiceResult<>(true, code, null);
    }

    public static <T> ServiceResult<T> failure(MessageCode code) {
        return new ServiceResult<>(false, code, null);
    }
}
