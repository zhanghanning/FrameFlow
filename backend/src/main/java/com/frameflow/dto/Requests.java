package com.frameflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Requests {
  public record Login(
      @NotBlank(message = "请输入用户名") String username,
      @NotBlank(message = "请输入密码") String password) {}

  public record Registration(
      @NotBlank(message = "请输入用户名")
          @Pattern(regexp = "[A-Za-z0-9_]{4,32}", message = "用户名须为4至32位英文字母、数字或下划线")
          String username,
      @NotBlank(message = "请输入姓名") @Size(max = 80, message = "姓名最多80字") String displayName,
      @NotBlank(message = "请输入密码") @Size(min = 8, max = 72, message = "密码长度为8至72位")
          String password,
      @NotBlank(message = "请再次输入密码") String confirmPassword) {}

  public record Password(
      @NotBlank(message = "请输入原密码") String oldPassword,
      @Size(min = 8, max = 72, message = "新密码长度为8至72位") @NotBlank(message = "请输入新密码")
          String newPassword) {}

  public record Creator(
      @NotBlank(message = "请输入创作者名称") String name,
      String handle,
      @Email(message = "邮箱格式错误") String email,
      String phone,
      @NotBlank(message = "请选择状态") String status,
      String bio) {}

  public record Account(
      @NotNull(message = "请选择平台") Long platformId,
      @NotNull(message = "请选择创作者") Long creatorId,
      @NotBlank(message = "请输入账号名称") String name,
      @NotBlank(message = "请输入平台账号ID") String externalId,
      @NotBlank(message = "请选择状态") String status) {}

  public record Project(
      @NotBlank(message = "请输入项目名称") String name,
      String clientName,
      String description,
      @NotNull(message = "请输入预算") @DecimalMin(value = "0", message = "预算须大于等于0") BigDecimal budget,
      @NotNull(message = "请选择开始日期") LocalDate startDate,
      @NotNull(message = "请选择截止日期") LocalDate dueDate,
      @NotBlank(message = "请选择状态") String status,
      @NotNull(message = "请选择负责人") Long ownerId,
      @NotEmpty(message = "请至少选择一名创作者") List<Long> creatorIds) {}

  public record Work(
      @NotNull(message = "请选择项目") Long projectId,
      @NotBlank(message = "请输入作品标题") String title,
      String description,
      @NotBlank(message = "请选择状态") String status,
      @NotNull(message = "请选择素材") List<Long> assetIds) {}

  public record Publication(
      @NotNull(message = "请选择作品") Long workId,
      @NotNull(message = "请选择平台账号") Long platformAccountId,
      @NotBlank(message = "请输入平台作品ID") String externalId,
      @NotBlank(message = "请选择发布时间") String publishedAt,
      String url) {}

  public record Revenue(
      @NotNull(message = "请选择发布记录") Long publicationId,
      @NotBlank(message = "请输入平台流水号") String sourceRef,
      @NotNull(message = "请选择收入日期") LocalDate revenueDate,
      @NotBlank(message = "请选择流水类型") String kind,
      @NotNull(message = "请输入金额") @DecimalMin(value = "0.01", message = "金额必须大于0")
          BigDecimal grossAmount,
      @NotNull(message = "请输入平台费用") @DecimalMin(value = "0", message = "平台费用不能为负")
          BigDecimal feeAmount,
      Long originalRevenueId,
      String note) {}

  public record Cost(
      @NotNull(message = "请选择项目") Long projectId,
      @NotBlank(message = "请输入成本类别") String category,
      @NotBlank(message = "请输入成本说明") String description,
      @NotNull(message = "请输入成本金额") @DecimalMin(value = "0.01", message = "成本金额必须大于0")
          BigDecimal amount,
      @NotNull(message = "请选择成本日期") LocalDate costDate) {}

  public record Participant(
      @NotNull(message = "请选择创作者") Long creatorId,
      @NotNull(message = "请输入分成比例")
          @DecimalMin(value = "0", inclusive = false, message = "创作者分成比例必须大于0")
          @DecimalMax(value = "100", message = "比例不能超过100")
          BigDecimal rate) {}

  public record Contract(
      @NotNull(message = "请选择项目") Long projectId,
      @NotBlank(message = "请输入合同名称") String name,
      @NotNull(message = "请选择开始日期") LocalDate startDate,
      @NotNull(message = "请选择结束日期") LocalDate endDate,
      @NotNull(message = "请输入工作室比例")
          @DecimalMin(value = "0", message = "比例不能为负")
          @DecimalMax(value = "100", message = "比例不能超过100")
          BigDecimal studioRate,
      @NotEmpty(message = "至少需要一名分成参与者") List<@Valid Participant> participants) {}

  public record Settlement(
      @NotNull(message = "请选择合同") Long contractId,
      @NotNull(message = "请选择结算开始日期") LocalDate periodStart,
      @NotNull(message = "请选择结算结束日期") LocalDate periodEnd) {}

  public record EditSettlement(
      @NotNull(message = "请选择合同") Long contractId,
      @NotNull(message = "请选择结算开始日期") LocalDate periodStart,
      @NotNull(message = "请选择结算结束日期") LocalDate periodEnd,
      @NotBlank(message = "请填写修改原因") @Size(max = 1000, message = "原因最多1000字") String reason) {}

  public record SettlementReason(
      @NotBlank(message = "请填写操作原因") @Size(max = 1000, message = "原因最多1000字") String reason) {}

  public record Reject(@NotBlank(message = "请输入驳回理由") String reason) {}

  public record Payment(
      @NotNull(message = "请选择创作者") Long creatorId,
      @NotNull(message = "请输入付款金额") @DecimalMin(value = "0.01", message = "付款金额必须大于0")
          BigDecimal amount,
      @NotBlank(message = "请选择付款时间") String paidAt,
      @NotBlank(message = "请输入付款凭证号") String reference,
      String note) {}

  public record ImportRows(@NotEmpty(message = "导入内容不能为空") List<Map<String, Object>> rows) {}

  public record RoleApplication(
      @NotBlank(message = "请选择申请岗位") String requestedRole,
      @NotBlank(message = "请填写申请理由") @Size(max = 1000, message = "申请理由最多1000字") String reason) {}

  public record RoleApproval(@Size(max = 1000, message = "审批意见最多1000字") String reviewNote) {}

  public record RoleRejection(
      @NotBlank(message = "请填写驳回理由") @Size(max = 1000, message = "驳回理由最多1000字") String reviewNote) {}

  public record NewUser(
      @NotBlank(message = "请输入用户名") String username,
      @NotBlank(message = "请输入姓名") String displayName,
      @NotBlank(message = "请输入密码") @Size(min = 8, max = 72, message = "密码长度为8至72位") String password,
      @NotEmpty(message = "请选择角色") List<String> roles,
      Long creatorId) {}

  public record EditUser(
      @NotBlank(message = "请输入姓名") String displayName,
      @NotNull(message = "请提交身份设置") List<String> roles,
      Long creatorId,
      @NotBlank(message = "请选择状态") String status) {}
}
