package com.tzl.llongagent.tools;


import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/***
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${tavily.apikey}")
    private String api_key;

    @Bean
    public ToolCallback[] allTools(){

        // 建立工具的对象实例
        FileOperationTool fileOperationTool = new FileOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        WebSearchTool webSearchTool = new WebSearchTool(api_key);
        TerminateTool terminateTool = new TerminateTool();

        // ToolCallbacks: 把一系列的普通的对象，转化成工具，但是对象必须要有@Tool注解
        // 把注册的工具返回
        return ToolCallbacks.from(
                fileOperationTool,
                pdfGenerationTool,
                resourceDownloadTool,
                terminateTool,
                webSearchTool,
                webScrapingTool,
                terminalOperationTool
        );
    }

}
