package com.frameflow.config;

import com.frameflow.common.*;
import com.frameflow.service.AuthService;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthService auth;

  public WebConfig(AuthService auth) {
    this.auth = auth;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                Actor.clear();
                String header = request.getHeader("Authorization");
                if (header == null || !header.startsWith("Bearer "))
                  throw new BusinessException(401, "请先登录");
                var actor = auth.authenticate(header.substring(7));
                Actor.set(actor);
                return true;
              }

              @Override
              public void afterCompletion(
                  HttpServletRequest request,
                  HttpServletResponse response,
                  Object handler,
                  Exception ex) {
                Actor.clear();
              }
            })
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login", "/api/auth/register");
  }
}
