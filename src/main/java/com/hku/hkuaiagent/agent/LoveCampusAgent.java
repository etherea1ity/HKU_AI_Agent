package com.hku.hkuaiagent.agent;

import com.hku.hkuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * HKU Campus 专用代理，支持工具调用（PDF、天气、MCP 等）
 */
@Component
public class LoveCampusAgent extends ToolCallAgent {

    public LoveCampusAgent(ToolCallback[] allTools, ToolCallbackProvider mcpTools, ChatModel dashscopeChatModel) {
        super(allTools, mcpTools);
        this.setName("loveCampusAgent");
        String SYSTEM_PROMPT = """
                你是 HKU Campus Assistant，专注于校园问答与检索，回答时尽量引用证据并在必要时调用工具（如天气查询、PDF 生成、资源下载等）。
                行为约束：
                1. 当需要检索知识库或引用文档，优先调用 campusRagSearch（HKU 知识库检索工具），并在输出中提供引用文档的文件名。
                2. 天气查询应调用 weather_lookup；不要使用 maps_weather。
                3. 当需要生成文件/报告时，调用 PDF 生成工具并在完成后把下载链接返回给用户。
                4. 如果工具返回 HTML、JSON 或其它技术格式数据，先提炼出与用户问题相关的重点，再用清晰的中文回复，绝不要原样输出技术内容。
                5. 最终回复中不要包含技术性工具调用细节，且必须使用纯中文文本，不要使用 Markdown 语法（如 #、**、`）。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);

        String NEXT_STEP_PROMPT = """
                根据当前对话目标，选择最合适的工具或检索策略：
                - 如果需要检索文件或引用资料，第一步调用 campusRagSearch（HKU 知识库检索工具），并将检索到的文件名/摘要返回到深度思考面板。
                - 天气：使用 weather_lookup 工具。
                - 生成文档：使用 generatePDF 工具并返回下载链接。
                在所有工具调用完成后，用清晰的中文总结结果并回答用户问题，不要在回答中包含原始 JSON 或 HTML；编号条目请独立换行，确保阅读顺畅。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(10);

        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
