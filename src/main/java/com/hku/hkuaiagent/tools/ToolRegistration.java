package com.hku.hkuaiagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类（禁用WebSearchTool，避免search-api配置依赖）
 */
@Configuration
public class ToolRegistration {

    // 关键修改1：删除search-api.api-key的注入（无需该配置）
    // @Value("${search-api.api-key}")
    // private String searchApiKey;

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        // 关键修改2：注释WebSearchTool（用不到且依赖无效配置）
        // WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();

        // 关键修改3：从返回数组中移除webSearchTool
        return ToolCallbacks.from(
                fileOperationTool,
                // webSearchTool, // 注释掉，不注册该工具
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool
        );
    }
}