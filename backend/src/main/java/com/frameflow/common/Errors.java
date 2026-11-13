package com.frameflow.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class Errors {
  private static final Logger log = LoggerFactory.getLogger(Errors.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Api<Void>> business(BusinessException ex) {
    return result(ex.status(), ex.getMessage());
  }

  @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
      org.springframework.web.servlet.NoHandlerFoundException.class})
  public ResponseEntity<Api<Void>> notFound(Exception ex) {
    return result(404, "接口不存在或尚未启用");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Api<Void>> validation(MethodArgumentNotValidException ex) {
    return result(400, ex.getBindingResult().getFieldErrors().getFirst().getDefaultMessage());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<Api<Void>> malformed(Exception ex) {
    return result(400, "请求参数格式错误，请检查日期、数字及必填字段");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Api<Void>> upload(MaxUploadSizeExceededException ex) {
    return result(400, "文件大小不能超过100MB");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Api<Void>> integrity(DataIntegrityViolationException ex) {
    if (ex.getMostSpecificCause() instanceof java.sql.SQLException sql
        && "45000".equals(sql.getSQLState())) return result(409, sql.getMessage());
    return result(409, "数据重复、关联记录不存在或违反业务约束，请检查后重试");
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<Api<Void>> database(DataAccessException ex) {
    Throwable cause = ex.getMostSpecificCause();
    if (cause instanceof java.sql.SQLException sql && "45000".equals(sql.getSQLState()))
      return result(409, sql.getMessage());
    log.error("数据库操作失败", ex);
    return result(500, "数据库操作失败，请检查服务端日志");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Api<Void>> other(Exception ex) {
    log.error("请求处理失败", ex);
    return result(500, "服务暂时无法完成请求，请检查服务端日志");
  }

  private ResponseEntity<Api<Void>> result(int status, String message) {
    return ResponseEntity.status(status).body(new Api<>(status, message, null));
  }
}
