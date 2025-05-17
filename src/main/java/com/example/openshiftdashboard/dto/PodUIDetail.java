package com.example.openshiftdashboard.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PodUIDetail {
    private String dataCenter;
    private String namespace;
    private String podName;
    private String applicationName;
    private String applicationVersion; // From image tag or label
    private String deploymentType; // e.g., "Helm", "Docker Image", "Operator"
    private String helmChartInfo; // e.g. "my-chart-0.1.2"
    private String dockerImage;
    private String currentCpuRequest;
    private String currentMemoryRequest;
    private String currentCpuLimit;
    private String currentMemoryLimit;
    private String currentCpuUsage; // Actual usage
    private String currentMemoryUsage; // Actual usage
    private String podStatus;
    private String podIP;
    private String nodeName;
    private String creationTimestamp;
    private String uid; // For unique identification in UI if needed
}