package com.tzl.llongagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PgVectorVectorStoreConfig {

    @Resource
    private PlanAppDocumentLoader documentLoader;

    @Resource
    private PlanAppTextSplitter textSplitter;

    @Bean
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {

        PgVectorStore pgVectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("vector_store")
                .maxDocumentBatchSize(10)
                .build();

        // 检查向量表是否已有数据，避免每次启动重复向量化
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store", Integer.class);
        if (rowCount != null && rowCount > 0) {
            log.info("向量表已有 {} 条数据，跳过文档加载与向量化", rowCount);
            return pgVectorStore;
        }

        List<Document> documents = documentLoader.loadMarkDown();
        log.info("文档加载完成，块数: {}", documents.size());

        // 过滤空内容文档，避免向量化失败
        List<Document> validDocuments = documents.stream()
                .filter(doc -> doc.getText() != null && !doc.getText().trim().isEmpty())
                .toList();
        int emptyCount = documents.size() - validDocuments.size();
        if (emptyCount > 0) {
            log.warn("过滤掉 {} 个空内容文档块", emptyCount);
        }

        List<Document> splitDocuments = textSplitter.splitDocuments(validDocuments);
        log.info("文档分割完成，总块数: {}", splitDocuments.size());

        int batchSize = 10;
        int maxRetries = 3;
        int successCount = 0;

        for (int i = 0; i < splitDocuments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, splitDocuments.size());
            List<Document> batch = splitDocuments.subList(i, end);
            int batchIndex = i / batchSize + 1;

            if (embedBatchWithRetry(pgVectorStore, batch, maxRetries, batchIndex)) {
                successCount += batch.size();
            }

            // 批次间延迟，避免触发 embedding API 限流
            if (end < splitDocuments.size()) {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("批次间等待被中断，停止向量化");
                    break;
                }
            }
        }

        log.info("向量化完成: {}/{} 个文档块成功嵌入", successCount, splitDocuments.size());
        return pgVectorStore;
    }

    private boolean embedBatchWithRetry(PgVectorStore store, List<Document> batch, int maxRetries, int batchIndex) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                store.add(batch);
                log.info("批次 {} 嵌入成功 ({} 个文档)", batchIndex, batch.size());
                return true;
            } catch (Exception e) {
                log.error("批次 {} 嵌入失败 (第 {} 次尝试): {}", batchIndex, attempt, e.getMessage());
                if (attempt < maxRetries) {
                    long waitMs = (long) Math.pow(2, attempt) * 1000L;
                    log.info("等待 {}ms 后重试...", waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        log.error("批次 {} 重试 {} 次后仍然失败，跳过该批次", batchIndex, maxRetries);
        return false;
    }

}
