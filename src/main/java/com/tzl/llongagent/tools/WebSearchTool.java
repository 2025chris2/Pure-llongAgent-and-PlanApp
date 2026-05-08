package com.tzl.llongagent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

public class WebSearchTool {

    private final String TAVILY_API_URL;

    private final String API_KEY;

    public WebSearchTool(String key) {
        TAVILY_API_URL = "https://api.tavily.com/search";
        API_KEY = key;
    }


    @Tool(description = "Search for information from Tavily")
    public String doWebSearch(
            @ToolParam(description = "Search query keyword") String query
    ) {

        // 1. 构造 POST 请求体（Tavily 要求 JSON body）
        JSONObject body = JSONUtil.createObj()
                .set("api_key", API_KEY)
                .set("query", query)
                .set("search_depth", "basic")
                .set("max_results", 5)
                .set("include_answer", true)
                .set("include_raw_content", false)
                .set("include_images", false);

        try(HttpResponse resp = HttpRequest.post(TAVILY_API_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .body(body.toString())
                .timeout(30000)
                .execute();) {
            if(!resp.isOk())
                throw new RuntimeException("Tavily HTTP" + resp.getStatus());

            // 解析响应
            JSONObject root = JSONUtil.parseObj(resp.body());
            JSONArray results = root.getJSONArray("results");

            // 截取前7条
            int limit = Math.min(results.size(), 5);
            List<Object> topResults = results.subList(0, limit);

            // 格式化为 AI 友好的文本
            return topResults.stream().map(obj -> {
                JSONObject item = (JSONObject) obj;
                return String.format(
                        "标题: %s\n链接:%s\n摘要: %s\n",
                        item.getStr("title"),
                        item.getStr("url"),
                        item.getStr("content")
                );
            }).collect(Collectors.joining("\n---\n"));
        } catch(Exception e) {
            // 抛出异常让上层处理，而不是返回错误字符串
            throw new RuntimeException("Tavily 搜索失败: " + e.getMessage());
        }
    }
}
