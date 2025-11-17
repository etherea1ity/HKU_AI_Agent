package com.hku.hkuaiagent.external.amap;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Lightweight client that calls AMap's public geocoding endpoint as a fallback when MCP geocoding fails.
 */
@Slf4j
@Component
public class AmapGeocodingFallbackClient {

    private static final String GEO_ENDPOINT = "https://restapi.amap.com/v3/geocode/geo";

    private final RestClient restClient = RestClient.create();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    private final String apiKey;

    public AmapGeocodingFallbackClient(
            @Value("${hku.ai.amap.rest-key:${AMAP_WEB_API_KEY:${AMAP_MAPS_API_KEY:}}}") String apiKey) {
        this.apiKey = StrUtil.isBlank(apiKey) ? null : apiKey;
    }

    public Optional<GeoResult> geocode(String rawAddress) {
        if (StrUtil.isBlank(apiKey)) {
            log.warn("AMap fallback geocode skipped because API key is missing");
            return Optional.empty();
        }
        if (StrUtil.isBlank(rawAddress)) {
            return Optional.empty();
        }
        String address = rawAddress.trim();
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("restapi.amap.com")
                            .path("/v3/geocode/geo")
                            .queryParam("key", apiKey)
                            .queryParam("address", address)
                            .queryParam("output", "JSON")
                            .build())
                    .retrieve()
                    .body(String.class);
            if (StrUtil.isBlank(response)) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("AMap fallback geocode rejected: status={}, info={}, infocode={}",
                        root.path("status").asText(),
                        root.path("info").asText(),
                        root.path("infocode").asText());
                return Optional.empty();
            }
            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray() || geocodes.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = geocodes.get(0);
            String location = first.path("location").asText();
            if (StrUtil.isBlank(location) || !location.contains(",")) {
                return Optional.empty();
            }
            String[] parts = location.split(",");
            double longitude = Double.parseDouble(parts[0]);
            double latitude = Double.parseDouble(parts[1]);
            String formatted = first.path("formatted_address").asText(address);
            return Optional.of(new GeoResult(address, formatted, latitude, longitude));
        } catch (Exception e) {
            log.warn("AMap fallback geocode failed for {}: {}", rawAddress, e.getMessage());
            return Optional.empty();
        }
    }

    public record GeoResult(String query, String formattedAddress, double latitude, double longitude) {
    }
}
