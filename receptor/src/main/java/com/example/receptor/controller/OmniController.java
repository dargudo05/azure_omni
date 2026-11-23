package com.example.receptor.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/omni")
public class OmniController {

    @GetMapping("/ping")
    public Map<String, Object> ping(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        String remoteAddr = request.getRemoteAddr();
        String requestId = request.getHeader("X-Request-Id");
        String userAgent = request.getHeader("User-Agent");

        String detectedClientIp = resolveClientIp(xForwardedFor, xRealIp, remoteAddr);

        log.info("[OMNI-LOG] IP detectada={} | X-Forwarded-For={} | X-Real-IP={} | RemoteAddr={} | RequestId={} | User-Agent={}",
                detectedClientIp,
                xForwardedFor,
                xRealIp,
                remoteAddr,
                requestId,
                userAgent);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "receptor (Omnihospital)");
        response.put("timestamp", Instant.now().toString());
        response.put("detectedClientIp", detectedClientIp);
        response.put("xForwardedFor", xForwardedFor);
        response.put("xRealIp", xRealIp);
        response.put("remoteAddr", remoteAddr);
        response.put("requestId", requestId);
        return response;
    }

    private String resolveClientIp(String xForwardedFor, String xRealIp, String remoteAddr) {
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return remoteAddr;
    }
}
