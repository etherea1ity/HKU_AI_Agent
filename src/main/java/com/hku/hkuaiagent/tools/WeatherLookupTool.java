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
 * 全局天气查询工具，调用 wttr.in 免费接口获取指定地点的实时天气。
 */
public class WeatherLookupTool {

    private static final String WEATHER_API = "https://wttr.in/%s?format=j1";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = "weather_lookup", description = "查询指定地点的实时天气与未来预报信息。location 可以是中文或英文的城市/地点名称，例如 'Hong Kong' 或 '香港'。")
    public String getWeather(@ToolParam(description = "城市或地点名称") String location) {
        if (StrUtil.isBlank(location)) {
            return "天气查询需要提供地点名称，请补充后重试。";
        }

        String encoded = URLEncoder.encode(location.trim(), StandardCharsets.UTF_8);
        String url = String.format(WEATHER_API, encoded);

        try {
            String responseBody = restTemplate.getForObject(url, String.class);
            if (StrUtil.isBlank(responseBody)) {
                return "天气服务返回了空结果，请稍后再试。";
            }
            return formatWeather(location.trim(), responseBody);
        } catch (RestClientException e) {
            return "天气服务访问失败：" + e.getMessage();
        } catch (Exception e) {
            return "解析天气数据时出错：" + e.getMessage();
        }
    }

    private String formatWeather(String location, String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode currentConditions = root.path("current_condition");
        if (currentConditions.isMissingNode() || !currentConditions.isArray() || currentConditions.isEmpty()) {
            return "未能获取 " + location + " 的实时天气，请确认地点名称是否正确。";
        }

        JsonNode current = currentConditions.get(0);
        String tempC = current.path("temp_C").asText("-");
        String feelsLike = current.path("FeelsLikeC").asText("-");
        String description = extractFromArray(current.path("weatherDesc"), "value");
        String humidity = current.path("humidity").asText("-");
        String wind = current.path("windspeedKmph").asText("-");

        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("【" + location + " 天气速览】");
        summaryLines.add("- 当前气温：" + tempC + "℃，体感 " + feelsLike + "℃");
        summaryLines.add("- 天气状况：" + description);
        summaryLines.add("- 相对湿度：" + humidity + "%");
        summaryLines.add("- 风速：" + wind + " km/h");

        JsonNode weatherArray = root.path("weather");
        if (weatherArray.isArray() && !weatherArray.isEmpty()) {
            JsonNode today = weatherArray.get(0);
            String maxTemp = today.path("maxtempC").asText("-");
            String minTemp = today.path("mintempC").asText("-");
            String sunrise = extractFromArray(today.path("astronomy"), "sunrise");
            String sunset = extractFromArray(today.path("astronomy"), "sunset");
            summaryLines.add("- 今日温差：" + minTemp + "℃ - " + maxTemp + "℃");
            if (StrUtil.isNotBlank(sunrise) || StrUtil.isNotBlank(sunset)) {
                summaryLines.add("- 日出日落：" + sunrise + " / " + sunset);
            }
        }

        return StrUtil.join("\n", summaryLines);
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
