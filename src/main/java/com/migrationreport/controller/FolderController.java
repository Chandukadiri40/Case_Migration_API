package com.migrationreport.controller;

import com.migrationreport.service.FolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
@CrossOrigin(origins = "*")
public class FolderController {

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

        byte[] data = folderService.getFileBytes(path);
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
            // Inline viewer
            MediaType mediaType = resolveMediaType(ext);
            headers.setContentType(mediaType);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
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
            case "xml":
                return MediaType.TEXT_XML;
            case "json":
                return MediaType.APPLICATION_JSON;
            case "csv":
            case "txt":
            case "log":
            default:
                return MediaType.TEXT_PLAIN;
        }
    }
}
