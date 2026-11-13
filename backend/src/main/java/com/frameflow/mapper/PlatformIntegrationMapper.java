package com.frameflow.mapper;

import java.util.*;
import org.apache.ibatis.annotations.Param;

public interface PlatformIntegrationMapper {
  List<Map<String, Object>> platforms();
  Map<String, Object> platform(String code);
  Map<String, Object> lockPlatform(long id);
  void insertAttempt(Map<String, Object> data);
  Map<String, Object> attempt(String state);
  int consumeAttempt(@Param("state") String state, @Param("platformId") long platformId);
  Map<String, Object> accountByExternal(@Param("platformId") long platformId, @Param("externalId") String externalId);
  void insertAccount(Map<String, Object> data);
  void updateAccountName(@Param("id") long id, @Param("name") String name);
  void saveConnection(Map<String, Object> data);
  Map<String, Object> connection(long id);
  Map<String, Object> connectionByAccount(long accountId);
  List<Map<String, Object>> connections(Map<String, Object> scope);
  void updateToken(Map<String, Object> data);
  void disconnect(long id);
  void connectionError(@Param("id") long id, @Param("status") String status, @Param("error") String error);
  void synced(long id);
  void insertJob(Map<String, Object> data);
  void finishJob(Map<String, Object> data);
  void upsertContent(Map<String, Object> data);
  List<Map<String, Object>> content(Map<String, Object> query);
  long countContent(Map<String, Object> query);
}
