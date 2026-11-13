package com.frameflow.common;

import java.util.*;

public record Actor(
    long id, String username, String displayName, List<String> roles, Long creatorId) {
  private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

  public static Actor current() {
    return CURRENT.get();
  }

  public static void set(Actor actor) {
    CURRENT.set(actor);
  }

  public static void clear() {
    CURRENT.remove();
  }

  public boolean has(String... allowed) {
    return roles.stream().anyMatch(Arrays.asList(allowed)::contains);
  }

  public boolean creatorOnly() {
    return has("CREATOR") && !has("ADMIN", "PRODUCER", "FINANCE", "REVIEWER");
  }

  public void require(String... allowed) {
    if (!has(allowed)) throw new BusinessException(403, "当前角色没有此操作权限");
  }
}
