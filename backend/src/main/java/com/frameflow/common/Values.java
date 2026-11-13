package com.frameflow.common;

import java.math.BigDecimal;
import java.util.*;

public final class Values {
  private Values() {}

  public static long id(Object value) {
    return ((Number) value).longValue();
  }

  public static BigDecimal money(Object value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  public static Map<String, Object> required(Map<String, Object> value, String noun) {
    if (value == null) throw new BusinessException(404, noun + "不存在");
    return value;
  }

  public static void status(Map<String, Object> row, String expected) {
    if (!expected.equals(row.get("status"))) throw BusinessException.conflict("当前状态不允许此操作");
  }

  public static void oneOf(String value, String... allowed) {
    if (value == null || !Arrays.asList(allowed).contains(value))
      throw BusinessException.bad("状态或类型不正确：" + value);
  }
}
