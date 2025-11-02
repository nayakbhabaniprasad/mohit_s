package com.mastercard.fraudriskscanner.feeder.monitoring;

import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors report directories for file presence.
 * Scans directories at configured intervals and triggers alerts if no files found.
 * 
 * Reference: FRS_0253 - No reports found for extended period of time
 * 
 * @author BizOps Bank Team
 */
public class ReportDirectoryMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportDirectoryMonitor.class);
    
    private final FeederConfig config;
    private final NetcoolAlertService alertService;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    public ReportDirectoryMonitor(FeederConfig config, NetcoolAlertService alertService) {
        this.config = config;
        this.alertService = alertService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ReportDirectoryMonitor");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Start monitoring directories.
     */
    public void start() {
        if (running) {
            logger.warn("ReportDirectoryMonitor is already running");
            return;
        }
        
        running = true;
        logger.info("Starting ReportDirectoryMonitor");
        logger.info("Will scan {} directory(ies) every {} minutes", 
            config.getReportDirectories().size(), 
            config.getScanIntervalMinutes());
        
        // Run immediately on start
        scheduler.execute(this::scanDirectories);
        
        // Schedule periodic scans
        scheduler.scheduleAtFixedRate(
            this::scanDirectories,
            config.getScanIntervalMinutes(),
            config.getScanIntervalMinutes(),
            TimeUnit.MINUTES
        );
    }
    
    /**
     * Stop monitoring.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        logger.info("Stopping ReportDirectoryMonitor");
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("ReportDirectoryMonitor stopped");
    }
    
    /**
     * Scan all configured directories for files.
     */
    private void scanDirectories() {
        if (!running) {
            return;
        }
        
        logger.debug("Scanning report directories...");
        
        List<DirectoryScanResult> results = new ArrayList<>();
        
        for (String dirPath : config.getReportDirectories()) {
            DirectoryScanResult result = scanDirectory(dirPath);
            results.add(result);
        }
        
        // Check if any directory needs alerting
        boolean needsAlert = false;
        for (DirectoryScanResult result : results) {
            if (result.needsAlert()) {
                needsAlert = true;
                break;
            }
        }
        
        if (needsAlert) {
            triggerAlert(results);
        } else {
            logger.debug("All directories have files within threshold. No alert needed.");
        }
    }
    
    /**
     * Scan a single directory for files.
     */
    private DirectoryScanResult scanDirectory(String dirPath) {
        DirectoryScanResult result = new DirectoryScanResult(dirPath);
        
        try {
            Path path = Paths.get(dirPath);
            
            // Check if directory exists
            if (!Files.exists(path)) {
                logger.warn("Report directory does not exist: {}", dirPath);
                result.setError("Directory does not exist");
                result.setLastFileTime(null);
                return result;
            }
            
            if (!Files.isDirectory(path)) {
                logger.warn("Path is not a directory: {}", dirPath);
                result.setError("Path is not a directory");
                result.setLastFileTime(null);
                return result;
            }
            
            // Find most recent file in directory
            File directory = path.toFile();
            File[] files = directory.listFiles();
            
            if (files == null || files.length == 0) {
                logger.warn("No files found in directory: {}", dirPath);
                result.setFileCount(0);
                result.setLastFileTime(null);
                return result;
            }
            
            // Find most recent file modification time
            Instant mostRecent = null;
            int fileCount = 0;
            
            for (File file : files) {
                if (file.isFile()) {
                    fileCount++;
                    try {
                        FileTime fileTime = Files.getLastModifiedTime(file.toPath());
                        Instant modified = fileTime.toInstant();
                        
                        if (mostRecent == null || modified.isAfter(mostRecent)) {
                            mostRecent = modified;
                        }
                    } catch (Exception e) {
                        logger.warn("Error reading file modification time for {}: {}", 
                            file.getAbsolutePath(), e.getMessage());
                    }
                }
            }
            
            result.setFileCount(fileCount);
            result.setLastFileTime(mostRecent);
            
            if (mostRecent != null) {
                long hoursSinceLastFile = ChronoUnit.HOURS.between(mostRecent, Instant.now());
                logger.debug("Directory {}: {} files, most recent modified {} hours ago", 
                    dirPath, fileCount, hoursSinceLastFile);
            }
            
        } catch (Exception e) {
            logger.error("Error scanning directory {}: {}", dirPath, e.getMessage(), e);
            result.setError(e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Trigger alert to Netcool if conditions are met.
     */
    private void triggerAlert(List<DirectoryScanResult> results) {
        logger.warn("Alert condition detected: No files found for {} hours", 
            config.getAlertThresholdHours());
        
        // Build alert message
        StringBuilder message = new StringBuilder();
        message.append("FRS_0253: No reports found for extended period of time. ");
        message.append("Threshold: ").append(config.getAlertThresholdHours()).append(" hours. ");
        message.append("Details: ");
        
        for (DirectoryScanResult result : results) {
            message.append("[Directory: ").append(result.getDirectoryPath());
            
            if (result.hasError()) {
                message.append(", Error: ").append(result.getError());
            } else if (result.getLastFileTime() == null) {
                message.append(", Status: No files found");
            } else {
                long hoursAgo = ChronoUnit.HOURS.between(
                    result.getLastFileTime(), 
                    Instant.now()
                );
                message.append(", Last file: ").append(hoursAgo).append(" hours ago");
            }
            message.append("] ");
        }
        
        String alertMessage = message.toString();
        logger.warn(alertMessage);
        
        // Send alert to Netcool
        try {
            alertService.sendAlert(
                "FRS_0253",
                "No Reports Found Alert",
                alertMessage,
                "CRITICAL"
            );
            logger.info("Alert sent to Netcool successfully");
        } catch (Exception e) {
            logger.error("Failed to send alert to Netcool: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Result of scanning a directory.
     */
    private static class DirectoryScanResult {
        private final String directoryPath;
        private int fileCount = 0;
        private Instant lastFileTime;
        private String error;
        
        public DirectoryScanResult(String directoryPath) {
            this.directoryPath = directoryPath;
        }
        
        public boolean needsAlert() {
            if (error != null) {
                return true; // Directory error triggers alert
            }
            
            if (lastFileTime == null) {
                return true; // No files found triggers alert
            }
            
            // Check if last file is older than threshold
            long hoursSinceLastFile = ChronoUnit.HOURS.between(lastFileTime, Instant.now());
            return hoursSinceLastFile >= 24; // 24 hours threshold
        }
        
        // Getters and setters
        public String getDirectoryPath() { return directoryPath; }
        public int getFileCount() { return fileCount; }
        public void setFileCount(int fileCount) { this.fileCount = fileCount; }
        public Instant getLastFileTime() { return lastFileTime; }
        public void setLastFileTime(Instant lastFileTime) { this.lastFileTime = lastFileTime; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public boolean hasError() { return error != null; }
    }
}

