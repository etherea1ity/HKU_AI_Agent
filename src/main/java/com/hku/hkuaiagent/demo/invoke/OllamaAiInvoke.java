package com.hku.hkuaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Demonstrates calling an Ollama chat model using Spring AI.
 */
// Uncomment the annotation below to execute this demo during application startup.
//@Component
public class OllamaAiInvoke implements CommandLineRunner {

    @Resource
    private ChatModel ollamaChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage assistantMessage = ollamaChatModel.call(new Prompt("State which Ollama model you are in a single sentence."))
                .getResult()
                .getOutput();
        System.out.println(assistantMessage.getText());
    }
}

