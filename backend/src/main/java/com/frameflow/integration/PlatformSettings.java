package com.frameflow.integration;

import java.net.URI;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PlatformSettings {
  private final Environment env;
  public PlatformSettings(Environment env) { this.env = env; }
  public String tokenKey() { return env.getProperty("PLATFORM_TOKEN_KEY", "").trim(); }
  public String clientId(String code) { return env.getProperty(code + "_CLIENT_ID", "").trim(); }
  public String clientSecret(String code) { return env.getProperty(code + "_CLIENT_SECRET", "").trim(); }
  public String frontendUrl() { return env.getProperty("PLATFORM_FRONTEND_URL", "http://localhost:3000").replaceAll("/+$", ""); }
  public String redirectUri(String code) {
    String base = env.getProperty("PLATFORM_PUBLIC_BASE_URL", "http://localhost:8080").replaceAll("/+$", "");
    return env.getProperty(code + "_REDIRECT_URI", base + "/api/platform-integrations/" + code + "/callback").trim();
  }
  public boolean clientConfigured(String code) {
    return !clientId(code).isBlank() && !clientSecret(code).isBlank();
  }
  public boolean redirectValid(String code) {
    try {
      URI uri = URI.create(redirectUri(code));
      if ("https".equals(uri.getScheme()) && uri.getHost() != null) return true;
      return "YOUTUBE".equals(code) && "http".equals(uri.getScheme())
          && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
    } catch (IllegalArgumentException ex) { return false; }
  }
}
