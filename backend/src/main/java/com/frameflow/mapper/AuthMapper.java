package com.frameflow.mapper;

import java.util.*;
import org.apache.ibatis.annotations.Param;

public interface AuthMapper {
  Map<String, Object> userByUsername(String username);

  Map<String, Object> userById(long id);

  boolean creatorExists(long creatorId);

  Map<String, Object> lockUser(long id);

  void insertPersonalCreator(Map<String, Object> creator);

  void bindCreator(@Param("userId") long userId, @Param("creatorId") long creatorId);

  List<Map<String, Object>> roleApplications(Map<String, Object> query);

  long countRoleApplications(Map<String, Object> query);

  void insertRoleApplication(Map<String, Object> application);

  Map<String, Object> lockRoleApplication(long id);

  void reviewRoleApplication(Map<String, Object> review);

  Map<String, Object> session(String token);

  List<String> roles(long userId);

  List<Map<String, Object>> users(Map<String, Object> query);

  long countUsers(Map<String, Object> query);

  List<Map<String, Object>> demoUsers();

  void password(@Param("id") long id, @Param("digest") String digest);

  void newSession(@Param("token") String token, @Param("userId") long userId);

  void logout(String token);

  void invalidateOthers(@Param("userId") long userId, @Param("token") String token);

  void insertUser(Map<String, Object> data);

  void updateUser(Map<String, Object> data);

  void deleteRoles(long id);

  void addRole(@Param("id") long id, @Param("role") String role);

  void audit(
      @Param("userId") long userId,
      @Param("action") String action,
      @Param("entityType") String entityType,
      @Param("entityId") Long entityId,
      @Param("detail") String detail);
}
