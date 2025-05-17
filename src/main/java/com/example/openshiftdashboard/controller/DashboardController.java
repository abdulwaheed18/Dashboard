package com.example.openshiftdashboard.controller;

import com.example.openshiftdashboard.dto.PodUIDetail;
import com.example.openshiftdashboard.service.DashboardDataService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class DashboardController {

    private final DashboardDataService dashboardDataService;

    public DashboardController(DashboardDataService dashboardDataService) {
        this.dashboardDataService = dashboardDataService;
    }

    @GetMapping("/")
    public String getDashboard(Model model,
                               @RequestParam(required = false) String filterDataCenter,
                               @RequestParam(required = false) String filterNamespace,
                               @RequestParam(required = false) String filterAppName,
                               @RequestParam(required = false) String filterPodStatus) {
        List<PodUIDetail> allDetails = dashboardDataService.getAllPodDetails();
        Stream<PodUIDetail> filteredStream = allDetails.stream();

        if (filterDataCenter != null && !filterDataCenter.isEmpty()) {
            filteredStream = filteredStream.filter(p -> filterDataCenter.equalsIgnoreCase(p.getDataCenter()));
        }
        if (filterNamespace != null && !filterNamespace.isEmpty()) {
            filteredStream = filteredStream.filter(p -> filterNamespace.equalsIgnoreCase(p.getNamespace()));
        }
        if (filterAppName != null && !filterAppName.isEmpty()) {
            filteredStream = filteredStream.filter(p -> p.getApplicationName() != null && p.getApplicationName().toLowerCase().contains(filterAppName.toLowerCase()));
        }
        if (filterPodStatus != null && !filterPodStatus.isEmpty()) {
            filteredStream = filteredStream.filter(p -> filterPodStatus.equalsIgnoreCase(p.getPodStatus()));
        }

        model.addAttribute("podDetailsList", filteredStream.collect(Collectors.toList()));
        model.addAttribute("dataCenters", allDetails.stream().map(PodUIDetail::getDataCenter).distinct().sorted().collect(Collectors.toList()));
        model.addAttribute("namespaces", allDetails.stream().map(PodUIDetail::getNamespace).distinct().sorted().collect(Collectors.toList()));
        model.addAttribute("podStatuses", allDetails.stream().map(PodUIDetail::getPodStatus).distinct().sorted().collect(Collectors.toList()));

        // Pass current filter values back to the view to repopulate filter fields
        model.addAttribute("currentDataCenter", filterDataCenter);
        model.addAttribute("currentNamespace", filterNamespace);
        model.addAttribute("currentAppName", filterAppName);
        model.addAttribute("currentPodStatus", filterPodStatus);


        return "dashboard"; // Name of the Thymeleaf HTML file (dashboard.html)
    }

    // Optional: Endpoint to refresh data manually via HTTP GET
    @GetMapping("/refresh-data")
    @ResponseBody // Indicates the return value should be directly in the response body
    public String manualRefreshData() {
        dashboardDataService.refreshAllData();
        return "Data refresh initiated. Check logs for status. New data will appear on next dashboard load/refresh.";
    }
}