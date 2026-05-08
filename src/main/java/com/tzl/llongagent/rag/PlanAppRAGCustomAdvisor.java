package com.tzl.llongagent.rag;

import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.aop.Advisor;

import java.util.Vector;

/***
 * 自定义的向量检索器
 *
 * 工厂模式,所以不用 @Component和 @Bean
 */
public class PlanAppRAGCustomAdvisor {

    // 创建 Advisor
    public static RetrievalAugmentationAdvisor createPlanAppRAGCustomAdvisor(VectorStore vectorStore) {

        // 过滤特定状态的文档
//        Filter.Expression expression = new FilterExpressionBuilder()
//                .eq("status", status)
//                .build();

        // 向量化的文件检索器
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
//                .filterExpression(expression)
                .topK(3)
                .similarityThreshold(0.3)
                .vectorStore(vectorStore)
                .build();

        // 这里用较为灵活的RetrieverAugmentationAdvisor顾问
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(PlanAppContextualQueryAugmenterFactory.createInstance())
                .build();

    }

}
