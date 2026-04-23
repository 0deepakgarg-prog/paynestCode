package com.paynest.tag.service;

import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.tag.dto.request.AddTagRequest;
import com.paynest.tag.dto.response.AccountTagResponse;
import com.paynest.tag.dto.response.CategoryResponse;
import com.paynest.tag.dto.response.TagResponse;
import com.paynest.tag.dto.response.TagAccountResponse;
import com.paynest.tag.dto.response.TagTypeResponse;
import com.paynest.tag.dto.response.UserTagResponse;
import com.paynest.tag.entity.Category;
import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.TagType;
import com.paynest.tag.entity.UserTag;
import com.paynest.tag.repository.CategoryRepository;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.TagTypeRepository;
import com.paynest.tag.repository.UserTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TagService {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final TagTypeRepository tagTypeRepository;
    private final UserTagRepository userTagRepository;

    @Transactional
    public TagResponse addTag(AddTagRequest request) {
        String normalizedTagCode = normalizeTagCode(request.getTagCode());
        String normalizedCategory = normalizeOptionalCode(request.getCategory());
        String normalizedTagType = normalizeOptionalCode(request.getTagType());

        tagRepository.findByTagCode(normalizedTagCode)
                .ifPresent(existingTag -> {
                    throw new ApplicationException(ErrorCodes.TAG_ALREADY_EXISTS, "Tag already exists");
                });

        if (normalizedCategory != null) {
            categoryRepository.findByCategoryCode(normalizedCategory)
                    .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_REQUEST, "Category does not exist"));
        }

        if (normalizedTagType != null) {
            tagTypeRepository.findByTypeCode(normalizedTagType)
                    .orElseThrow(() -> new ApplicationException(ErrorCodes.INVALID_REQUEST, "Tag type does not exist"));
        }

        Tag tag = new Tag();
        tag.setTagCode(normalizedTagCode);
        tag.setTagName(request.getTagName().trim());
        tag.setCategory(normalizedCategory);
        tag.setIsDefault(Boolean.FALSE);
        tag.setTagType(normalizedTagType);
        tag.setStatus("ACTIVE");

        return new TagResponse(tagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(Long tagId) {
        Tag tag = getTag(tagId);
        userTagRepository.deleteByTagId(tag.getTagId());
        tagRepository.delete(tag);
    }

    @Transactional
    public UserTagResponse linkTagToAccount(Long tagId, String accountId) {
        Tag tag = getTag(tagId);
        if (!"ACTIVE".equalsIgnoreCase(tag.getStatus())) {
            throw new ApplicationException(ErrorCodes.TAG_INACTIVE, "Tag is inactive");
        }

        userTagRepository.findByAccountIdAndTagId(accountId, tagId)
                .ifPresent(existingLink -> {
                    throw new ApplicationException(ErrorCodes.TAG_LINK_ALREADY_EXISTS, "Tag is already linked to account");
                });

        UserTag userTag = new UserTag();
        userTag.setAccountId(accountId);
        userTag.setTagId(tagId);
        userTag.setCreatedBy(JWTUtils.getCurrentAccountId());

        return new UserTagResponse(userTagRepository.save(userTag));
    }

    @Transactional
    public void unlinkTagFromAccount(Long tagId, String accountId) {
        getTag(tagId);
        UserTag userTag = userTagRepository.findByAccountIdAndTagId(accountId, tagId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.TAG_LINK_NOT_FOUND, "Tag is not linked to account"));

        userTagRepository.delete(userTag);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TagTypeResponse> getAllTagTypes() {
        return tagTypeRepository.findAll().stream()
                .map(TagTypeResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TagResponse> getAllTags() {
        return tagRepository.findAll().stream()
                .map(TagResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountTagResponse> getTagsForAccount(String accountId) {
        return userTagRepository.findByAccountId(accountId).stream()
                .map(userTag -> new AccountTagResponse(userTag, getTag(userTag.getTagId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TagAccountResponse> getAccountsForTag(Long tagId) {
        getTag(tagId);
        return userTagRepository.findByTagId(tagId).stream()
                .map(TagAccountResponse::new)
                .toList();
    }

    private Tag getTag(Long tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new ApplicationException(ErrorCodes.TAG_NOT_FOUND, "Tag not found"));
    }

    private String normalizeTagCode(String tagCode) {
        return tagCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
