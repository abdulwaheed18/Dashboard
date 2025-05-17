package com.example.openshiftdashboard.scheduler;

import com.example.openshiftdashboard.service.DashboardDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataRefreshScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DataRefreshScheduler.class);
    private final DashboardDataService dashboardDataService;

    public DataRefreshScheduler(DashboardDataService dashboardDataService) {
        this.dashboardDataService = dashboardDataService;
    }

    // Cron expression from application.yml, e.g., "0 0/30 * * * ?" for every 30 minutes
    @Scheduled(cron = "${openshift.scheduler-cron}")
    public void refreshOpenShiftData() {
        logger.info("Scheduled OpenShift data refresh triggered.");
        dashboardDataService.refreshAllData();
    }
}