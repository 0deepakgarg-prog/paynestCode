package com.paynest.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermissionDto {
    private Long permissionId;
    private String permissionCode;
    private String module;
    private String description;
    private LocalDateTime createdAt;
}
