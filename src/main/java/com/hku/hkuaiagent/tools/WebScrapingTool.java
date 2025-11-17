package com.hku.hkuaiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    @Tool(description = "Scrape the content of a web page and return readable text")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document document = Jsoup.connect(url).get();
            String title = document.title();
            String bodyText = document.body() != null ? document.body().text() : "";

            if (bodyText.length() > 3000) {
                bodyText = bodyText.substring(0, 3000) + "...";
            }

            StringBuilder builder = new StringBuilder();
            if (!title.isBlank()) {
                builder.append("页面标题: ").append(title).append('\n');
            }
            builder.append("摘录内容: ").append(bodyText);
            builder.append("\n\n原始链接: ").append(url);

            return builder.toString();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}

