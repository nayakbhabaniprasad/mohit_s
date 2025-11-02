package com.mastercard.fraudriskscanner.feeder.monitoring;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mastercard.fraudriskscanner.feeder.config.FeederConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Service to send alerts to Netcool monitoring system.
 * 
 * Uses FRS_0253 alert specification.
 * Sends alerts via HTTP POST using cURL-like functionality.
 * 
 * Reference: https://confluence.mastercard.int/display/EM/How+to+send+a+payload+via+CURL+Command
 * 
 * @author BizOps Bank Team
 */
public class NetcoolAlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(NetcoolAlertService.class);
    
    private final FeederConfig config;
    private final HttpClient httpClient;
    private final Gson gson;
    
    public NetcoolAlertService(FeederConfig config) {
        this.config = config;
        this.gson = new Gson();
        
        // Create HTTP client with timeout
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getNetcoolTimeoutSeconds()))
            .build();
    }
    
    /**
     * Send alert to Netcool.
     * 
     * @param alertId Alert identifier (e.g., "FRS_0253")
     * @param title Alert title
     * @param message Alert message
     * @param severity Alert severity (INFO, WARNING, CRITICAL)
     */
    public void sendAlert(String alertId, String title, String message, String severity) {
        logger.info("Sending alert to Netcool: {}", alertId);
        
        try {
            // Build JSON payload
            JsonObject payload = new JsonObject();
            payload.addProperty("alertId", alertId);
            payload.addProperty("title", title);
            payload.addProperty("message", message);
            payload.addProperty("severity", severity);
            payload.addProperty("timestamp", Instant.now().toString());
            payload.addProperty("source", "fraud-risk-scanner-feeder");
            payload.addProperty("application", "fraud-risk-scanner");
            payload.addProperty("component", "feeder");
            
            String jsonPayload = gson.toJson(payload);
            logger.debug("Alert payload: {}", jsonPayload);
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getNetcoolUrl()))
                .timeout(Duration.ofSeconds(config.getNetcoolTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
            
            // Send request
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            int statusCode = response.statusCode();
            
            if (statusCode >= 200 && statusCode < 300) {
                logger.info("Alert sent successfully to Netcool. Status: {}", statusCode);
                logger.debug("Response: {}", response.body());
            } else {
                logger.warn("Netcool returned non-success status: {}. Response: {}", 
                    statusCode, response.body());
                throw new IOException("Netcool returned status " + statusCode);
            }
            
        } catch (IOException e) {
            logger.error("IO error sending alert to Netcool: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send alert to Netcool", e);
        } catch (InterruptedException e) {
            logger.error("Interrupted while sending alert to Netcool", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending alert", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending alert to Netcool: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send alert to Netcool", e);
        }
    }
    
    /**
     * Test Netcool connectivity.
     * 
     * @return true if Netcool is reachable
     */
    public boolean testConnectivity() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getNetcoolUrl()))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            logger.info("Netcool connectivity test: Status {}", response.statusCode());
            return true;
        } catch (Exception e) {
            logger.warn("Netcool connectivity test failed: {}", e.getMessage());
            return false;
        }
    }
}

