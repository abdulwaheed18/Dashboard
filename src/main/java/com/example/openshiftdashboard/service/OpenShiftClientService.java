package com.example.openshiftdashboard.service;

import com.example.openshiftdashboard.config.OpenShiftInstanceProperties;
import com.example.openshiftdashboard.dto.PodUIDetail;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList; // Added import for PodMetricsList
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenShiftClientService {

    private static final Logger logger = LoggerFactory.getLogger(OpenShiftClientService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    public List<PodUIDetail> fetchPodDetailsForInstance(OpenShiftInstanceProperties instanceProperties) {
        if (instanceProperties == null) {
            logger.warn("OpenShift instance properties are null. Skipping.");
            return Collections.emptyList();
        }
        List<PodUIDetail> podDetailsList = new ArrayList<>();
        ConfigBuilder configBuilder = new ConfigBuilder().withMasterUrl(instanceProperties.getUrl());

        if (StringUtils.hasText(instanceProperties.getToken())) {
            configBuilder.withOauthToken(instanceProperties.getToken());
            logger.debug("Using token authentication for instance: {}", instanceProperties.getName());
        } else if (StringUtils.hasText(instanceProperties.getUsername()) && StringUtils.hasText(instanceProperties.getPassword())) {
            configBuilder.withUsername(instanceProperties.getUsername());
            configBuilder.withPassword(instanceProperties.getPassword());
            logger.debug("Using username/password authentication for instance: {}", instanceProperties.getName());
        } else {
            logger.warn("No authentication method (token or username/password) configured for instance: {}. Attempting anonymous connection.", instanceProperties.getName());
        }

        // WARNING: Insecure for production. Configure proper CA certificates.
        configBuilder.withTrustCerts(true);
        logger.debug("Trusting all certificates for instance: {} (Development setting)", instanceProperties.getName());


        Config config = configBuilder.build();

        try (KubernetesClient client = new DefaultKubernetesClient(config)) {
            logger.info("Attempting to connect to OpenShift instance: {} at URL: {}", instanceProperties.getName(), instanceProperties.getUrl());

            if (instanceProperties.getNamespaces() == null || instanceProperties.getNamespaces().isEmpty()) {
                logger.warn("No namespaces configured for instance: {}", instanceProperties.getName());
                return Collections.emptyList();
            }

            for (String namespace : instanceProperties.getNamespaces()) {
                if (!StringUtils.hasText(namespace)) {
                    logger.warn("Empty namespace string found for instance: {}", instanceProperties.getName());
                    continue;
                }
                try {
                    logger.debug("Fetching pods from namespace '{}' in instance '{}'", namespace, instanceProperties.getName());
                    List<Pod> pods = client.pods().inNamespace(namespace).list().getItems();
                    logger.info("Found {} pods in namespace '{}' in instance '{}'", pods.size(), namespace, instanceProperties.getName());
                    for (Pod pod : pods) {
                        podDetailsList.add(mapPodToPodUIDetail(pod, client, instanceProperties.getDataCenter(), namespace));
                    }
                } catch (KubernetesClientException e) {
                    logger.error("Kubernetes API error fetching pods from namespace '{}' in instance '{}'. Status: {}. Message: {}",
                            namespace, instanceProperties.getName(), e.getStatus(), e.getMessage());
                } catch (Exception e) {
                    logger.error("Generic error fetching pods from namespace '{}' in instance '{}': {}",
                            namespace, instanceProperties.getName(), e.getMessage(), e);
                }
            }
        } catch (KubernetesClientException e) {
            logger.error("Failed to connect to or process OpenShift instance '{}' (Kubernetes API error). Status: {}. Message: {}",
                    instanceProperties.getName(), e.getStatus(), e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to connect to or process OpenShift instance '{}': {}",
                    instanceProperties.getName(), e.getMessage(), e);
        }
        return podDetailsList;
    }

    private PodUIDetail mapPodToPodUIDetail(Pod pod, KubernetesClient client, String dataCenter, String currentNamespaceFromConfig) {
        PodUIDetail detail = new PodUIDetail();
        detail.setDataCenter(dataCenter != null ? dataCenter : "N/A");

        String actualNamespace = pod.getMetadata().getNamespace();
        detail.setNamespace(actualNamespace != null ? actualNamespace : currentNamespaceFromConfig);

        detail.setPodName(pod.getMetadata().getName());
        detail.setPodStatus(pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown");
        detail.setPodIP(pod.getStatus() != null ? pod.getStatus().getPodIP() : "N/A");
        detail.setNodeName(pod.getSpec() != null ? pod.getSpec().getNodeName() : "N/A");

        try {
            if (StringUtils.hasText(pod.getMetadata().getCreationTimestamp())) {
                detail.setCreationTimestamp(DATE_TIME_FORMATTER.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(pod.getMetadata().getCreationTimestamp())));
            } else {
                detail.setCreationTimestamp("N/A");
            }
        } catch (DateTimeParseException e) {
            logger.warn("Could not parse creation timestamp '{}' for pod {}: {}", pod.getMetadata().getCreationTimestamp(), pod.getMetadata().getName(), e.getMessage());
            detail.setCreationTimestamp("N/A (Parse Error)");
        }
        detail.setUid(pod.getMetadata().getUid());


        Map<String, String> labels = pod.getMetadata().getLabels() != null ? pod.getMetadata().getLabels() : Collections.emptyMap();
        String appName = labels.getOrDefault("app.kubernetes.io/name", labels.get("app"));
        if (appName == null && pod.getMetadata().getOwnerReferences() != null && !pod.getMetadata().getOwnerReferences().isEmpty()) {
            String ownerName = pod.getMetadata().getOwnerReferences().get(0).getName();
            if (ownerName != null) {
                appName = ownerName.replaceFirst("-[a-zA-Z0-9]{8,10}$", "").replaceFirst("-[0-9]+$", "");
            }
        }
        detail.setApplicationName(appName != null ? appName : "N/A");


        String appVersion = labels.getOrDefault("app.kubernetes.io/version", labels.get("version"));
        String dockerImage = "N/A";
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null && !pod.getSpec().getContainers().isEmpty()) {
            Container firstContainer = pod.getSpec().getContainers().get(0);
            if (firstContainer != null) {
                dockerImage = firstContainer.getImage();
                detail.setDockerImage(dockerImage != null ? dockerImage : "N/A");
                if (appVersion == null && dockerImage != null && dockerImage.contains(":")) {
                    String tag = dockerImage.substring(dockerImage.lastIndexOf(":") + 1);
                    if (!"latest".equalsIgnoreCase(tag) && !tag.matches("^[a-f0-9]{64}$")) {
                        appVersion = tag;
                    }
                }
            }
        }
        detail.setApplicationVersion(appVersion != null ? appVersion : "N/A");

        if ("Helm".equalsIgnoreCase(labels.get("app.kubernetes.io/managed-by"))) {
            detail.setDeploymentType("Helm");
            detail.setHelmChartInfo(labels.getOrDefault("helm.sh/chart", "N/A"));
        } else {
            detail.setDeploymentType("Manifest/Other");
            detail.setHelmChartInfo("N/A");
        }

        detail.setCurrentCpuRequest("N/A");
        detail.setCurrentMemoryRequest("N/A");
        detail.setCurrentCpuLimit("N/A");
        detail.setCurrentMemoryLimit("N/A");

        if (pod.getSpec() != null && pod.getSpec().getContainers() != null && !pod.getSpec().getContainers().isEmpty()) {
            Container container = pod.getSpec().getContainers().get(0);
            if (container != null && container.getResources() != null) {
                if (container.getResources().getRequests() != null) {
                    detail.setCurrentCpuRequest(Optional.ofNullable(container.getResources().getRequests().get("cpu")).map(Quantity::toString).orElse("N/A"));
                    detail.setCurrentMemoryRequest(Optional.ofNullable(container.getResources().getRequests().get("memory")).map(Quantity::toString).orElse("N/A"));
                }
                if (container.getResources().getLimits() != null) {
                    detail.setCurrentCpuLimit(Optional.ofNullable(container.getResources().getLimits().get("cpu")).map(Quantity::toString).orElse("N/A"));
                    detail.setCurrentMemoryLimit(Optional.ofNullable(container.getResources().getLimits().get("memory")).map(Quantity::toString).orElse("N/A"));
                }
            }
        }

        // ** METRICS FETCHING - ADAPTED FOR .metrics() RETURNING PodMetricsList **
        detail.setCurrentCpuUsage("N/A");
        detail.setCurrentMemoryUsage("N/A");
        try {
            var podTopOperations = client.top().pods()
                    .inNamespace(actualNamespace) // Use actual pod namespace
                    .withName(pod.getMetadata().getName());

            PodMetricsList podMetricsList = podTopOperations.metrics(); // Assuming this returns PodMetricsList

            PodMetrics podMetrics = null; // Initialize as null
            if (podMetricsList != null && podMetricsList.getItems() != null && !podMetricsList.getItems().isEmpty()) {
                podMetrics = podMetricsList.getItems().get(0); // Get the first item from the list
                if (podMetricsList.getItems().size() > 1) {
                    logger.warn("Metrics query for single pod {} in namespace {} with .withName().metrics() unexpectedly returned a list with {} items. Using the first one.",
                            pod.getMetadata().getName(), actualNamespace, podMetricsList.getItems().size());
                }
            }


            if (podMetrics != null && podMetrics.getContainers() != null && !podMetrics.getContainers().isEmpty()) {
                io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics containerMetrics = podMetrics.getContainers().get(0);
                if (containerMetrics != null && containerMetrics.getUsage() != null) {
                    detail.setCurrentCpuUsage(Optional.ofNullable(containerMetrics.getUsage().get("cpu")).map(Quantity::toString).orElse("N/A"));
                    detail.setCurrentMemoryUsage(Optional.ofNullable(containerMetrics.getUsage().get("memory")).map(Quantity::toString).orElse("N/A"));
                }
            } else {
                if (podMetricsList == null || podMetricsList.getItems() == null || podMetricsList.getItems().isEmpty()) {
                    logger.debug("No metrics list or empty metrics list returned by .metrics() for pod {} in namespace {}.", pod.getMetadata().getName(), actualNamespace);
                } else if (podMetrics == null) { // List was not empty, but somehow podMetrics is still null (should not happen if list had items)
                    logger.warn("Metrics list was not empty, but failed to extract a PodMetrics object for pod {} in namespace {}.", pod.getMetadata().getName(), actualNamespace);
                }
                else { // podMetrics is not null, but containers are empty or null
                    logger.debug("PodMetrics object found for pod {} in namespace {} but it has no container metrics.", pod.getMetadata().getName(), actualNamespace);
                }
            }
        } catch (KubernetesClientException kce) {
            logger.warn("Could not fetch metrics for pod {} in namespace {} (K8s client exception: {}). Status: {}. This often means metrics are not available or RBAC issues.",
                    pod.getMetadata().getName(), actualNamespace, kce.getMessage(), kce.getStatus());
            detail.setCurrentCpuUsage("N/A (Metrics NA)");
            detail.setCurrentMemoryUsage("N/A (Metrics NA)");
        } catch (Exception e) {
            logger.error("Generic error fetching metrics for pod {} in namespace {}: {}", pod.getMetadata().getName(), actualNamespace, e.getMessage(), e);
            detail.setCurrentCpuUsage("N/A (Error)");
            detail.setCurrentMemoryUsage("N/A (Error)");
        }
        return detail;
    }
}
