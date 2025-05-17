package com.example.openshiftdashboard.service;

import com.example.openshiftdashboard.config.OpenShiftInstanceProperties;
import com.example.openshiftdashboard.config.OpenShiftProperties;
import com.example.openshiftdashboard.dto.PodUIDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DashboardDataService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardDataService.class);

    private final OpenShiftProperties openShiftProperties;
    private final OpenShiftClientService openShiftClientService;

    // In-memory cache for simplicity. For larger scale, consider a distributed cache or database.
    private final List<PodUIDetail> cachedPodDetails = new CopyOnWriteArrayList<>(); // Thread-safe for read/write

    public DashboardDataService(OpenShiftProperties openShiftProperties, OpenShiftClientService openShiftClientService) {
        this.openShiftProperties = openShiftProperties;
        this.openShiftClientService = openShiftClientService;
    }

    public void refreshAllData() {
        logger.info("Starting data refresh from all OpenShift instances...");
        List<PodUIDetail> allPodDetails = new ArrayList<>();
        if (openShiftProperties.getInstances() == null || openShiftProperties.getInstances().isEmpty()) {
            logger.warn("No OpenShift instances configured. Skipping data refresh.");
            cachedPodDetails.clear();
            return;
        }

        for (OpenShiftInstanceProperties instanceConfig : openShiftProperties.getInstances()) {
            logger.info("Fetching data from instance: {}", instanceConfig.getName());
            try {
                List<PodUIDetail> instancePodDetails = openShiftClientService.fetchPodDetailsForInstance(instanceConfig);
                allPodDetails.addAll(instancePodDetails);
                logger.info("Fetched {} pod details from instance: {}", instancePodDetails.size(), instanceConfig.getName());
            } catch (Exception e) {
                logger.error("Error fetching data for instance {}: {}", instanceConfig.getName(), e.getMessage(), e);
            }
        }

        cachedPodDetails.clear();
        cachedPodDetails.addAll(allPodDetails);
        logger.info("Data refresh completed. Total pod details cached: {}", cachedPodDetails.size());
    }

    public List<PodUIDetail> getAllPodDetails() {
        if (cachedPodDetails.isEmpty()) {
            // Optionally trigger a refresh if cache is empty and accessed for the first time
            // refreshAllData(); // Be careful with this in a web context to avoid long initial load times
            logger.info("Cache is currently empty. A scheduled refresh should populate it.");
        }
        return Collections.unmodifiableList(new ArrayList<>(cachedPodDetails)); // Return a copy
    }
}