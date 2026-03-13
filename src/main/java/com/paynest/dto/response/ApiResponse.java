package com.paynest.dto.response;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.HashMap;
import java.util.Map;

public class ApiResponse {

    private String status;
    private String code;
    private String message;

    private Map<String, Object> body = new HashMap<>();

    public ApiResponse(String status,
                              String code,
                              String message,
                              String fieldName,
                              Object data) {

        this.status = status;
        this.code = code;
        this.message = message;
        body.put(fieldName, data);
    }


    public ApiResponse(String status,
                       String message,
                       String fieldName,
                       Object data) {

        this.status = status;
        this.message = message;
        body.put(fieldName, data);
    }

    public String getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @JsonAnyGetter
    public Map<String, Object> getBody() {
        return body;
    }
}