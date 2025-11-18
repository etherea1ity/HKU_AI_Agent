package com.hku.hkuaiagent.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * Demonstrates calling DashScope over raw HTTP.
 */
public class HttpAiInvoke {

    public static void main(String[] args) {
        // API key used for the demo
        String apiKey = TestApiKey.API_KEY;

        // DashScope generation endpoint
        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        // Build the JSON request payload
        JSONObject inputJson = new JSONObject();
        JSONObject messagesJson = new JSONObject();

        // System message to steer the assistant
        JSONObject systemMessage = new JSONObject();
        systemMessage.set("role", "system");
        systemMessage.set("content", "You are a helpful assistant.");

        // User message that drives the response
        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", "Who are you?");

        // Assemble the messages array
        messagesJson.set("messages", JSONUtil.createArray().set(systemMessage).set(userMessage));

        // Basic result configuration
        JSONObject parametersJson = new JSONObject();
        parametersJson.set("result_format", "message");

        // Final request body containing model, input, and parameters
        JSONObject requestJson = new JSONObject();
        requestJson.set("model", "qwen-plus");
        requestJson.set("input", messagesJson);
        requestJson.set("parameters", parametersJson);

        // Send the request
        String result = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestJson.toString())
                .execute()
                .body();

        // Print the raw JSON result
        System.out.println(result);
    }
}
