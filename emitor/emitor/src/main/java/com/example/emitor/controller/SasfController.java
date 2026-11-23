package com.example.emitor.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/sasf")
public class SasfController {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String defaultTargetUrl;

    public SasfController(
            RestClient.Builder restClientBuilder,
            @Value("${receptor.base-url}") String defaultTargetUrl) {
        this.restClient = restClientBuilder.build();
        this.defaultTargetUrl = defaultTargetUrl;
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(
            @RequestParam(required = false) String targetUrl) {
        String url = buildPingUrl(targetUrl);
        String requestId = UUID.randomUUID().toString();

        log.info("[SASF-EMITOR] Enviando peticion a {} | RequestId={}", url, requestId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", "emitor (SASF)");
        result.put("timestamp", Instant.now().toString());
        result.put("targetUrl", url);
        result.put("requestId", requestId);

        try {
            Map<String, Object> receptorBody = callReceptor(url, requestId);
            result.put("status", "OK");
            result.put("receptorResponse", receptorBody);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("[SASF-EMITOR] Error llamando a {}: {}", url, ex.getMessage());
            result.put("status", "ERROR");
            result.put("error", ex.getMessage());
            return ResponseEntity.status(502).body(result);
        }
    }

    @GetMapping("/burst")
    public ResponseEntity<Map<String, Object>> burst(
            @RequestParam(defaultValue = "5") int count,
            @RequestParam(required = false) String targetUrl) {
        int safeCount = Math.max(1, Math.min(count, 50));
        String url = buildPingUrl(targetUrl);

        log.info("[SASF-EMITOR] Iniciando rafaga de {} peticiones hacia {}", safeCount, url);

        Set<String> uniqueIps = new LinkedHashSet<>();
        List<Map<String, Object>> details = new ArrayList<>();

        for (int i = 1; i <= safeCount; i++) {
            String requestId = UUID.randomUUID().toString();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", i);
            entry.put("requestId", requestId);

            try {
                Map<String, Object> receptorBody = callReceptor(url, requestId);
                Object ip = receptorBody != null ? receptorBody.get("detectedClientIp") : null;
                if (ip != null) {
                    uniqueIps.add(ip.toString());
                }
                entry.put("status", "OK");
                entry.put("detectedClientIp", ip);
            } catch (Exception ex) {
                entry.put("status", "ERROR");
                entry.put("error", ex.getMessage());
            }
            details.add(entry);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "emitor (SASF)");
        response.put("timestamp", Instant.now().toString());
        response.put("targetUrl", url);
        response.put("totalRequests", safeCount);
        response.put("uniqueIpsCount", uniqueIps.size());
        response.put("uniqueIpsDetected", uniqueIps);
        response.put("details", details);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> callReceptor(String url, String requestId) {
        return restClient.get()
                .uri(url)
                .header("X-Request-Id", requestId)
                .header("ngrok-skip-browser-warning", "true")
                .retrieve()
                .body(MAP_TYPE);
    }

    private String buildPingUrl(String overrideBaseUrl) {
        String base = (overrideBaseUrl != null && !overrideBaseUrl.isBlank())
                ? overrideBaseUrl.trim()
                : defaultTargetUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/api/omni/ping") ? base : base + "/api/omni/ping";
    }
}
