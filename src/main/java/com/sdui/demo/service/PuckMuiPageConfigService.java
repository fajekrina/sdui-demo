package com.sdui.demo.service;

import com.sdui.demo.model.PageConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Separate service for PuckMUI — isolated from SDUI PageConfigService.
 * Uses PuckMuiJsonFileUtils (data/puckmui/pages) and its own cache.
 */
@Service
public class PuckMuiPageConfigService {

    private final PuckMuiJsonFileUtils jsonFileUtils;
    private final Map<String, PageConfig> cache = new ConcurrentHashMap<>();

    @Autowired
    public PuckMuiPageConfigService(PuckMuiJsonFileUtils jsonFileUtils) {
        this.jsonFileUtils = jsonFileUtils;
    }

    private String cacheKey(String merchantId, String pageKey) {
        return "puckmui::" + merchantId + "::" + pageKey;
    }

    public List<String> listPages(String merchantId) throws IOException {
        return jsonFileUtils.listPages(merchantId);
    }

    public PageConfig getPage(String merchantId, String pageKey) throws IOException {
        String key = cacheKey(merchantId, pageKey);
        // always read file to avoid stale cache after file edits (was causing swapped gallery_small ↔ 100)
        PageConfig config = jsonFileUtils.readFile(merchantId, pageKey, PageConfig.class);
        if (config != null) {
            cache.put(key, config);
        } else {
            cache.remove(key);
        }
        return config;
    }

    public PageConfig savePage(String merchantId, String pageKey, PageConfig incoming) throws IOException {
        PageConfig existing = getPage(merchantId, pageKey);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new PageConfig();
            existing.setPageId(merchantId + "-" + pageKey);
            existing.setCreatedAt(now);
            existing.setVersion(1L);
            existing.setStatus("DRAFT");
        } else {
            existing.setVersion((existing.getVersion() == null ? 0L : existing.getVersion()) + 1);
        }
        if (incoming.getLayout() != null) {
            existing.setLayout(incoming.getLayout());
        }
        if (incoming.getStatus() != null && !incoming.getStatus().isBlank()) {
            existing.setStatus(incoming.getStatus());
        }
        if (incoming.getPageId() != null && !incoming.getPageId().isBlank()) {
            existing.setPageId(incoming.getPageId());
        }
        existing.setMerchantId(merchantId);
        existing.setPageKey(pageKey);
        existing.setUpdatedAt(now);
        jsonFileUtils.writeFile(merchantId, pageKey, existing);
        cache.put(cacheKey(merchantId, pageKey), existing);
        return existing;
    }

    public PageConfig publishPage(String merchantId, String pageKey) throws IOException {
        PageConfig config = getPage(merchantId, pageKey);
        if (config == null) {
            return null;
        }
        config.setStatus("PUBLISHED");
        config.setUpdatedAt(LocalDateTime.now());
        jsonFileUtils.writeFile(merchantId, pageKey, config);
        cache.put(cacheKey(merchantId, pageKey), config);
        return config;
    }

    public boolean deletePage(String merchantId, String pageKey) throws IOException {
        String key = cacheKey(merchantId, pageKey);
        cache.remove(key);
        return jsonFileUtils.deleteFile(merchantId, pageKey);
    }

    public PageConfig getPublishedPage(String merchantId, String pageKey) throws IOException {
        PageConfig config = getPage(merchantId, pageKey);
        if (config == null) {
            return null;
        }
        if (!"PUBLISHED".equalsIgnoreCase(config.getStatus())) {
            return null;
        }
        return config;
    }

    public void clearCache() {
        cache.clear();
    }

    public Map<String, Object> handleAction(String action, Map<String, Object> data, Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        result.put("action", action);
        result.put("receivedAt", LocalDateTime.now().toString());
        // Reuse same actions as SDUI for consistency
        if ("SEAT_SELECTION".equalsIgnoreCase(action) || "selectSeat".equalsIgnoreCase(action)) {
            Object seatId = data != null ? data.get("seatId") : null;
            Object section = data != null ? data.get("section") : null;
            if (seatId == null || seatId.toString().isBlank()) {
                result.put("success", false);
                result.put("error", "seatId is required");
                return result;
            }
            result.put("success", true);
            result.put("seatId", seatId);
            result.put("section", section);
            result.put("message", "Seat " + seatId + " reserved successfully");
            return result;
        }
        if ("CHECKOUT".equalsIgnoreCase(action) || "submitCheckout".equalsIgnoreCase(action)) {
            Object email = data != null ? data.get("email") : null;
            Object fullName = data != null ? data.get("fullName") : null;
            boolean ok = email != null && !email.toString().isBlank()
                    && fullName != null && !fullName.toString().isBlank();
            result.put("success", ok);
            result.put("message", ok ? "Checkout submitted" : "email and fullName are required");
            result.put("data", data);
            return result;
        }
        if ("PAYMENT".equalsIgnoreCase(action) || "submitPayment".equalsIgnoreCase(action)) {
            Object amount = data != null ? data.get("amount") : null;
            Object method = data != null ? data.get("method") : null;
            boolean ok = amount != null && method != null;
            result.put("success", ok);
            if (!ok) {
                result.put("error", "amount and method are required");
            } else {
                result.put("message", "Payment of " + amount + " via " + method + " processed");
            }
            return result;
        }
        result.put("success", true);
        result.put("message", "Action '" + action + "' processed (puckmui)");
        result.put("data", data);
        result.put("params", params);
        return result;
    }
}
