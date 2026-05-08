package com.tzl.llongagent.chatmemoryrepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 Redis List 的 {@link ChatMemoryRepository} 实现，提供用户隔离的对话记忆持久化。
 *
 * <h3>Spring AI 集成</h3>
 * <p>
 * 本类实现标准 {@link ChatMemoryRepository} 接口，可无缝替换默认的
 * {@code InMemoryChatMemoryRepository}。Spring AI 的 {@code ChatMemory}
 * 自动配置会通过 {@code @ConditionalOnMissingBean} 检测到本实现并注入。
 * </p>
 *
 * <h3>用户隔离机制</h3>
 * <p>
 * 以 {@code userId:actualId} 格式的<strong>复合 conversationId</strong> 实现隔离。
 * Spring AI 的 {@code ChatMemory} / {@code ChatClient} 接口方法不带 userId 参数，
 * 通过复合 ID 将 userId 编码进 conversationId：
 * </p>
 * <pre>{@code
 * // 拼接复合 ID 传给 Spring AI
 * String compositeId = RedisChatMemoryRepository.compositeId("user123", "chat456");
 * chatMemory.add(compositeId, message);
 *
 * // 也可直接调用 userId 感知方法，读写同一份 Redis 数据
 * repository.findByConversationId("user123", "chat456");    // 等价
 * repository.appendMessage("user123", "chat456", message);   // O(1) 追加
 * }</pre>
 *
 * <h3>性能说明</h3>
 * <p>
 * {@code MessageWindowChatMemory} 的 {@code add()} 采用<strong>读-改-写</strong>模式
 * （先 {@code findByConversationId} 全量读取，追加后再 {@code saveAll} 全量写回）。
 * 对于大对话存在 O(n) 开销。本类额外提供 {@link # appendMessage} 方法实现 O(1) 追加，
 * 如需极致性能可自定义 {@code ChatMemory} 实现直接调用。
 * </p>
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>强制用户隔离：userId 参与 Redis key 和 compositeId，防止数据越权</li>
 *   <li>高性能序列化：Jackson JSON 序列化</li>
 *   <li>自动过期：支持 TTL，默认 7 天（168 小时）</li>
 *   <li>安全 Key 设计：sanitize 过滤非法字符，防 Redis Key 注入</li>
 *   <li>Redis List 存储：每条消息独立序列化为一个 List 元素，追加无需反序列化历史</li>
 *   <li>SCAN 替代 KEYS：避免阻塞 Redis</li>
 * </ul>
 *
 * <h3>Redis Key 设计</h3>
 * <pre>
 *   {prefix}:{userId}:{conversationId}  → Redis List
 *   例如: chat:memory:user123:chat456   → [JSON(Message1), JSON(Message2), ...]
 * </pre>
 *
 * @see ChatMemoryRepository
 */
@Slf4j
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    /** Redis 操作模板，String 键 + byte[] 值序列化器 */
    private final RedisTemplate<String, byte[]> redisTemplate;

    /** Redis Key 前缀，默认 {@code chat:memory} */
    private final String keyPrefix;

    /** 默认 TTL（秒），由配置项 {@code spring.ai.chat.memory.redis.ttl-hours} 转换而来 */
    private final long defaultTtlSeconds;

    /**
     * Jackson ObjectMapper，配置多态类型序列化以支持 Message 接口。
     * <p>
     * {@code activateDefaultTyping} 会在 JSON 中嵌入 {@code @class} 类型信息，
     * 确保反序列化时能正确实例化具体的 Message 实现类。
     * </p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .activateDefaultTyping(
                    BasicPolymorphicTypeValidator.builder()
                            .allowIfBaseType(Message.class)
                            .build(),
                    ObjectMapper.DefaultTyping.NON_FINAL
            );

    /**
     * 构造 RedisChatMemoryRepository。
     *
     * @param redisTemplate    Redis 操作模板（需配置 {@code StringRedisSerializer} 键序列化器
     *                         和 {@code ByteArrayRedisSerializer} 值序列化器）
     * @param keyPrefix        Redis Key 前缀，默认 {@code chat:memory}
     * @param defaultTtlHours  默认 TTL 小时数，默认 168（7 天）
     */
    public RedisChatMemoryRepository(
            RedisTemplate<String, byte[]> redisTemplate,
            @Value("${spring.ai.chat.memory.redis.key-prefix:chat:memory}") String keyPrefix,
            @Value("${spring.ai.chat.memory.redis.ttl-hours:168}") long defaultTtlHours) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.defaultTtlSeconds = TimeUnit.HOURS.toSeconds(defaultTtlHours);
        log.info("RedisChatMemoryRepository 初始化完成，key前缀: {}, TTL: {}小时", keyPrefix, defaultTtlHours);
    }


    // ==================== 核心 Key 生成方法 ====================

    /**
     * 构建 Redis Key。
     * <p>
     * 格式: {@code {prefix}:{sanitize(userId)}:{sanitize(conversationId)}}
     * </p>
     *
     * @param userId         用户标识
     * @param conversationId 对话标识
     * @return 安全过滤后的 Redis Key
     * @throws IllegalArgumentException userId 或 conversationId 为空时抛出
     */
    private String buildKey(String userId, String conversationId) {
        return keyPrefix + ":" + sanitize(userId) + ":" + sanitize(conversationId);
    }

    /**
     * 构建用户维度的 Redis Key SCAN 模式。
     * <p>
     * 格式: {@code {prefix}:{sanitize(userId)}:*}
     * </p>
     *
     * @param userId 用户标识
     * @return Redis SCAN 匹配模式
     */
    private String buildUserPattern(String userId) {
        return keyPrefix + ":" + sanitize(userId) + ":*";
    }

    /**
     * 对输入字符串做安全过滤，防止 Redis Key 注入。
     * <p>
     * 只保留字母、数字、下划线、连字符，其余字符替换为 {@code _}。
     * 空值或空白字符串直接抛异常。
     * </p>
     *
     * @param input 原始输入
     * @return 过滤后的安全字符串
     * @throws IllegalArgumentException 输入为 null 或空白时抛出
     */
    private static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("userId 和 conversationId 不能为空");
        }
        return input.replaceAll("[^a-zA-Z0-9\\-_]", "_");
    }


    // ==================== 用户隔离方法 ====================

    /**
     * 获取指定用户的所有对话 ID。
     * <p>
     * 通过 SCAN 命令扫描 {@code {prefix}:{userId}:*} 并提取 conversationId 部分。
     * 返回的 ID 为<strong>原始 conversationId</strong>（不含 userId 前缀），
     * 如需传给 Spring AI 接口方法，请使用 {@link #compositeId} 拼接。
     * </p>
     *
     * @param userId 用户标识
     * @return 对话 ID 列表，无记录时返回空列表
     */
    public List<String> findConversationIds(String userId) {
        Set<String> keys = scanKeys(buildUserPattern(userId));
        return extractConversationIds(keys);
    }

    /**
     * 获取用户指定对话的全部消息。
     * <p>
     * 通过 LRANGE 命令从 Redis List 拉取所有元素并反序列化。
     * 若 key 不存在返回空列表。
     * </p>
     *
     * @param userId         用户标识
     * @param conversationId 对话标识
     * @return 消息列表（按插入顺序），无记录时返回空列表
     */
    public List<Message> findByConversationId(String userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        List<byte[]> rawList = redisTemplate.opsForList().range(key, 0, -1);
        return deserializeMessages(rawList);
    }

    /**
     * 保存用户对话的全部消息。
     * <p>
     * 通过 {@code LLEN} 比较新旧消息数，自动选择最优写入策略：
     * </p>
     * <ul>
     *   <li><strong>增量追加</strong>：新列表长度 &gt; 当前长度且当前非空 → 仅 RPUSH 尾部新增消息</li>
     *   <li><strong>全量覆写</strong>：新建对话 / 消息被截断 / 等长覆写 → DEL + RPUSH + EXPIRE</li>
     * </ul>
     * <p>
     * Spring AI 的 {@code MessageWindowChatMemory.add()} 按读-改-写模式工作，
     * 在未触及 {@code maxMessages} 上限时触发增量追加路径（O(1) 写入），
     * 触及上限后回退全量覆写。
     * </p>
     *
     * @param userId         用户标识
     * @param conversationId 对话标识
     * @param messages       要保存的消息列表（覆盖写入）
     */
    public void saveAll(String userId, String conversationId, List<Message> messages) {
        String key = buildKey(userId, conversationId);
        int incoming = messages.size();

        // 判断当前 Redis List 长度，区分全量覆写 vs 增量追加
        Long currentSize = redisTemplate.opsForList().size(key);
        int current = (currentSize == null) ? 0 : currentSize.intValue();

        if (incoming > current && current > 0) {
            // 纯追加场景：新列表是旧列表 + 尾部新增，只需 RPUSH 增量部分
            List<Message> newMessages = messages.subList(current, incoming);
            appendToKey(key, serializeMessages(newMessages));
            log.debug("增量追加对话, key: {}, 新增: {} 条, 总计: {} 条", key, newMessages.size(), incoming);
        } else {
            // 新建 或 截断 或 等长覆写：全量重建 List
            overwriteKey(key, serializeMessages(messages));
            log.debug("全量覆写对话, key: {}, 消息数: {}", key, incoming);
        }
    }


    /**
     * 删除用户的指定对话。
     *
     * @param userId         用户标识
     * @param conversationId 对话标识
     */
    public void deleteByConversationId(String userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("删除用户对话成功, user: {}, conversation: {}", userId, conversationId);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 检查对话是否存在。
     *
     * @param userId         用户标识
     * @param conversationId 对话标识
     * @return {@code true} 对话存在，{@code false} 不存在
     */
    public boolean exists(String userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }


    // ==================== 私有工具方法 ====================

    /**
     * 从一组 Redis Key 中提取 conversationId 部分。
     * <p>
     * 假设 key 格式为 {@code ...:conversationId}，取最后一个 {@code :} 之后的内容。
     * </p>
     *
     * @param keys Redis Key 集合
     * @return conversationId 列表
     */
    private List<String> extractConversationIds(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }
        return keys.stream()
                .map(key -> key.substring(key.lastIndexOf(':') + 1))
                .collect(Collectors.toList());
    }

    /**
     * 全量覆写 Key：Pipeline 执行 DEL + RPUSH × N + EXPIRE。
     * <p>
     * 适用于新对话首次写入或消息被截断后的全量替换场景。
     * </p>
     *
     * @param key        目标 Redis Key
     * @param serialized 序列化后的完整消息列表
     */
    private void overwriteKey(String key, List<byte[]> serialized) {
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            connection.keyCommands().del(keyBytes);
            for (byte[] data : serialized) {
                connection.listCommands().rPush(keyBytes, data);
            }
            connection.keyCommands().expire(keyBytes, defaultTtlSeconds);
            return null;
        });
        checkPipelineResults(key, results);
    }

    /**
     * 增量追加到 Key 尾部：Pipeline 执行 RPUSH × N + EXPIRE。
     * <p>
     * 不删已有数据，仅向 List 尾部追加新元素并刷新 TTL。
     * </p>
     *
     * @param key        目标 Redis Key（必须已存在）
     * @param serialized 序列化后的新增消息列表
     */
    private void appendToKey(String key, List<byte[]> serialized) {
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            for (byte[] data : serialized) {
                connection.listCommands().rPush(keyBytes, data);
            }
            connection.keyCommands().expire(keyBytes, defaultTtlSeconds);
            return null;
        });
        checkPipelineResults(key, results);
    }

    /** 检查 Pipeline 执行结果，记录失败的命令。 */
    private void checkPipelineResults(String key, List<Object> results) {
        for (int i = 0; i < results.size(); i++) {
            Object result = results.get(i);
            if (result instanceof Throwable) {
                log.error("Pipeline 命令失败, key: {}, index: {}", key, i, (Throwable) result);
            }
        }
    }

    /**
     * 使用 Redis SCAN 命令渐进式扫描匹配的 Key。
     * <p>
     * 替代 KEYS 命令避免阻塞 Redis 服务端。每次迭代约返回 100 条记录。
     * SCAN 不保证强一致性，可能遗漏或重复扫描期间的变更。
     * </p>
     *
     * @param pattern Redis glob 匹配模式
     * @return 匹配的 Key 集合，扫描异常时返回空集合
     */
    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    /**
     * 使用 Jackson 序列化单条 Message。
     *
     * @param message 待序列化的消息
     * @return 序列化后的 JSON 字节数组
     * @throws RuntimeException 序列化失败时抛出
     */
    private byte[] serializeMessage(Message message) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(message);
        } catch (Exception e) {
            log.error("序列化消息失败", e);
            throw new RuntimeException("序列化消息失败", e);
        }
    }

    /**
     * 批量序列化消息列表。
     *
     * @param messages 消息列表
     * @return 序列化后的字节数组列表，列表大小与输入一致
     */
    private List<byte[]> serializeMessages(List<Message> messages) {
        List<byte[]> result = new ArrayList<>(messages.size());
        for (Message message : messages) {
            result.add(serializeMessage(message));
        }
        return result;
    }

    /**
     * 批量反序列化消息。
     * <p>
     * 单条消息损坏不会影响其他消息：损坏的消息被跳过并记录错误日志。
     * </p>
     *
     * @param rawList JSON 字节数组列表
     * @return 反序列化后的消息列表，输入为 null 或空时返回空列表
     */
    private List<Message> deserializeMessages(List<byte[]> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return new ArrayList<>();
        }
        List<Message> messages = new ArrayList<>(rawList.size());
        for (byte[] data : rawList) {
            try {
                Message message = OBJECT_MAPPER.readValue(data, Message.class);
                if (message != null) {
                    messages.add(message);
                }
            } catch (Exception e) {
                log.error("反序列化消息失败，跳过该消息", e);
            }
        }
        return messages;
    }


    // ==================== 复合 ID 工具方法 ====================

    /**
     * 拼接复合 conversationId，用于 Spring AI 标准接口。
     * <p>
     * 格式: {@code sanitize(userId):sanitize(conversationId)}。
     * 输入会经过安全过滤（非法字符替换为 {@code _}），确保 {@code :} 分割点唯一。
     * </p>
     * <p>
     * 此方法同时也会被内部委托调用以保证与 Redis Key 的一致性。
     * </p>
     *
     * @param userId         用户标识
     * @param conversationId 对话标识
     * @return 复合 conversationId，可安全用于 Spring AI 的 ChatMemory/ChatClient
     */
    public static String compositeId(String userId, String conversationId) {
        return sanitize(userId) + ":" + sanitize(conversationId);
    }

    /**
     * 从复合 conversationId 中解析 [userId, actualConversationId]。
     * <p>
     * 按<strong>第一个</strong> {@code :} 分割。由于 {@link #compositeId} 已对两部分
     * 做 sanitize 处理，{@code :} 不会出现在 userId 或 conversationId 内部。
     * </p>
     *
     * @param conversationId 由 {@link #compositeId} 生成的复合 ID
     * @return 长度为 2 的数组: [userId, conversationId]
     * @throws IllegalArgumentException 格式不合法（不含 {@code :}）时抛出
     */
    private static String[] parseCompositeId(String conversationId) {
        int idx = conversationId.indexOf(':');
        if (idx <= 0) {
            throw new IllegalArgumentException(
                    "conversationId 格式必须为 userId:actualId，收到: " + conversationId);
        }
        return new String[] { conversationId.substring(0, idx), conversationId.substring(idx + 1) };
    }

    /**
     * 从 Redis Key 提取复合 conversationId。
     * <p>
     * Redis Key 格式: {@code prefix:userId:actualId}，去掉 {@code prefix:} 前缀后
     * 剩余部分即为 {@code userId:actualId} 形式的复合 ID。
     * </p>
     *
     * @param key 完整的 Redis Key
     * @return {@code userId:actualId} 格式的复合 conversationId
     */
    private String compositeIdFromKey(String key) {
        return key.substring(keyPrefix.length() + 1);
    }


    // ==================== ChatMemoryRepository 接口方法 ====================

    /**
     * {@inheritDoc}
     * <p>
     * 返回所有用户的复合 conversationId（{@code userId:actualId} 格式）。
     * 扫描模式: {@code {prefix}:*:*}。
     * </p>
     */
    @Override
    public List<String> findConversationIds() {
        Set<String> keys = scanKeys(keyPrefix + ":*:*");
        return keys.stream()
                .map(this::compositeIdFromKey)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code conversationId} 需为 {@link #compositeId} 生成的复合格式。
     * 内部解析出 userId 和 actualConversationId 后委托到
     * {@link #findByConversationId(String, String)}。
     * </p>
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        String[] parts = parseCompositeId(conversationId);
        return findByConversationId(parts[0], parts[1]);
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code conversationId} 需为 {@link #compositeId} 生成的复合格式。
     * 内部解析出 userId 和 actualConversationId 后委托到
     * {@link #saveAll(String, String, List)}。
     * </p>
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String[] parts = parseCompositeId(conversationId);
        saveAll(parts[0], parts[1], messages);
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code conversationId} 需为 {@link #compositeId} 生成的复合格式。
     * 内部解析出 userId 和 actualConversationId 后委托到
     * {@link #deleteByConversationId(String, String)}。
     * </p>
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        String[] parts = parseCompositeId(conversationId);
        deleteByConversationId(parts[0], parts[1]);
    }
}
