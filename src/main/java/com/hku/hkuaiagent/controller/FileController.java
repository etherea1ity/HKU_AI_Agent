package com.hku.hkuaiagent.controller;

import com.hku.hkuaiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件操作控制器
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    /**
     * 文件下载接口
     *
     * @param fileName 文件名
     * @param type     文件类型（pdf, txt, html等）
     * @return 文件流
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam String fileName,
            @RequestParam(required = false, defaultValue = "pdf") String type) {
        
        try {
            // 构建文件路径
            String fileDir = FileConstant.FILE_SAVE_DIR + "/" + type;
            String filePath = fileDir + "/" + fileName;
            File file = new File(filePath);
            
            // 检查文件是否存在
            if (!file.exists() || !file.isFile()) {
                log.error("文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            // 检查文件是否在允许的目录下（安全检查）
            String canonicalPath = file.getCanonicalPath();
            String canonicalBaseDir = new File(fileDir).getCanonicalPath();
            if (!canonicalPath.startsWith(canonicalBaseDir)) {
                log.error("非法文件路径访问: {}", filePath);
                return ResponseEntity.badRequest().build();
            }
            
            // 创建资源
            Resource resource = new FileSystemResource(file);
            
            // 确定Content-Type
            MediaType mediaType = getMediaType(type);
            
            // 对文件名进行URL编码（支持中文文件名）
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            
            // 返回文件
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                    .body(resource);
            
        } catch (Exception e) {
            log.error("文件下载失败: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 根据文件类型获取MediaType
     */
    private MediaType getMediaType(String type) {
        return switch (type.toLowerCase()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt" -> MediaType.TEXT_PLAIN;
            case "html" -> MediaType.TEXT_HTML;
            case "json" -> MediaType.APPLICATION_JSON;
            case "xml" -> MediaType.APPLICATION_XML;
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
    
    /**
     * 列出指定类型的所有文件
     *
     * @param type 文件类型
     * @return 文件列表
     */
    @GetMapping("/list")
    public ResponseEntity<String[]> listFiles(@RequestParam(required = false, defaultValue = "pdf") String type) {
        try {
            String fileDir = FileConstant.FILE_SAVE_DIR + "/" + type;
            File dir = new File(fileDir);
            
            if (!dir.exists() || !dir.isDirectory()) {
                return ResponseEntity.ok(new String[0]);
            }
            
            String[] files = dir.list();
            return ResponseEntity.ok(files != null ? files : new String[0]);
            
        } catch (Exception e) {
            log.error("列出文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

