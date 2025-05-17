package com.example.openshiftdashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.example.openshiftdashboard.config.OpenShiftProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OpenShiftProperties.class)
public class OpenshiftDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenshiftDashboardApplication.class, args);
    }
}