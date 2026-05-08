package com.tzl.llongagent.agent;

import com.tzl.llongagent.advisor.llongLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/***
 * 龙的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class llongManus extends ToolCallAgent{

    public llongManus(ToolCallback[] allTools, DeepSeekChatModel deepseekChatModel) {
        super(allTools);
        this.setName("llongManus");
        String SYSTEM_PROMPT = """
                You are llongManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                """;
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.                
                """;
        this.setMaxStep(20);
        this.setSYSTEM_PROMPT(SYSTEM_PROMPT);
        this.setNEXT_STEP_PROMPT(NEXT_STEP_PROMPT);

        ChatClient deepseekChatClient = ChatClient.builder(deepseekChatModel)
                .defaultAdvisors(new llongLoggerAdvisor())
                .build();

        // 在上上层中，我们定义了 ChatClient，在这里我们传入
        this.setDeepseekChatClient(deepseekChatClient);


    }

}
