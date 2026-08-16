package com.migrationreport.controller;

import com.migrationreport.service.FolderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@CrossOrigin(originPatterns = "*")
public class FolderController {

    private static final Logger log = LoggerFactory.getLogger(FolderController.class);

    @Autowired
    private FolderService folderService;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(folderService.getConfig());
    }

    @GetMapping("/browse")
    public ResponseEntity<Map<String, Object>> browseFolder(@RequestParam(value = "path", required = false) String path) {
        return ResponseEntity.ok(folderService.listDirectory(path));
    }

    @GetMapping("/view")
    public ResponseEntity<byte[]> viewOrDownloadFile(
            @RequestParam("path") String path,
            @RequestParam(value = "download", defaultValue = "false") boolean download) {

        byte[] data = folderService.getProcessedFileBytes(path);
        String fileName = new File(path).getName();
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            ext = fileName.substring(dot + 1).toLowerCase();
        }

        HttpHeaders headers = new HttpHeaders();

        if (download) {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        } else {
            // Inline viewer (TIFF is converted on-the-fly to PNG by TwelveMonkeys ImageIO)
            MediaType mediaType = ("tif".equals(ext) || "tiff".equals(ext)) ? MediaType.IMAGE_PNG : resolveMediaType(ext);
            headers.setContentType(mediaType);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }

    /**
     * Resolves and streams document content by docId prefix for Search Docs integration.
     */
    @GetMapping("/resolve-by-docid")
    public ResponseEntity<byte[]> viewDocumentByDocId(
            @RequestParam("docId") String docId,
            @RequestParam(value = "download", defaultValue = "false") boolean download) {
        log.info("[FolderController] Resolve document by docId request: {}, download={}", docId, download);
        String resolvedPath = folderService.resolveFileByDocId(docId);
        if (resolvedPath == null) {
            return ResponseEntity.notFound().build();
        }
        return viewOrDownloadFile(resolvedPath, download);
    }

    private MediaType resolveMediaType(String ext) {
        switch (ext) {
            case "pdf":
                return MediaType.APPLICATION_PDF;
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            case "tif":
            case "tiff":
                return MediaType.parseMediaType("image/tiff");
            case "webp":
                return MediaType.parseMediaType("image/webp");
            case "xml":
                return MediaType.TEXT_XML;
            case "json":
                return MediaType.APPLICATION_JSON;
            case "xlsx":
            case "xlsm":
            case "xls":
                return MediaType.parseMediaType("application/vnd.ms-excel");
            case "docx":
            case "doc":
                return MediaType.parseMediaType("application/msword");
            case "mtc":
            case "cls":
            case "csv":
            case "txt":
            case "log":
            default:
                return MediaType.TEXT_PLAIN;
        }
    }
}
