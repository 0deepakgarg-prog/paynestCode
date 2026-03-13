package com.paynest.dto;

import lombok.Data;

@Data
public class RolePermissionDto {
    private Long id;
    private Long roleId;
    private Long permissionId;
}
