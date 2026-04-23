package com.paynest.tag.controller;

import com.paynest.common.ErrorCodes;
import com.paynest.config.dto.response.ApiResponse;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.tag.dto.request.AddTagRequest;
import com.paynest.tag.dto.response.AccountTagResponse;
import com.paynest.tag.dto.response.CategoryResponse;
import com.paynest.tag.dto.response.TagResponse;
import com.paynest.tag.dto.response.TagAccountResponse;
import com.paynest.tag.dto.response.TagTypeResponse;
import com.paynest.tag.dto.response.UserTagResponse;
import com.paynest.tag.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllTags() {
        validateAdminScope();
        List<TagResponse> response = tagService.getAllTags();
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Tags fetched successfully", "tags", response));
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<ApiResponse> getTagsForAccount(@PathVariable String accountId) {
        validateAdminScope();
        List<AccountTagResponse> response = tagService.getTagsForAccount(accountId);
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Account tags fetched successfully", "accountTags", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addTag(@Valid @RequestBody AddTagRequest request) {
        validateAdminScope();
        TagResponse response = tagService.addTag(request);
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Tag created successfully", "tag", response));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse> getAllCategories() {
        validateAdminScope();
        List<CategoryResponse> response = tagService.getAllCategories();
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Categories fetched successfully", "categories", response));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse> getAllTagTypes() {
        validateAdminScope();
        List<TagTypeResponse> response = tagService.getAllTagTypes();
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Tag types fetched successfully", "tagTypes", response));
    }

    @GetMapping("/{tagId}/accounts")
    public ResponseEntity<ApiResponse> getAccountsForTag(@PathVariable Long tagId) {
        validateAdminScope();
        List<TagAccountResponse> response = tagService.getAccountsForTag(tagId);
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Tag accounts fetched successfully", "tagAccounts", response));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<?> deleteTag(@PathVariable Long tagId) {
        validateAdminScope();
        tagService.deleteTag(tagId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Tag deleted successfully"));
    }

    @PostMapping("/{tagId}/accounts/{accountId}")
    public ResponseEntity<ApiResponse> linkTagToAccount(@PathVariable Long tagId, @PathVariable String accountId) {
        validateAdminScope();
        UserTagResponse response = tagService.linkTagToAccount(tagId, accountId);
        return ResponseEntity.ok(new ApiResponse("SUCCESS", "Tag linked successfully", "accountTag", response));
    }

    @DeleteMapping("/{tagId}/accounts/{accountId}")
    public ResponseEntity<?> unlinkTagFromAccount(@PathVariable Long tagId, @PathVariable String accountId) {
        validateAdminScope();
        tagService.unlinkTagFromAccount(tagId, accountId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Tag unlinked successfully"));
    }

    private void validateAdminScope() {
        if (!"ADMIN".equalsIgnoreCase(JWTUtils.getCurrentAccountType())) {
           // throw new ApplicationException(ErrorCodes.INVALID_PRIVILEGES, "Token does not have necessary access");
        }
    }
}
