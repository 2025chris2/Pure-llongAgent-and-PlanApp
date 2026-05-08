package com.tzl.llongagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/***
 * 自定义日志Advisor
 * 打印 info 级别日志、只输出单次用户提示词和 AI 回复的文本
 */
@Slf4j
public class llongLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    private final int order;

    public llongLoggerAdvisor() {
        this(0);
    }

    public llongLoggerAdvisor(int order) {
        this.order = order;
    }

    // 发送请求时打印
    protected void logRequest(ChatClientRequest request) {
        log.info("llong的觅途的request日志打印: {}", request.context());
    }

    // 接收响应时打印
    protected void logResponse(ChatClientResponse response) {
        log.info("llong的觅途的response日志打印: {}", response.chatResponse().getResult().getOutput().getText());
    }

    // 非流式的核心方法
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logRequest(chatClientRequest);
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        logResponse(chatClientResponse);
        return chatClientResponse;
    }

    // 流式的核心方法
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        logRequest(chatClientRequest);
        Flux<ChatClientResponse> chatClientResponseFlux = streamAdvisorChain.nextStream(chatClientRequest);
        return (new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponseFlux, this::logResponse));
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return order;
    }

    public String toString() {
        return llongLoggerAdvisor.class.getSimpleName();
    }
}
