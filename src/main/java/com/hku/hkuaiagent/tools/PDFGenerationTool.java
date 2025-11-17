package com.hku.hkuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.hku.hkuaiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PDF 生成工具（支持中文、Markdown风格）
 */
@Slf4j
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with the given content. Supports Chinese and Markdown-style formatting. Returns a downloadable URL.", 
          returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Content to be included in the PDF. Supports Markdown-style formatting with headers (#, ##, ###), bold (**text**), and line breaks.") 
            String content,
            @ToolParam(description = "Optional title for the PDF document. If not provided, will use 'Document' as default.", required = false) 
            String title) {
        
        // 生成唯一文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = IdUtil.simpleUUID().substring(0, 8);
        String fileName = "pdf_" + timestamp + "_" + uniqueId + ".pdf";
        
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                
                // 使用支持中文的字体
                PdfFont font;
                PdfFont boldFont;
                try {
                    // 尝试使用内置的中文字体（需要 itext7-font-asian 依赖）
                    font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                    boldFont = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                    log.info("成功加载中文字体 STSongStd-Light");
                } catch (Exception e) {
                    log.warn("无法加载 STSongStd-Light，尝试使用系统字体: {}", e.getMessage());
                    try {
                        // 尝试使用 Windows 系统字体
                        String fontPath = "C:/Windows/Fonts/simhei.ttf"; // 黑体
                        font = PdfFontFactory.createFont(fontPath, PdfEncodings.IDENTITY_H);
                        boldFont = PdfFontFactory.createFont(fontPath, PdfEncodings.IDENTITY_H);
                        log.info("成功加载系统字体: {}", fontPath);
                    } catch (Exception e2) {
                        log.error("无法加载任何中文字体，使用默认字体（不支持中文）: {}", e2.getMessage());
                        // 如果都失败，使用Helvetica作为后备（不支持中文）
                        font = PdfFontFactory.createFont("Helvetica", PdfEncodings.WINANSI);
                        boldFont = PdfFontFactory.createFont("Helvetica-Bold", PdfEncodings.WINANSI);
                    }
                }
                
                document.setFont(font);
                
                // 添加标题
                if (title != null && !title.isEmpty()) {
                    Paragraph titlePara = new Paragraph(title)
                            .setFont(boldFont)
                            .setFontSize(20)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(20)
                            .setFontColor(ColorConstants.BLUE);
                    document.add(titlePara);
                }
                
                // 添加生成时间
                String timeStr = "生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                Paragraph timePara = new Paragraph(timeStr)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.GRAY)
                        .setMarginBottom(20);
                document.add(timePara);
                
                // 解析并添加内容（支持简单的Markdown格式）
                parseAndAddContent(document, content, font, boldFont);
                
                log.info("PDF生成成功: {}", filePath);
            }
            
            // 返回可下载的URL（对文件名进行URL编码以支持中文）
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            String downloadUrl = "http://localhost:8123/api/file/download?fileName=" + encodedFileName + "&type=pdf";
            
            // 返回带有特殊标记的格式，前端会将其转换为下载按钮
            return String.format("✅ PDF生成成功！\n\n📄 文件名: %s\n\n[DOWNLOAD_LINK]%s[/DOWNLOAD_LINK]", 
                    fileName, downloadUrl);
            
        } catch (Exception e) {
            log.error("生成PDF失败", e);
            return "❌ 生成PDF失败: " + e.getMessage();
        }
    }
    
    /**
     * 解析内容并添加到文档（支持简单的Markdown格式）
     */
    private void parseAndAddContent(Document document, String content, PdfFont normalFont, PdfFont boldFont) throws IOException {
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                // 空行，添加间距
                document.add(new Paragraph(" ").setMarginBottom(5));
                continue;
            }
            
            Paragraph paragraph = new Paragraph();
            
            // 处理标题
            if (line.startsWith("### ")) {
                paragraph.add(new Text(line.substring(4))
                        .setFont(boldFont)
                        .setFontSize(14)
                        .setFontColor(ColorConstants.DARK_GRAY));
                paragraph.setMarginTop(10).setMarginBottom(8);
            } else if (line.startsWith("## ")) {
                paragraph.add(new Text(line.substring(3))
                        .setFont(boldFont)
                        .setFontSize(16)
                        .setFontColor(ColorConstants.BLACK));
                paragraph.setMarginTop(12).setMarginBottom(10);
            } else if (line.startsWith("# ")) {
                paragraph.add(new Text(line.substring(2))
                        .setFont(boldFont)
                        .setFontSize(18)
                        .setFontColor(ColorConstants.BLUE));
                paragraph.setMarginTop(15).setMarginBottom(12);
            } else {
                // 处理粗体文本 **text**
                String processedLine = line;
                while (processedLine.contains("**")) {
                    int start = processedLine.indexOf("**");
                    int end = processedLine.indexOf("**", start + 2);
                    if (end != -1) {
                        // 添加粗体前的文本
                        if (start > 0) {
                            paragraph.add(new Text(processedLine.substring(0, start)).setFont(normalFont));
                        }
                        // 添加粗体文本
                        paragraph.add(new Text(processedLine.substring(start + 2, end)).setFont(boldFont));
                        processedLine = processedLine.substring(end + 2);
                    } else {
                        break;
                    }
                }
                // 添加剩余文本
                if (!processedLine.isEmpty()) {
                    paragraph.add(new Text(processedLine).setFont(normalFont));
                }
                paragraph.setMarginBottom(6);
            }
            
            document.add(paragraph);
        }
    }
}

