package com.tzl.llongagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/***
 * 自定义的DocumentReader,这里为了和官方的加载器进行区分,取名为DocumentLoader
 * 恋爱大师应用文档加载器
 */
@Slf4j
@Component
public class PlanAppDocumentLoader {

    // 同时处理多个文档
    private final ResourcePatternResolver resourcePatternResolver;

    // Spring IoC自动注入参数，此参数是启动类加载器在启动时加载的
    public PlanAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<Document> loadMarkDown() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:docs/*.md");
            log.info("=== 开始加载所有 md 文档，总数量：{} ===", resources.length);
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                if (fileName == null) {
                    log.warn("跳过空文件名的资源: {}", resource);
                    continue;
                }
                log.info("正在处理文档：{}", fileName);
                try {
                    MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                            .withHorizontalRuleCreateDocument(true)
                            .withIncludeBlockquote(false)
                            .withIncludeCodeBlock(false)
                            .withAdditionalMetadata("fileName", fileName)
                            .build();

                    MarkdownDocumentReader markdownDocumentReader =
                            new MarkdownDocumentReader(resource, config);
                    List<Document> docs = markdownDocumentReader.get();
                    allDocuments.addAll(docs);
                    log.info("成功加载文档: {}, 分块数: {}", fileName, docs.size());
                } catch (Exception e) {
                    log.error("加载文档失败: {}, 原因: {}", fileName, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.error("扫描 docs 目录失败", e);
        }
        log.info("文档加载完成, 总计加载 {} 个文档块", allDocuments.size());
        return allDocuments;
    }

}
