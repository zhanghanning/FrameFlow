package com.frameflow.integration;

import com.frameflow.common.BusinessException;

public class PlatformRemoteException extends BusinessException {
  private final boolean expired;
  public PlatformRemoteException(String message, boolean expired) {
    super(expired ? 409 : 502, message);
    this.expired = expired;
  }
  public boolean expired() { return expired; }
}
