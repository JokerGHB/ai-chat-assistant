package com.lrj.end;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.lrj.end.mapper")
@EnableScheduling  // 开启定时任务（90 天过期会话清理，见 task.SessionPurgeTask）
public class EndApplication {

    public static void main(String[] args) {
        SpringApplication.run(EndApplication.class, args);
    }

}
