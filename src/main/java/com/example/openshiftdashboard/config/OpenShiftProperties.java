package com.example.openshiftdashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "openshift")
public class OpenShiftProperties {
    private List<OpenShiftInstanceProperties> instances;
    private String schedulerCron = "0 0/30 * * * ?"; // Default: every 30 mins
}