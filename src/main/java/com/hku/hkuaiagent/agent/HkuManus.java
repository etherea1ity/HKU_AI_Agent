package com.hku.hkuaiagent.agent;

import com.hku.hkuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * HKU 的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class HkuManus extends ToolCallAgent {

        public HkuManus(ToolCallback[] allTools, ToolCallbackProvider mcpTools, ChatModel dashscopeChatModel) {
        super(allTools,mcpTools);
                this.setName("hkuManus");
        String SYSTEM_PROMPT = """
                                你是 HkuManus，一个全能的 AI 助手，旨在解决用户提出的任何任务。
                你可以使用各种工具来高效完成复杂的请求。
                
                重要原则：
                1. 当你需要使用工具时，直接调用相应的工具函数
                2. 当所有工具调用完成后，你必须用自然、友好的语言总结结果，并直接回答用户的问题
                3. 不要在最终回复中包含技术细节、JSON格式或工具调用信息
                4. 始终以用户为中心，确保你的回复对用户有实际帮助
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或工具组合。
                
                如果需要使用工具：
                - 对于复杂任务，可以分解问题并逐步使用不同的工具来解决
                - 调用相应的工具函数获取信息
                
                工具使用注意事项：
                - 生成PDF时，content参数应该包含完整的文本内容，支持Markdown格式（# 标题，## 副标题，**粗体**等）
                - 如果用户要求生成文档，主动为文档添加合适的标题和结构化的内容
                - PDF工具返回的是下载链接，请将完整的链接告诉用户
                
                如果所有必要的工具都已调用完成：
                - 不要再调用任何工具
                - 基于工具返回的结果，用清晰、自然的中文语言回答用户的问题
                - 直接提供有价值的信息，不要提及工具的使用过程
                - 如果找到了用户需要的信息（如餐厅列表、地址、下载链接等），清晰地列出来
                
                如果你想在任何时候停止交互，使用 `terminate` 工具/函数调用。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        // 限定10次调用
        this.setMaxSteps(10);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}

