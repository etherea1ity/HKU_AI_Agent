package com.hku.hkuaiagent.tools;

import com.hku.hkuaiagent.rag.QueryRewriter;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised registry that exposes every tool available to agents.
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Autowired(required = false)
    private VectorStore hkuAiVectorStore;

    @Autowired(required = false)
    private QueryRewriter queryRewriter;

    @Bean
    public ToolCallback[] allTools() {
        List<Object> tools = new ArrayList<>();

        tools.add(new FileOperationTool());
        // tools.add(new WebSearchTool(searchApiKey));
        tools.add(new WebScrapingTool());
        tools.add(new ResourceDownloadTool());
        tools.add(new TerminalOperationTool());
        tools.add(new PDFGenerationTool());
        tools.add(new TerminateTool());
        tools.add(new WeatherLookupTool());

        if (hkuAiVectorStore != null) {
            tools.add(new CampusKnowledgeSearchTool(hkuAiVectorStore, queryRewriter));
        }

        return ToolCallbacks.from(tools.toArray(new Object[0]));
    }
}

