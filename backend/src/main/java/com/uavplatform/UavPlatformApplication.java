package com.uavplatform;

import com.uavplatform.config.WslMysqlHostEnvironmentPostProcessor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UAVPlatform 启动类。
 */
@SpringBootApplication
@MapperScan("com.uavplatform.**.mapper")
@EnableScheduling
public class UavPlatformApplication {

    public static void main(String[] args) {
        WslMysqlHostEnvironmentPostProcessor.applyEarlySystemProperties();
        SpringApplication.run(UavPlatformApplication.class, args);
    }
}
