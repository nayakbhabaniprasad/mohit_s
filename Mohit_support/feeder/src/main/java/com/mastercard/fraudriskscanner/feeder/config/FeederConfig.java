package com.mastercard.fraudriskscanner.feeder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for Feeder application.
 * Reads configuration from environment variables or Habitat TOML.
 * 
 * @author BizOps Bank Team
 */
public class FeederConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(FeederConfig.class);
    
    private List<String> reportDirectories;
    private int scanIntervalMinutes;
    private int alertThresholdHours;
    private String netcoolUrl;
    private int netcoolTimeoutSeconds;
    
    public FeederConfig() {
        loadConfiguration();
    }
    
    /**
     * Load configuration from environment variables.
     * Habitat scaffolding will populate these from default.toml
     */
    private void loadConfiguration() {
        // Read report directories from environment
        reportDirectories = new ArrayList<>();
        String directoriesEnv = getEnv("FEEDER_REPORT_DIRECTORIES", "");
        
        if (directoriesEnv != null && !directoriesEnv.isEmpty()) {
            // Support comma-separated or semicolon-separated directories
            String[] dirs = directoriesEnv.split("[;,]");
            for (String dir : dirs) {
                String trimmedDir = dir.trim();
                if (!trimmedDir.isEmpty()) {
                    reportDirectories.add(trimmedDir);
                }
            }
        }
        
        // Default to example directories if none configured
        if (reportDirectories.isEmpty()) {
            logger.warn("No report directories configured. Using default: /home/bizopsbank/reports");
            reportDirectories.add("/home/bizopsbank/reports");
        }
        
        // Scan interval in minutes (default: 5 minutes)
        scanIntervalMinutes = Integer.parseInt(
            getEnv("FEEDER_SCAN_INTERVAL_MINUTES", "5")
        );
        
        // Alert threshold in hours (default: 24 hours)
        alertThresholdHours = Integer.parseInt(
            getEnv("FEEDER_ALERT_THRESHOLD_HOURS", "24")
        );
        
        // Netcool URL (default: example URL)
        netcoolUrl = getEnv("FEEDER_NETCOOL_URL", 
            "https://netcool.mastercard.int/api/alerts");
        
        // Netcool timeout in seconds (default: 30 seconds)
        netcoolTimeoutSeconds = Integer.parseInt(
            getEnv("FEEDER_NETCOOL_TIMEOUT_SECONDS", "30")
        );
        
        logger.info("Feeder configuration loaded:");
        logger.info("  Report directories: {}", reportDirectories);
        logger.info("  Scan interval: {} minutes", scanIntervalMinutes);
        logger.info("  Alert threshold: {} hours", alertThresholdHours);
        logger.info("  Netcool URL: {}", netcoolUrl);
        logger.info("  Netcool timeout: {} seconds", netcoolTimeoutSeconds);
    }
    
    /**
     * Get environment variable with fallback to default value.
     */
    private String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            // Try system property as fallback
            value = System.getProperty(key, defaultValue);
        }
        return value;
    }
    
    public List<String> getReportDirectories() {
        return new ArrayList<>(reportDirectories);
    }
    
    public int getScanIntervalMinutes() {
        return scanIntervalMinutes;
    }
    
    public int getAlertThresholdHours() {
        return alertThresholdHours;
    }
    
    public String getNetcoolUrl() {
        return netcoolUrl;
    }
    
    public int getNetcoolTimeoutSeconds() {
        return netcoolTimeoutSeconds;
    }
    
    /**
     * Validate configuration.
     * @return true if configuration is valid
     */
    public boolean isValid() {
        if (reportDirectories.isEmpty()) {
            logger.error("No report directories configured");
            return false;
        }
        
        if (scanIntervalMinutes <= 0) {
            logger.error("Scan interval must be greater than 0");
            return false;
        }
        
        if (alertThresholdHours <= 0) {
            logger.error("Alert threshold must be greater than 0");
            return false;
        }
        
        if (netcoolUrl == null || netcoolUrl.isEmpty()) {
            logger.error("Netcool URL must be configured");
            return false;
        }
        
        return true;
    }
}

