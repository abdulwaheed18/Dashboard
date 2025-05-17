package com.example.openshiftdashboard.service;

import com.example.openshiftdashboard.config.OpenShiftInstanceProperties;
import com.example.openshiftdashboard.dto.PodUIDetail;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics; // Correct import for PodMetrics
// import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList; // Not needed if we get a single PodMetrics
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException; // Import for specific exception handling
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenShiftClientService {

    private static final Logger logger = LoggerFactory.getLogger(OpenShiftClientService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());


    public List<PodUIDetail> fetchPodDetailsForInstance(OpenShiftInstanceProperties instanceProperties) {
        List<PodUIDetail> podDetailsList = new ArrayList<>();
        ConfigBuilder configBuilder = new ConfigBuilder().withMasterUrl(instanceProperties.getUrl());

        if (StringUtils.hasText(instanceProperties.getToken())) {
            configBuilder.withOauthToken(instanceProperties.getToken());
        } else if (StringUtils.hasText(instanceProperties.getUsername()) && StringUtils.hasText(instanceProperties.getPassword())) {
            configBuilder.withUsername(instanceProperties.getUsername());
            configBuilder.withPassword(instanceProperties.getPassword());
        } else {
            logger.warn("No authentication method configured for instance: {}. Attempting anonymous connection.", instanceProperties.getName());
        }

        configBuilder.withTrustCerts(true); // WARNING: Use with caution, ideally configure proper trust.

        Config config = configBuilder.build();

        try (KubernetesClient client = new DefaultKubernetesClient(config)) {
            logger.info("Successfully connected to OpenShift instance: {}", instanceProperties.getName());
            for (String namespace : instanceProperties.getNamespaces()) {
                try {
                    List<Pod> pods = client.pods().inNamespace(namespace).list().getItems();
                    for (Pod pod : pods) {
                        podDetailsList.add(mapPodToPodUIDetail(pod, client, instanceProperties.getDataCenter(), namespace));
                    }
                } catch (Exception e) {
                    logger.error("Error fetching pods from namespace {} in instance {}: {}", namespace, instanceProperties.getName(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to connect or process OpenShift instance {}: {}", instanceProperties.getName(), e.getMessage(), e);
        }
        return podDetailsList;
    }

    private PodUIDetail mapPodToPodUIDetail(Pod pod, KubernetesClient client, String dataCenter, String namespace) {
        PodUIDetail detail = new PodUIDetail();
        detail.setDataCenter(dataCenter);
        detail.setNamespace(pod.getMetadata().getNamespace());
        detail.setPodName(pod.getMetadata().getName());
        detail.setPodStatus(pod.getStatus().getPhase());
        detail.setPodIP(pod.getStatus().getPodIP());
        detail.setNodeName(pod.getSpec().getNodeName());
        try {
            detail.setCreationTimestamp(DATE_TIME_FORMATTER.format(DATE_TIME_FORMATTER.parse(pod.getMetadata().getCreationTimestamp())));
        } catch (Exception e) {
            logger.warn("Could not parse creation timestamp: {}", pod.getMetadata().getCreationTimestamp());
            detail.setCreationTimestamp("N/A");
        }
        detail.setUid(pod.getMetadata().getUid());


        Map<String, String> labels = pod.getMetadata().getLabels() != null ? pod.getMetadata().getLabels() : Map.of();
        String appName = labels.getOrDefault("app.kubernetes.io/name", labels.getOrDefault("app", null));
        if (appName == null && pod.getMetadata().getOwnerReferences() != null && !pod.getMetadata().getOwnerReferences().isEmpty()) {
            String ownerName = pod.getMetadata().getOwnerReferences().get(0).getName();
            appName = ownerName.replaceAll("-[a-zA-Z0-9]+$", "").replaceAll("-[0-9]+$", "");
        }
        detail.setApplicationName(appName != null ? appName : "N/A");


        String appVersion = labels.getOrDefault("app.kubernetes.io/version", labels.get("version"));
        String dockerImage = "N/A";
        if (pod.getSpec().getContainers() != null && !pod.getSpec().getContainers().isEmpty()) {
            dockerImage = pod.getSpec().getContainers().get(0).getImage();
            detail.setDockerImage(dockerImage);
            if (appVersion == null && dockerImage != null && dockerImage.contains(":")) {
                appVersion = dockerImage.substring(dockerImage.lastIndexOf(":") + 1);
            }
        }
        detail.setApplicationVersion(appVersion != null ? appVersion : "N/A");

        if ("Helm".equalsIgnoreCase(labels.get("app.kubernetes.io/managed-by"))) {
            detail.setDeploymentType("Helm");
            detail.setHelmChartInfo(labels.getOrDefault("helm.sh/chart", "N/A"));
        } else {
            detail.setDeploymentType("Manifest/Other");
        }


        if (pod.getSpec().getContainers() != null && !pod.getSpec().getContainers().isEmpty()) {
            Container container = pod.getSpec().getContainers().get(0);
            if (container.getResources() != null) {
                if (container.getResources().getRequests() != null) {
                    detail.setCurrentCpuRequest(Optional.ofNullable(container.getResources().getRequests().get("cpu")).map(Quantity::toString).orElse("N/A"));
                    detail.setCurrentMemoryRequest(Optional.ofNullable(container.getResources().getRequests().get("memory")).map(Quantity::toString).orElse("N/A"));
                } else {
                    detail.setCurrentCpuRequest("N/A");
                    detail.setCurrentMemoryRequest("N/A");
                }
                if (container.getResources().getLimits() != null) {
                    detail.setCurrentCpuLimit(Optional.ofNullable(container.getResources().getLimits().get("cpu")).map(Quantity::toString).orElse("N/A"));
                    detail.setCurrentMemoryLimit(Optional.ofNullable(container.getResources().getLimits().get("memory")).map(Quantity::toString).orElse("N/A"));
                } else {
                    detail.setCurrentCpuLimit("N/A");
                    detail.setCurrentMemoryLimit("N/A");
                }
            } else {
                detail.setCurrentCpuRequest("N/A");
                detail.setCurrentMemoryRequest("N/A");
                detail.setCurrentCpuLimit("N/A");
                detail.setCurrentMemoryLimit("N/A");
            }
        } else {
            detail.setCurrentCpuRequest("N/A");
            detail.setCurrentMemoryRequest("N/A");
            detail.setCurrentCpuLimit("N/A");
            detail.setCurrentMemoryLimit("N/A");
        }

        // ** CORRECTED METRICS FETCHING **
        try {
            // Use withName(...).get() for fetching metrics for a specific pod
            PodMetrics podMetrics = client.top().pods()
                    .inNamespace(namespace)
                    .withName(pod.getMetadata().getName()).get(); // This should return a single PodMetrics object or null

            if (podMetrics != null && podMetrics.getContainers() != null && !podMetrics.getContainers().isEmpty()) {
                io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics containerMetrics = podMetrics.getContainers().get(0);
                detail.setCurrentCpuUsage(Optional.ofNullable(containerMetrics.getUsage().get("cpu")).map(Quantity::toString).orElse("N/A"));
                detail.setCurrentMemoryUsage(Optional.ofNullable(containerMetrics.getUsage().get("memory")).map(Quantity::toString).orElse("N/A"));
            } else {
                detail.setCurrentCpuUsage("N/A");
                detail.setCurrentMemoryUsage("N/A");
                if (podMetrics == null) {
                    logger.debug("No metrics object returned for pod {} in namespace {}.", pod.getMetadata().getName(), namespace);
                } else {
                    logger.debug("Metrics object returned for pod {} but no container metrics found.", pod.getMetadata().getName(), namespace);
                }
            }
        } catch (KubernetesClientException kce) {
            // This can happen if the metrics server returns 404 (e.g., pod not found by metrics server, or metrics not yet available)
            logger.warn("Could not fetch metrics for pod {} in namespace {} (K8s client exception, often due to metrics not available): {}",
                    pod.getMetadata().getName(), namespace, kce.getMessage());
            detail.setCurrentCpuUsage("N/A (No Metrics)");
            detail.setCurrentMemoryUsage("N/A (No Metrics)");
        } catch (Exception e) {
            logger.error("Generic error fetching metrics for pod {} in namespace {}: {}", pod.getMetadata().getName(), namespace, e.getMessage(), e);
            detail.setCurrentCpuUsage("N/A (Error)");
            detail.setCurrentMemoryUsage("N/A (Error)");
        }
        return detail;
    }
}