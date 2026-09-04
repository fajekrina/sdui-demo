package com.sdui.demo.controller;

import com.sdui.demo.model.PageConfig;
import com.sdui.demo.service.PuckMuiPageConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Separate merchant API for PuckMUI — isolated from SDUI /api/merchant.
 * Base: /api/puckmui/merchant | storage: data/puckmui/pages
 */
@RestController
@RequestMapping("/api/puckmui/merchant")
public class PuckMuiMerchantController {

    private final PuckMuiPageConfigService pageConfigService;

    @Autowired
    public PuckMuiMerchantController(PuckMuiPageConfigService pageConfigService) {
        this.pageConfigService = pageConfigService;
    }

    @GetMapping("/pages/{merchantId}")
    public ResponseEntity<?> listPages(@PathVariable String merchantId) {
        try {
            List<String> pages = pageConfigService.listPages(merchantId);
            return ResponseEntity.ok(Map.of(
                    "merchantId", merchantId,
                    "pages", pages,
                    "count", pages.size(),
                    "source", "puckmui"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list puckmui pages: " + e.getMessage()));
        }
    }

    @GetMapping("/pages/{merchantId}/{pageKey}")
    public ResponseEntity<?> getPage(@PathVariable String merchantId,
                                     @PathVariable String pageKey) {
        try {
            PageConfig config = pageConfigService.getPage(merchantId, pageKey);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "PuckMUI page not found: " + pageKey));
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get puckmui page: " + e.getMessage()));
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
                    "message", "PuckMUI page saved", "page", saved));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save puckmui page: " + e.getMessage()));
        }
    }

    @PostMapping("/pages/{merchantId}/{pageKey}/publish")
    public ResponseEntity<?> publishPage(@PathVariable String merchantId,
                                         @PathVariable String pageKey) {
        try {
            PageConfig published = pageConfigService.publishPage(merchantId, pageKey);
            if (published == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "PuckMUI page not found: " + pageKey));
            }
            return ResponseEntity.ok(Map.of(
                    "message", "PuckMUI page published", "page", published));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to publish puckmui page: " + e.getMessage()));
        }
    }

    @DeleteMapping("/pages/{merchantId}/{pageKey}")
    public ResponseEntity<?> deletePage(@PathVariable String merchantId,
                                        @PathVariable String pageKey) {
        try {
            boolean deleted = pageConfigService.deletePage(merchantId, pageKey);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "PuckMUI page not found: " + pageKey));
            }
            return ResponseEntity.ok(Map.of(
                    "message", "PuckMUI page deleted", "pageKey", pageKey));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete puckmui page: " + e.getMessage()));
        }
    }
}
