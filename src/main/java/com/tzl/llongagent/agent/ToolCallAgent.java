package com.tzl.llongagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.tzl.llongagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolCallAgent extends ReActAgent {

    // 注入所有的工具
    private ToolCallback[] availableTools;

    // 获取聊天响应，此聊天响应有要调用的工具
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 SpringAI 内置的工具调用机制，手动控制调用工具
    private ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] toolCallbacks) {

        super();

        this.availableTools = toolCallbacks;
        this.toolCallingManager = ToolCallingManager.builder().build();

        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和上下文
        this.chatOptions = ToolCallingChatOptions.builder()
                // 这里是禁止
                .internalToolExecutionEnabled(false)
                .toolCallbacks(availableTools)
                .build();
    }
    /***
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1.校验提示词，拼接用户提示词
        if(StrUtil.isNotBlank(getNEXT_STEP_PROMPT())) {
            Message message = new UserMessage(getNEXT_STEP_PROMPT());
            getMessageList().add(message);
        }

        // 获取维护的上下文记忆列表,方便操作
        List<Message> messageList = getMessageList();

        Prompt prompt = new Prompt(messageList, chatOptions);

        // 2.调用 AI 大模型，获取工具调用列表
        try{

            // 拿到 AI 的响应，响应里面有需要调用的工具
            ChatResponse chatResponse = getDeepseekChatClient()
                    // 用户的消息，封装在上面的prompt中,所以这算是user()
                    .prompt(prompt)
                    .system(getSYSTEM_PROMPT())
                    .call()
                    .chatResponse();

            // 助手工具
            assert chatResponse != null;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();

            // 获取要调用的工具
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

            // 输出提示消息
            String result = assistantMessage.getText();
            log.info("{}的思考: {}", getName(), result);
            log.info("{}选择了: {}个工具来使用", getName(), toolCalls.size());

            // 格式化工具的调用信息
            String toolCallInfo = toolCalls.stream()
                    .map(toolCall -> String.format("工具名称: %s, 参数: %s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);

            // 如果不需要调用工具
            if(toolCalls.isEmpty()) {

                // 由于我们劫持了此次 AI 的返回信息,并进行了一系列操作
                // 当不需要调用工具时, 手动添加 AI 的回复消息进入 上下文列表中
                getMessageList().add(assistantMessage);
                return false;

            } else{

                // 需要调用工具，返回 true
                return true;

            }

        }catch (Exception e) {

            log.info(getName() + "的思考过程遇到了问题" + e.getMessage());

            // 如果报错也是 AI 的回答，需要以 AI 的身份添加进 上下文消息列表中
            getMessageList().add(new AssistantMessage("AI 处理时遇到了问题"));

            // 默认是false，既不调用工具，因为报错了
            return false;
        }
    }
    /***
     * 执行工具调用并处理结果
     * @return 执行结果
     */
    @Override
    public String act() {

        if(!toolCallChatResponse.hasToolCalls())
            return "不需要调用工具";

        // 在 UserMessage和ChatClient中间的一层,Prompt = 上下文加 ChatOptions
        Prompt prompt = new Prompt(getMessageList(), chatOptions);

        // 调用工具
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

        // 记录消息上下文
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));

        // 如果调用了终止工具，那么必须修改本 Agent 的状态
        if(terminateToolCalled)
            setState(AgentState.FINISHED);

        // 从工具调用结束后的信息中，格式化数据
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));

        // 打印格式化的数据
        log.info(results);

        // 返回格式化的数据
        return results;

    }
}
