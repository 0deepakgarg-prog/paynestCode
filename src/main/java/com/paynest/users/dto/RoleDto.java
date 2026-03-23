package com.paynest.users.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleDto {
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String roleType;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}

