package com.sdui.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionRequest {

    private String action;
    private String endpoint;
    private String target;
    private Map<String, Object> data;
    private Map<String, Object> params;
}
