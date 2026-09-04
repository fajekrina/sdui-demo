package com.sdui.demo.controller;

import com.sdui.demo.model.ActionRequest;
import com.sdui.demo.model.PageConfig;
import com.sdui.demo.service.PuckMuiPageConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Separate client API for PuckMUI — isolated from SDUI /api/client.
 * Base: /api/puckmui/client | storage: data/puckmui/pages
 */
@RestController
@RequestMapping("/api/puckmui/client")
public class PuckMuiClientController {

    private final PuckMuiPageConfigService pageConfigService;

    @Autowired
    public PuckMuiClientController(PuckMuiPageConfigService pageConfigService) {
        this.pageConfigService = pageConfigService;
    }

    @GetMapping("/page/{merchantId}/{pageKey}")
    public ResponseEntity<?> getPublishedPage(@PathVariable String merchantId,
                                              @PathVariable String pageKey) {
        try {
            PageConfig config = pageConfigService.getPublishedPage(merchantId, pageKey);
            if (config == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error",
                                "Published PuckMUI page not found: " + pageKey +
                                        ". It may not exist or is not published."));
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load puckmui page: " + e.getMessage()));
        }
    }

    @PostMapping("/action/submit")
    public ResponseEntity<?> submitAction(@RequestBody(required = false) ActionRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "ActionRequest body is required"));
            }
            if (request.getAction() == null || request.getAction().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "action is required"));
            }
            Map<String, Object> result = pageConfigService.handleAction(
                    request.getAction(),
                    request.getData(),
                    request.getParams());
            boolean success = Boolean.TRUE.equals(result.get("success"));
            return ResponseEntity.status(success ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process puckmui action: " + e.getMessage()));
        }
    }
}
