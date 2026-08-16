package com.migrationreport.controller;

import com.migrationreport.service.FolderService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller exposing real-time directory listing and document streaming for any MIME type.
 */
@Slf4j
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * Returns default Linux configuration parameters from application.properties.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getDefaultConfig() {
        return ResponseEntity.ok(folderService.getDefaultConfig());
    }

    /**
     * Browses files and directories at the requested path.
     */
    @GetMapping("/browse")
    public ResponseEntity<Map<String, Object>> browseDirectory(
            @RequestParam(value = "path", required = false) String path) {
        log.info("[FolderController] Received browse request for path: {}", path);
        Map<String, Object> result = folderService.listDirectory(path);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .body(result);
    }

    /**
     * Streams document content for ANY MIME type directly for in-browser viewing or download with exact filename.
     */
    @GetMapping("/view")
    public ResponseEntity<Resource> viewDocument(
            @RequestParam("path") String filePath,
            @RequestParam(value = "download", defaultValue = "false") boolean download) {
        log.info("[FolderController] View document request for path: {}, download={}", filePath, download);
        Resource resource = folderService.getFileResource(filePath);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        String fileName = "document";
        if (filePath != null && !filePath.isEmpty()) {
            int lastSep = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
            fileName = (lastSep >= 0) ? filePath.substring(lastSep + 1) : filePath;
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".xml") || lowerName.endsWith(".json") || lowerName.endsWith(".log") || lowerName.endsWith(".txt") || lowerName.endsWith(".csv")) {
            mediaType = MediaType.TEXT_PLAIN;
        }

        String dispositionType = download ? "attachment" : "inline";

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + fileName + "\"")
                .body(resource);
    }
}
