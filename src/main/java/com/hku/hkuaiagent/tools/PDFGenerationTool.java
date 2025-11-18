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
 * PDF generation tool supporting Chinese content and lightweight Markdown syntax.
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
        
        // Generate a unique filename to avoid collisions
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = IdUtil.simpleUUID().substring(0, 8);
        String fileName = "pdf_" + timestamp + "_" + uniqueId + ".pdf";
        
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        
        try {
            // Ensure the output directory exists
            FileUtil.mkdir(fileDir);
            
            // Create PdfWriter and PdfDocument handles
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                
                // Load fonts that support Chinese characters when possible
                PdfFont font;
                PdfFont boldFont;
                try {
                    // Attempt to load bundled Chinese fonts (requires itext7-font-asian)
                    font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                    boldFont = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                    log.info("Successfully loaded Chinese font STSongStd-Light");
                } catch (Exception e) {
                    log.warn("Unable to load STSongStd-Light, trying a system font: {}", e.getMessage());
                    try {
                        // Fallback to a Windows system font
                        String fontPath = "C:/Windows/Fonts/simhei.ttf"; // SimHei
                        font = PdfFontFactory.createFont(fontPath, PdfEncodings.IDENTITY_H);
                        boldFont = PdfFontFactory.createFont(fontPath, PdfEncodings.IDENTITY_H);
                        log.info("Successfully loaded system font: {}", fontPath);
                    } catch (Exception e2) {
                        log.error("Unable to load any Chinese-capable font, falling back to Helvetica: {}", e2.getMessage());
                        // Final fallback: Helvetica (does not support Chinese characters)
                        font = PdfFontFactory.createFont("Helvetica", PdfEncodings.WINANSI);
                        boldFont = PdfFontFactory.createFont("Helvetica-Bold", PdfEncodings.WINANSI);
                    }
                }
                
                document.setFont(font);
                
                // Optional document title
                if (title != null && !title.isEmpty()) {
                    Paragraph titlePara = new Paragraph(title)
                            .setFont(boldFont)
                            .setFontSize(20)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(20)
                            .setFontColor(ColorConstants.BLUE);
                    document.add(titlePara);
                }
                
                // Creation timestamp for traceability
                String timeStr = "Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                Paragraph timePara = new Paragraph(timeStr)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.GRAY)
                        .setMarginBottom(20);
                document.add(timePara);
                
                // Parse and render the Markdown-like content
                parseAndAddContent(document, content, font, boldFont);
                
                log.info("PDF generated successfully: {}", filePath);
            }
            
            // Build a downloadable URL (URL-encode to handle non-ASCII names)
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            String downloadUrl = "http://localhost:8123/api/file/download?fileName=" + encodedFileName + "&type=pdf";
            
            // Return the special marker format recognised by the frontend for download buttons
            return String.format("✅ PDF generated successfully!\n\n📄 File name: %s\n\n[DOWNLOAD_LINK]%s[/DOWNLOAD_LINK]", 
                    fileName, downloadUrl);
            
        } catch (Exception e) {
            log.error("Failed to generate PDF", e);
            return "❌ Failed to generate PDF: " + e.getMessage();
        }
    }
    
    /**
     * Parse the incoming text and add Markdown-like sections to the PDF document.
     */
    private void parseAndAddContent(Document document, String content, PdfFont normalFont, PdfFont boldFont) throws IOException {
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                // Blank line → add spacing paragraph
                document.add(new Paragraph(" ").setMarginBottom(5));
                continue;
            }
            
            Paragraph paragraph = new Paragraph();
            
            // Headers
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
                // Handle bold text **text**
                String processedLine = line;
                while (processedLine.contains("**")) {
                    int start = processedLine.indexOf("**");
                    int end = processedLine.indexOf("**", start + 2);
                    if (end != -1) {
                        // Plain text before the bold section
                        if (start > 0) {
                            paragraph.add(new Text(processedLine.substring(0, start)).setFont(normalFont));
                        }
                        // Bold portion
                        paragraph.add(new Text(processedLine.substring(start + 2, end)).setFont(boldFont));
                        processedLine = processedLine.substring(end + 2);
                    } else {
                        break;
                    }
                }
                // Remaining text (no more bold markers)
                if (!processedLine.isEmpty()) {
                    paragraph.add(new Text(processedLine).setFont(normalFont));
                }
                paragraph.setMarginBottom(6);
            }
            
            document.add(paragraph);
        }
    }
}

