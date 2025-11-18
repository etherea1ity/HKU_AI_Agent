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
 * REST controller exposing simple file download and listing endpoints.
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    /**
     * Download an exported file saved by the agent.
     *
     * @param fileName Target filename
     * @param type     Subdirectory/type (pdf, txt, html, etc.)
     * @return File contents as a streamed response
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam String fileName,
            @RequestParam(required = false, defaultValue = "pdf") String type) {
        
        try {
            // Build the on-disk path
            String fileDir = FileConstant.FILE_SAVE_DIR + "/" + type;
            String filePath = fileDir + "/" + fileName;
            File file = new File(filePath);
            
            // Validate file presence
            if (!file.exists() || !file.isFile()) {
                log.error("File not found: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            // Ensure the resolved path stays within the allowed directory (basic security check)
            String canonicalPath = file.getCanonicalPath();
            String canonicalBaseDir = new File(fileDir).getCanonicalPath();
            if (!canonicalPath.startsWith(canonicalBaseDir)) {
                log.error("Illegal file path access attempt: {}", filePath);
                return ResponseEntity.badRequest().build();
            }
            
            // Create a resource handle
            Resource resource = new FileSystemResource(file);
            
            // Determine the Content-Type header
            MediaType mediaType = getMediaType(type);
            
            // URL-encode filenames so browsers handle special characters correctly
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            
            // Return the download response
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                    .body(resource);
            
        } catch (Exception e) {
            log.error("Failed to download file: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Resolve the appropriate MediaType for a given file extension.
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
     * List files for the provided type subdirectory.
     *
     * @param type Subdirectory/type filter
     * @return Array of filenames
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
            log.error("Failed to list files", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

