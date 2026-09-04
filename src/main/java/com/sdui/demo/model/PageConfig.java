package com.sdui.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageConfig {

    private String pageId;
    private String pageKey;
    private String merchantId;
    private String status; // DRAFT / PUBLISHED / ARCHIVED
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, Object> layout;
}
