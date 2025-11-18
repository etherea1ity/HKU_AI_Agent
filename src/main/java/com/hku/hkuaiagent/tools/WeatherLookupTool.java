package com.hku.hkuaiagent.tools;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Weather lookup utility backed by the public wttr.in API.
 */
public class WeatherLookupTool {

    private static final String WEATHER_API = "https://wttr.in/%s?format=j1";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = "weather_lookup", description = "Fetch real-time weather and short-range forecast for the provided location. Accepts English or Chinese names, e.g. 'Hong Kong' or 'Beijing'.")
    public String getWeather(@ToolParam(description = "City or location name") String location) {
        if (StrUtil.isBlank(location)) {
            return "Weather lookup requires a location. Please provide one and try again.";
        }

        String trimmedLocation = location.trim();
        List<String> candidateQueries = buildCandidateQueries(trimmedLocation);
        Exception lastException = null;

        for (String candidate : candidateQueries) {
            try {
                String encoded = URLEncoder.encode(candidate, StandardCharsets.UTF_8);
                String url = String.format(WEATHER_API, encoded);
                String responseBody = restTemplate.getForObject(url, String.class);
                if (responseBody == null || StrUtil.isBlank(responseBody)) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(responseBody);
                String formatted = formatWeather(candidate, root);
                if (StrUtil.isNotBlank(formatted)) {
                    return formatted;
                }
            } catch (RestClientException e) {
                lastException = e;
            } catch (Exception e) {
                lastException = e;
            }
        }

        if (lastException instanceof RestClientException) {
            return "Failed to reach the weather service: " + lastException.getMessage();
        }
        if (lastException != null) {
            return "Failed to parse weather data: " + lastException.getMessage();
        }
        return "Sorry, no reliable weather data was returned for " + trimmedLocation + ". Please refine the location name.";
    }

    private List<String> buildCandidateQueries(String baseLocation) {
        List<String> candidates = new ArrayList<>();
        candidates.add(baseLocation);
        String lower = baseLocation.toLowerCase();
        if (!lower.contains("hong kong")) {
            candidates.add(baseLocation + ", Hong Kong");
        }
        if (!lower.contains("china")) {
            candidates.add(baseLocation + ", China");
        }
        candidates.add("Hong Kong");
        List<String> distinct = new ArrayList<>();
        for (String item : candidates) {
            if (StrUtil.isBlank(item)) {
                continue;
            }
            boolean exists = distinct.stream().anyMatch(existing -> existing.equalsIgnoreCase(item));
            if (!exists) {
                distinct.add(item.trim());
            }
        }
        return distinct;
    }

    private String formatWeather(String location, JsonNode root) {
        JsonNode currentConditions = root.path("current_condition");
        if (currentConditions.isMissingNode() || !currentConditions.isArray() || currentConditions.isEmpty()) {
            return "Could not retrieve real-time weather for " + location + ". Please verify the location name.";
        }

        JsonNode current = currentConditions.get(0);
        String tempC = current.path("temp_C").asText("-");
        String feelsLike = current.path("FeelsLikeC").asText("-");
        String description = extractFromArray(current.path("weatherDesc"), "value");
        String humidity = current.path("humidity").asText("-");
        String wind = current.path("windspeedKmph").asText("-");

        String resolvedArea = resolveNearestArea(root);

        List<String> summaryLines = new ArrayList<>();
        if (StrUtil.isNotBlank(resolvedArea) && !resolvedArea.equalsIgnoreCase(location)) {
            summaryLines.add(resolvedArea + " (requested: " + location + ")");
        } else {
            summaryLines.add(location + " weather snapshot:");
        }
        summaryLines.add("- Temperature: " + tempC + " C (feels like " + feelsLike + " C)");
        summaryLines.add("- Conditions: " + description);
        summaryLines.add("- Humidity: " + humidity + "%");
        summaryLines.add("- Wind: " + wind + " km/h");

        JsonNode weatherArray = root.path("weather");
        if (weatherArray.isArray() && !weatherArray.isEmpty()) {
            JsonNode today = weatherArray.get(0);
            String maxTemp = today.path("maxtempC").asText("-");
            String minTemp = today.path("mintempC").asText("-");
            String sunrise = extractFromArray(today.path("astronomy"), "sunrise");
            String sunset = extractFromArray(today.path("astronomy"), "sunset");
            summaryLines.add("- Temperature range today: " + minTemp + " C to " + maxTemp + " C");
            if (StrUtil.isNotBlank(sunrise) || StrUtil.isNotBlank(sunset)) {
                summaryLines.add("- Sunrise / sunset: " + sunrise + " / " + sunset);
            }
        }

        return StrUtil.join("\n", summaryLines);
    }

    private String resolveNearestArea(JsonNode root) {
        JsonNode nearestArea = root.path("nearest_area");
        if (!nearestArea.isArray() || nearestArea.isEmpty()) {
            return null;
        }
        JsonNode firstArea = nearestArea.get(0);
        String areaName = extractFromArray(firstArea.path("areaName"), "value");
        String region = extractFromArray(firstArea.path("region"), "value");
        if (StrUtil.isBlank(areaName) && StrUtil.isBlank(region)) {
            return null;
        }
        if (StrUtil.isNotBlank(areaName) && StrUtil.isNotBlank(region)) {
            return areaName + ", " + region;
        }
        return StrUtil.isNotBlank(areaName) ? areaName : region;
    }

    private String extractFromArray(JsonNode node, String key) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = extractFromArray(item, key);
                if (StrUtil.isNotBlank(value)) {
                    return value;
                }
            }
            return "";
        }
        if (node.has(key)) {
            return node.path(key).asText("");
        }
        if (node.isObject() && node.has("value")) {
            return node.path("value").asText("");
        }
        return node.asText("");
    }
}
