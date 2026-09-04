package com.sdui.demo.controller;

import com.sdui.demo.model.PageConfig;
import com.sdui.demo.service.PageConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final PageConfigService pageConfigService;

    @Autowired
    public MerchantController(PageConfigService pageConfigService) {
        this.pageConfigService = pageConfigService;
    }

    @GetMapping("/pages/{merchantId}")
    public ResponseEntity<?> listPages(@PathVariable String merchantId) {
        try {
            List<String> pages = pageConfigService.listPages(merchantId);
            return ResponseEntity.ok(Map.of(
                    "merchantId", merchantId,
                    "pages", pages,
                    "count", pages.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list pages: " + e.getMessage()));
        }
    }

    @GetMapping("/pages/{merchantId}/{pageKey}")
    public ResponseEntity<?> getPage(@PathVariable String merchantId,
                                     @PathVariable String pageKey) {
        try {
            PageConfig config = pageConfigService.getPage(merchantId, pageKey);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Page not found: " + pageKey));
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get page: " + e.getMessage()));
        }
    }

    @PostMapping("/pages/{merchantId}/{pageKey}")
    public ResponseEntity<?> savePage(@PathVariable String merchantId,
                                      @PathVariable String pageKey,
                                      @RequestBody(required = false) PageConfig pageConfig) {
        try {
            if (pageConfig == null) {
                pageConfig = new PageConfig();
            }
            PageConfig saved = pageConfigService.savePage(merchantId, pageKey, pageConfig);
            return ResponseEntity.ok(Map.of(
                    "message", "Page saved", "page", saved));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save page: " + e.getMessage()));
        }
    }

    @PostMapping("/pages/{merchantId}/{pageKey}/publish")
    public ResponseEntity<?> publishPage(@PathVariable String merchantId,
                                         @PathVariable String pageKey) {
        try {
            PageConfig published = pageConfigService.publishPage(merchantId, pageKey);
            if (published == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Page not found: " + pageKey));
            }
            return ResponseEntity.ok(Map.of(
                    "message", "Page published", "page", published));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to publish page: " + e.getMessage()));
        }
    }

    @DeleteMapping("/pages/{merchantId}/{pageKey}")
    public ResponseEntity<?> deletePage(@PathVariable String merchantId,
                                        @PathVariable String pageKey) {
        try {
            boolean deleted = pageConfigService.deletePage(merchantId, pageKey);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Page not found: " + pageKey));
            }
            return ResponseEntity.ok(Map.of(
                    "message", "Page deleted", "pageKey", pageKey));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete page: " + e.getMessage()));
        }
    }
}
