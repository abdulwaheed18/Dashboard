package com.example.openshiftdashboard.config;

import lombok.Data;
import java.util.List;

@Data // Lombok annotation for getters, setters, toString, etc.
public class OpenShiftInstanceProperties {
    private String name; // e.g., "DataCenterA-Cluster1"
    private String url; // OpenShift API URL e.g., "https://api.cluster1.example.com:6443"
    private String username;
    private String password; // For PoC. Secure this for production (e.g., token, Vault)
    private String token; // Alternative to username/password
    private List<String> namespaces;
    private String dataCenter; // User-defined data center name
}