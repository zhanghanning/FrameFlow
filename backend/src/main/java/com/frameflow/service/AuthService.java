package com.frameflow.service;

import static com.frameflow.common.Values.*;

import com.frameflow.common.*;
import com.frameflow.dto.Requests;
import com.frameflow.mapper.AuthMapper;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements ApplicationRunner {
  private final AuthMapper mapper;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();

  public AuthService(AuthMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (var row : mapper.demoUsers())
      mapper.password(id(row.get("id")), encoder.encode("FrameFlow2026!"));
  }

  public Actor actor(Map<String, Object> row) {
    return new Actor(
        id(row.get("id")),
        (String) row.get("username"),
        (String) row.get("displayName"),
        mapper.roles(id(row.get("id"))),
        row.get("creatorId") == null ? null : id(row.get("creatorId")));
  }

  public Actor authenticate(String token) {
    var row = mapper.session(token);
    if (row == null) throw new BusinessException(401, "登录已过期，请重新登录");
    return actor(row);
  }

  @Transactional
  public Map<String, Object> register(Requests.Registration request) {
    if (!request.password().equals(request.confirmPassword()))
      throw BusinessException.bad("两次输入的密码不一致");
    if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw BusinessException.bad("密码过长，请缩短后重试");
    var data = new HashMap<String, Object>();
    data.put("username", request.username());
    data.put("displayName", request.displayName().trim());
    data.put("creatorId", null);
    data.put("passwordDigest", encoder.encode(request.password()));
    try {
      mapper.insertUser(data);
    } catch (DuplicateKeyException ex) {
      throw BusinessException.conflict("该用户名已注册，请更换用户名或直接登录");
    }
    long userId = id(data.get("id"));
    long creatorId = createPersonalCreator(userId, (String) data.get("displayName"));
    mapper.addRole(userId, "CREATOR");
    mapper.audit(userId, "REGISTER", "USER", userId, "自主注册创作者，创建并绑定个人档案");
    return Map.of("id", userId, "username", request.username(), "displayName", data.get("displayName"), "creatorId", creatorId);
  }

  @Transactional
  public Map<String, Object> login(Requests.Login request) {
    var row = mapper.userByUsername(request.username());
    if (row == null
        || !"ACTIVE".equals(row.get("status"))
        || !encoder.matches(request.password(), (String) row.get("passwordDigest")))
      throw new BusinessException(401, "用户名或密码错误");
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    mapper.newSession(token, id(row.get("id")));
    mapper.audit(id(row.get("id")), "LOGIN", "USER", id(row.get("id")), "用户登录");
    return Map.of("token", token, "user", actor(row));
  }

  public void logout(String token) {
    mapper.logout(token);
  }

  @Transactional
  public void changePassword(Requests.Password request, String token) {
    var row = mapper.userById(Actor.current().id());
    if (!encoder.matches(request.oldPassword(), (String) row.get("passwordDigest")))
      throw BusinessException.bad("原密码不正确");
    mapper.password(Actor.current().id(), encoder.encode(request.newPassword()));
    mapper.invalidateOthers(Actor.current().id(), token);
    audit("CHANGE_PASSWORD", "USER", Actor.current().id(), "修改登录密码");
  }

  public Map<String, Object> users(Map<String, String> input) {
    Actor.current().require("ADMIN");
    int page;
    int pageSize;
    try {
      page = Integer.parseInt(input.getOrDefault("page", "1"));
      pageSize = Integer.parseInt(input.getOrDefault("pageSize", "20"));
    } catch (NumberFormatException ex) {
      throw BusinessException.bad("页码和每页数量须为正整数");
    }
    if (page < 1 || pageSize < 1) throw BusinessException.bad("页码和每页数量须为正整数");
    pageSize = Math.min(pageSize, 100);
    var query = new HashMap<String, Object>();
    String search = input.get("q");
    String status = input.get("status");
    String assignment = input.get("assignment");
    if (search != null && !search.isBlank()) query.put("q", search.trim());
    if (status != null && !status.isBlank()) {
      oneOf(status, "ACTIVE", "INACTIVE");
      query.put("status", status);
    }
    if (assignment != null && !assignment.isBlank()) {
      oneOf(assignment, "PENDING", "ASSIGNED");
      query.put("assignment", assignment);
    }
    query.put("limit", pageSize);
    query.put("offset", (long) (page - 1) * pageSize);
    long total = mapper.countUsers(query);
    var rows = mapper.users(query);
    rows.forEach(row -> row.put("roles", mapper.roles(id(row.get("id")))));
    return Map.of("items", rows, "total", total, "page", page, "pageSize", pageSize);
  }

  @Transactional
  public long createUser(Requests.NewUser request) {
    Actor.current().require("ADMIN");
    validateRoles(request.roles(), request.creatorId());
    var data = new HashMap<String, Object>();
    data.put("username", request.username());
    data.put("displayName", request.displayName());
    data.put("creatorId", request.creatorId());
    data.put("passwordDigest", encoder.encode(request.password()));
    mapper.insertUser(data);
    long userId = id(data.get("id"));
    for (String role : request.roles()) mapper.addRole(userId, role);
    audit("CREATE", "USER", userId, "新增用户 " + request.username());
    return userId;
  }

  @Transactional
  public void updateUser(long userId, Requests.EditUser request) {
    Actor.current().require("ADMIN");
    var existing = required(mapper.lockUser(userId), "用户");
    oneOf(request.status(), "ACTIVE", "INACTIVE");
    var roles = new ArrayList<>(request.roles());
    if (new HashSet<>(roles).size() != roles.size()) throw BusinessException.bad("角色重复");
    Long creatorId = existing.get("creatorId") == null ? request.creatorId() : id(existing.get("creatorId"));
    if (existing.get("creatorId") != null && request.creatorId() != null && !creatorId.equals(request.creatorId()))
      throw BusinessException.conflict("该账号已有个人创作者档案，请保留原有绑定");
    if (roles.isEmpty()) roles.add("CREATOR");
    if (creatorId != null && !roles.contains("CREATOR")) roles.add("CREATOR");
    if (userId == Actor.current().id()
        && (!"ACTIVE".equals(request.status()) || !roles.contains("ADMIN")))
      throw BusinessException.bad("当前管理员需保留启用状态和管理员角色");
    if (roles.contains("CREATOR") && creatorId == null)
      creatorId = createPersonalCreator(userId, request.displayName());
    validateRoles(roles, creatorId);
    var data = new HashMap<String, Object>();
    data.put("id", userId);
    data.put("displayName", request.displayName());
    data.put("creatorId", creatorId);
    data.put("status", request.status());
    mapper.updateUser(data);
    mapper.deleteRoles(userId);
    for (String role : roles) mapper.addRole(userId, role);
    audit("UPDATE", "USER", userId, "更新账号岗位与状态，保留个人创作者身份");
  }

  private long createPersonalCreator(long userId, String name) {
    var creator = new HashMap<String, Object>();
    creator.put("name", name);
    creator.put("handle", "member-" + UUID.randomUUID());
    mapper.insertPersonalCreator(creator);
    long creatorId = id(creator.get("id"));
    mapper.bindCreator(userId, creatorId);
    return creatorId;
  }

  public Map<String, Object> roleApplications(Map<String, String> input) {
    String scope = input.getOrDefault("scope", "MINE");
    oneOf(scope, "MINE", "ALL");
    if ("ALL".equals(scope)) Actor.current().require("ADMIN");
    int page;
    int pageSize;
    try {
      page = Integer.parseInt(input.getOrDefault("page", "1"));
      pageSize = Integer.parseInt(input.getOrDefault("pageSize", "20"));
    } catch (NumberFormatException ex) {
      throw BusinessException.bad("页码和每页数量须为正整数");
    }
    if (page < 1 || pageSize < 1) throw BusinessException.bad("页码和每页数量须为正整数");
    pageSize = Math.min(pageSize, 100);
    var query = new HashMap<String, Object>();
    if ("MINE".equals(scope)) query.put("userId", Actor.current().id());
    String search = input.get("q");
    String state = input.get("status");
    if (search != null && !search.isBlank()) query.put("q", search.trim());
    if (state != null && !state.isBlank()) {
      oneOf(state, "PENDING", "APPROVED", "REJECTED");
      query.put("status", state);
    }
    query.put("limit", pageSize);
    query.put("offset", (long) (page - 1) * pageSize);
    return Map.of("items", mapper.roleApplications(query), "total", mapper.countRoleApplications(query),
        "page", page, "pageSize", pageSize);
  }

  @Transactional
  public long applyRole(Requests.RoleApplication request) {
    oneOf(request.requestedRole(), "ADMIN", "PRODUCER", "FINANCE", "REVIEWER");
    long userId = Actor.current().id();
    required(mapper.lockUser(userId), "用户");
    if (mapper.roles(userId).contains(request.requestedRole()))
      throw BusinessException.conflict("你已拥有该岗位，无需重复申请");
    var application = new HashMap<String, Object>();
    application.put("userId", userId);
    application.put("requestedRole", request.requestedRole());
    application.put("reason", request.reason().trim());
    try {
      mapper.insertRoleApplication(application);
    } catch (DuplicateKeyException ex) {
      throw BusinessException.conflict("该岗位已有待审批申请，请等待管理员处理");
    }
    long applicationId = id(application.get("id"));
    audit("APPLY_ROLE", "ROLE_APPLICATION", applicationId, "申请附加岗位：" + roleName(request.requestedRole()));
    return applicationId;
  }

  @Transactional
  public void approveRole(long applicationId, String note) {
    reviewRole(applicationId, "APPROVED", note);
  }

  @Transactional
  public void rejectRole(long applicationId, String note) {
    reviewRole(applicationId, "REJECTED", note.trim());
  }

  private void reviewRole(long applicationId, String decision, String note) {
    Actor.current().require("ADMIN");
    var application = required(mapper.lockRoleApplication(applicationId), "岗位申请");
    status(application, "PENDING");
    long userId = id(application.get("userId"));
    if (userId == Actor.current().id()) throw BusinessException.conflict("请由其他管理员审批自己的岗位申请");
    var applicant = required(mapper.lockUser(userId), "申请人");
    String role = (String) application.get("requestedRole");
    if ("APPROVED".equals(decision)) {
      if (!"ACTIVE".equals(applicant.get("status"))) throw BusinessException.conflict("申请人账号已停用，请先恢复账号再批准岗位");
      if (!mapper.roles(userId).contains(role)) mapper.addRole(userId, role);
    }
    var review = new HashMap<String, Object>();
    review.put("id", applicationId);
    review.put("status", decision);
    review.put("reviewNote", note == null ? null : note.trim());
    review.put("reviewedBy", Actor.current().id());
    mapper.reviewRoleApplication(review);
    audit("APPROVED".equals(decision) ? "APPROVE_ROLE" : "REJECT_ROLE", "ROLE_APPLICATION", applicationId,
        ("APPROVED".equals(decision) ? "批准" : "驳回") + "附加岗位申请：" + roleName(role));
  }

  private String roleName(String role) {
    return switch (role) {
      case "ADMIN" -> "管理员";
      case "PRODUCER" -> "项目统筹";
      case "FINANCE" -> "财务";
      case "REVIEWER" -> "审核";
      default -> "创作者";
    };
  }

  private void validateRoles(List<String> roles, Long creatorId) {
    if (new HashSet<>(roles).size() != roles.size()) throw BusinessException.bad("角色重复");
    for (String role : roles) oneOf(role, "ADMIN", "PRODUCER", "FINANCE", "REVIEWER", "CREATOR");
    if (creatorId != null && !mapper.creatorExists(creatorId))
      throw BusinessException.bad("关联的创作者不存在，请重新选择");
    if (roles.contains("CREATOR") && creatorId == null) throw BusinessException.bad("创作者账号需要绑定创作者");
  }

  public void audit(String action, String entity, Long id, String detail) {
    mapper.audit(Actor.current().id(), action, entity, id, detail);
  }
}
