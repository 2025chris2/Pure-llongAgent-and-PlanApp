package com.tzl.llongagent.app;

import com.tzl.llongagent.advisor.ReReadingAdvisor;
import com.tzl.llongagent.advisor.llongLoggerAdvisor;
import com.tzl.llongagent.chatmemoryrepository.RedisChatMemoryRepository;
import com.tzl.llongagent.rag.PlanAppQueryRewrite;
import com.tzl.llongagent.rag.PlanAppRAGCustomAdvisor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

@Component
@Slf4j
public class PlanApp {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private PlanAppQueryRewrite queryRewrite;

    // 本应用自己维护一个ChatClient，此ChatClient在构造函数中进行初始化
    private final ChatClient deepseekChatClient;



    public PlanApp(DeepSeekChatModel deepSeekChatModel,
                   RedisChatMemoryRepository redisChatMemoryRepository) {

        String SYSTEM_PROMPT = """
                你是一位资深旅行定制师，你叫EternalChristmas,需根据用户输入生成一份详尽的旅行报告。请遵守以下规则：
                1. **信息核验**：先确认用户已经提供了哪些信息（目的地、出行日期、天数）。如果目的地已提供，要在回复中明确呼应（如”泰山是个好选择！”），只追问缺失的信息。如果用户没有提供出行日期（至少精确到月份）和天数，必须主动、友好地提问，直到补全这些信息再生成报告。切勿让用户重复已经说过的信息。
                2. **报告标题**：格式为 “{地点}旅行指南 - {月份/日期} {天数}日行程” ，例如 “华清池旅行指南 - 1月3日-4日两日行程”。
                3. **报告内容**：必须包含以下部分，且每部分都要展开具体细节：
                  - **季节性贴士**：根据出行月份，给出当地天气特征、穿衣指数、必备物品（防晒/雨具/羽绒服等）。
                  - **每日详细行程**：按上午、中午、下午、晚上拆分，注明大概时间点、景点游览时长、交通衔接方式和耗时。
                  - **深度推荐**：不只列名字，要说明这地方为什么值得去、拍照机位、导览预约方式、历史彩蛋等。
                  - **美食清单**：附上推荐店铺名、人均消费、招牌菜和位置，区分景区附近和市区。
                  - **住宿方案**：给出不同价位的具体酒店/区域建议，说明优缺点。
                  - **预算参考**：给出门票、交通、餐饮、住宿的大致费用区间。
                  - **注意事项**：结合季节和地点的特殊提醒（如冬季演出停演、山路防滑等）。
                4. **语言风格**：亲切、实用，像朋友在给你做行程，避免干巴巴的列表。
                """;

        // ReReadingAdvisor
        ReReadingAdvisor reReadingAdvisor = new ReReadingAdvisor();

        // 日志打印顾问
        llongLoggerAdvisor llongLoggerAdvisor = new llongLoggerAdvisor();

        // 聊天记忆存储仓库
        ChatMemory redisChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(20)
                .build();

        MessageChatMemoryAdvisor chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(redisChatMemory)
                .build();

        deepseekChatClient = ChatClient.builder(deepSeekChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                // 由于有Re2的顾问，很重，所以这里只演示，不使用
//                .defaultAdvisors(reReadingAdvisor, chatMemoryAdvisor, llongLoggerAdvisor)
                // 较轻的 顾问链
                .defaultAdvisors(chatMemoryAdvisor, llongLoggerAdvisor)
                .build();


    }


    public String doChat(String userMessage, String conversationId) {
        String content = deepseekChatClient.prompt()
                .user(userMessage)
                .advisors(
                        spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId)
                )
                .call()
                .content();
        log.info("寻觅的Content: {}", content);
        return content;
    }

    // 添加public属性，让别的包也能访问
    public record PlanReport(String title, List<String> suggestions){

    }

    public PlanReport doChatWithReport(String userMessage, String conversationId) {
        com.tzl.llongagent.app.PlanApp.PlanReport report = deepseekChatClient.prompt()
                .system("生成:{地点}旅行报告,报告内容：行程建议列表")
                .user(userMessage)
                .advisors(
                        spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId)
                )
                .call()
                .entity(PlanReport.class);
        log.info("寻觅的PlanReport: {}", report);
        return report;
    }

    public String doChatWithPgSql(String userMessage, String conversationId){

        // 查询重写:重写是重写用户的提问，所以这里在用户发送消息前，进行改写
//        String reWrittenQuery = queryRewrite.doQueryRewrite(userMessage);

        ChatResponse chatResponse = deepseekChatClient.prompt()
                .user(userMessage)
                .advisors(PlanAppRAGCustomAdvisor.createPlanAppRAGCustomAdvisor(pgVectorVectorStore))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        Assert.notNull(chatResponse, "pgVector 调用 AI 服务返回异常: chatResponse is null");
        Assert.notNull(chatResponse.getResult(), "pgVector 调用 AI 服务返回异常: result is null");
        String content = chatResponse.getResult().getOutput().getText();
        log.info("pgVector 调用的 content: {}", content);
        return content;
    }

    public String doChatWithTools(String userMessage, String conversationId) {

        ChatResponse chatResponse = deepseekChatClient.prompt()
                .user(userMessage)
                .toolCallbacks(allTools)
                .advisors(
                        spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId)
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;

    }

}
