package com.paynest.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserRoleDto {
    private Long id;
    private Long userId;
    private Long roleId;
    private String assignedBy;
    private LocalDateTime assignedAt;
}
