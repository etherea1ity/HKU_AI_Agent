package com.hku.hkuaiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Simple web scraping utility that returns readable text for the agent.
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
                builder.append("Page title: ").append(title).append('\n');
            }
            builder.append("Excerpt: ").append(bodyText);
            builder.append("\n\nOriginal link: ").append(url);

            return builder.toString();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}

