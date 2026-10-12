package com.lrj.end;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.lrj.end.mapper")
public class EndApplication {

    public static void main(String[] args) {
        SpringApplication.run(EndApplication.class, args);
    }

}
