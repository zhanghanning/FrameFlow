package com.frameflow.common;

public record Api<T>(int code, String msg, T data) {
  public static <T> Api<T> ok(T data) {
    return new Api<>(0, "ok", data);
  }
}
