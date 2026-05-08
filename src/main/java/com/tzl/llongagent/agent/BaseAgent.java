package com.tzl.llongagent.agent;

import cn.hutool.core.util.StrUtil;
import com.tzl.llongagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/***
 * 抽象的基础代理类，用于管理代理状态和执行流程
 * 提供状态转换，内存管理和基于步骤的执行循环的基础功能
 * 子类必须实现 step 方法
 */
@Slf4j
@Data
public abstract class BaseAgent {

    // Agent 的名字
    private String name;

    // 系统提示词
    private String SYSTEM_PROMPT;

    // 引导智能体下一步的提示词
    private String NEXT_STEP_PROMPT;

    // agent 的状态,默认是空闲
    private AgentState state = AgentState.IDLE;

    // 当前步骤
    private int currentStep = 0;

    // 最大的执行次数
    private int maxStep = 20;

    // LLM 大模型
    private ChatClient deepseekChatClient;

    // 大模型的上下文 memory 记忆(需自己维护)
    private List<Message> messageList = new ArrayList<>();

    /***
     * 运行代理
     * @param userMessage 用户的提示词
     * @param conversationId 用户的对话ID
     * @return 执行结果
     */
    public String run(String userMessage, String conversationId) {

        // 1.基础校验
        // 对 Agent 状态和提示词合法性 进行判断
        if (this.state != AgentState.IDLE)
            throw new RuntimeException("Agent cannot run agent form this state :" + state);
        if (StrUtil.isBlank(userMessage))
            throw new RuntimeException("Agent cannot run with empty userMessage!");
        if (StrUtil.isBlank(conversationId))
            throw new RuntimeException("Agent need your conversationId!");

        // 2.修改状态,避免冲突
        this.state = AgentState.RUNNING;

        // 3.记录上下文信息
        messageList.add(new UserMessage(userMessage));

        // 4.保存结果列表(大模型返回的是String, 且模型只认String)
        List<String> results = new ArrayList<>();

        try {
            for (int i = 0; i < maxStep; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxStep);
                // 单步执行结果
                String stepResult = this.step();
                String result = "Step" + stepNumber + ":" + stepResult;
                results.add(result);
            }

            if (currentStep == maxStep) {
                this.state = AgentState.FINISHED;
                results.add("Terminated : Agent has reached max step:(" + maxStep + ")");
                log.info("Reached max step" + maxStep);
            }

            return String.join("\n", results);
        }catch(Exception e) {
            this.state = AgentState.ERROR;
            log.error("Agent executing error",e);
            return "执行错误" + e.getMessage();
        } finally {
          cleanUp();
        }
    }


    public abstract String step();

    public void cleanUp(){}

}
