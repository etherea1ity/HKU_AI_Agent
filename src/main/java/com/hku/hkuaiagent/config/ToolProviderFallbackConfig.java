package com.hku.hkuaiagent.config;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies a no-op ToolCallbackProvider when MCP integration is disabled or unavailable.
 */
@Configuration
public class ToolProviderFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(ToolCallbackProvider.class)
    public ToolCallbackProvider emptyToolCallbackProvider() {
        return () -> new ToolCallback[0];
    }
}
