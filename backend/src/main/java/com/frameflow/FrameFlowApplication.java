package com.frameflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.frameflow.mapper")
public class FrameFlowApplication {
  public static void main(String[] args) {
    SpringApplication.run(FrameFlowApplication.class, args);
  }
}
