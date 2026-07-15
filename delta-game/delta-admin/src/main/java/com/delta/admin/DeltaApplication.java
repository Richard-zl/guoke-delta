package com.delta.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.delta")
@MapperScan(basePackages = "com.delta.**.mapper")
public class DeltaApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeltaApplication.class, args);
    }
}
