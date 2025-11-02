package com.mastercard.fraudriskscanner.feeder;

import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import com.mastercard.fraudriskscanner.feeder.monitoring.NetcoolAlertService;
import com.mastercard.fraudriskscanner.feeder.monitoring.ReportDirectoryMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feeder Application
 * 
 * Monitors report directories and sends alerts to Netcool when no files are found
 * for an extended period of time (24 hours by default).
 * 
 * Requirements:
 * - Scan configured directories every 5 minutes
 * - Alert to Netcool if no files found for 24 hours
 * - Support multiple report directories
 * - Use FRS_0253 alert specification
 * 
 * @author BizOps Bank Team
 * @version 0.1.0
 */
public class FeederApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(FeederApplication.class);
    
    private FeederConfig config;
    private NetcoolAlertService alertService;
    private ReportDirectoryMonitor monitor;
    
    public static void main(String[] args) {
        FeederApplication app = new FeederApplication();
        app.start();
        app.run();
    }
    
    /**
     * Initialize and start the application.
     */
    private void start() {
        logger.info("Starting Feeder Application");
        
        try {
            // Load configuration
            config = new FeederConfig();
            
            if (!config.isValid()) {
                logger.error("Invalid configuration. Exiting.");
                System.exit(1);
            }
            
            // Initialize services
            alertService = new NetcoolAlertService(config);
            
            // Test Netcool connectivity (non-blocking)
            new Thread(() -> {
                try {
                    Thread.sleep(5000); // Wait 5 seconds for startup
                    alertService.testConnectivity();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            // Initialize and start directory monitor
            monitor = new ReportDirectoryMonitor(config, alertService);
            monitor.start();
            
            logger.info("Feeder Application started successfully");
            logger.info("Monitoring {} directory(ies) every {} minutes", 
                config.getReportDirectories().size(),
                config.getScanIntervalMinutes());
            
        } catch (Exception e) {
            logger.error("Failed to start Feeder Application: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    /**
     * Keep application running until interrupted.
     */
    private void run() {
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            if (monitor != null) {
                monitor.stop();
            }
            logger.info("Feeder Application stopped");
        }));
        
        // Keep application running
        try {
            // Sleep indefinitely, waiting for shutdown signal
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            logger.info("Feeder Application interrupted");
            if (monitor != null) {
                monitor.stop();
            }
            Thread.currentThread().interrupt();
        }
    }
}
