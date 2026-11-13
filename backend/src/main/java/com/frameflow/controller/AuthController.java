package com.frameflow.controller;

import com.frameflow.common.*;
import com.frameflow.dto.Requests;
import com.frameflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/auth/register")
  public Api<?> register(@Valid @RequestBody Requests.Registration request) {
    return Api.ok(service.register(request));
  }

  @PostMapping("/auth/login")
  public Api<?> login(@Valid @RequestBody Requests.Login request) {
    return Api.ok(service.login(request));
  }

  @GetMapping("/auth/me")
  public Api<?> me() {
    return Api.ok(Actor.current());
  }

  @PostMapping("/auth/logout")
  public Api<?> logout(@RequestHeader("Authorization") String token) {
    service.logout(token.substring(7));
    return Api.ok(null);
  }

  @PutMapping("/auth/password")
  public Api<?> password(
      @Valid @RequestBody Requests.Password request, @RequestHeader("Authorization") String token) {
    service.changePassword(request, token.substring(7));
    return Api.ok(null);
  }

  @GetMapping("/role-applications")
  public Api<?> applications(@RequestParam java.util.Map<String, String> query) {
    return Api.ok(service.roleApplications(query));
  }

  @PostMapping("/role-applications")
  public Api<?> apply(@Valid @RequestBody Requests.RoleApplication request) {
    return Api.ok(java.util.Map.of("id", service.applyRole(request)));
  }

  @PostMapping("/role-applications/{id}/approve")
  public Api<?> approve(@PathVariable long id, @Valid @RequestBody(required = false) Requests.RoleApproval request) {
    service.approveRole(id, request == null ? null : request.reviewNote());
    return Api.ok(null);
  }

  @PostMapping("/role-applications/{id}/reject")
  public Api<?> reject(@PathVariable long id, @Valid @RequestBody Requests.RoleRejection request) {
    service.rejectRole(id, request.reviewNote());
    return Api.ok(null);
  }

  @GetMapping("/users")
  public Api<?> users(@RequestParam java.util.Map<String, String> query) {
    return Api.ok(service.users(query));
  }

  @PostMapping("/users")
  public Api<?> create(@Valid @RequestBody Requests.NewUser request) {
    return Api.ok(java.util.Map.of("id", service.createUser(request)));
  }

  @PutMapping("/users/{id}")
  public Api<?> edit(@PathVariable long id, @Valid @RequestBody Requests.EditUser request) {
    service.updateUser(id, request);
    return Api.ok(null);
  }
}
