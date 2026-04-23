package com.paynest.tag.service;

import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.exception.ApplicationException;
import com.paynest.tag.dto.request.AddTagRequest;
import com.paynest.tag.dto.response.AccountTagResponse;
import com.paynest.tag.dto.response.TagResponse;
import com.paynest.tag.dto.response.TagAccountResponse;
import com.paynest.tag.dto.response.UserTagResponse;
import com.paynest.tag.entity.Category;
import com.paynest.tag.entity.Tag;
import com.paynest.tag.entity.TagType;
import com.paynest.tag.entity.UserTag;
import com.paynest.tag.repository.CategoryRepository;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.TagTypeRepository;
import com.paynest.tag.repository.UserTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagTypeRepository tagTypeRepository;

    @Mock
    private UserTagRepository userTagRepository;

    @InjectMocks
    private TagService tagService;

    @Test
    void addTag_shouldCreateTagWithUppercaseCode() {
        AddTagRequest request = new AddTagRequest();
        request.setTagCode("vip");
        request.setTagName("VIP");
        request.setCategory("customer");
        request.setTagType("base");

        when(tagRepository.findByTagCode("VIP")).thenReturn(Optional.empty());
        when(categoryRepository.findByCategoryCode("CUSTOMER")).thenReturn(Optional.of(new Category()));
        when(tagTypeRepository.findByTypeCode("BASE")).thenReturn(Optional.of(new TagType()));
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tag.setTagId(1L);
            tag.setCreatedAt(LocalDateTime.now());
            tag.setUpdatedAt(LocalDateTime.now());
            tag.setStatus("ACTIVE");
            return tag;
        });

        TagResponse response = tagService.addTag(request);

        assertEquals(1L, response.getTagId());
        assertEquals("VIP", response.getTagCode());
        assertEquals("VIP", response.getTagName());
        assertEquals("CUSTOMER", response.getCategory());
        assertEquals(Boolean.FALSE, response.getIsDefault());
        assertEquals("BASE", response.getTagType());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void addTag_shouldRejectDuplicateTagCode() {
        AddTagRequest request = new AddTagRequest();
        request.setTagCode("premium");
        request.setTagName("Premium");
        request.setCategory("customer");

        Tag existingTag = new Tag();
        existingTag.setTagId(10L);
        existingTag.setTagCode("PREMIUM");

        when(tagRepository.findByTagCode("PREMIUM")).thenReturn(Optional.of(existingTag));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> tagService.addTag(request));

        assertEquals(ErrorCodes.TAG_ALREADY_EXISTS, exception.getErrorCode());
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    void addTag_shouldAllowMissingCategoryAndTagType() {
        AddTagRequest request = new AddTagRequest();
        request.setTagCode("merchant");
        request.setTagName("Merchant");

        when(tagRepository.findByTagCode("MERCHANT")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tag.setTagId(2L);
            tag.setCreatedAt(LocalDateTime.now());
            tag.setUpdatedAt(LocalDateTime.now());
            tag.setStatus("ACTIVE");
            return tag;
        });

        TagResponse response = tagService.addTag(request);

        assertEquals(2L, response.getTagId());
        assertEquals("MERCHANT", response.getTagCode());
        assertEquals(null, response.getCategory());
        assertEquals(null, response.getTagType());
    }

    @Test
    void addTag_shouldRejectUnknownCategory() {
        AddTagRequest request = new AddTagRequest();
        request.setTagCode("vip");
        request.setTagName("VIP");
        request.setCategory("unknown");

        when(tagRepository.findByTagCode("VIP")).thenReturn(Optional.empty());
        when(categoryRepository.findByCategoryCode("UNKNOWN")).thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(ApplicationException.class, () -> tagService.addTag(request));

        assertEquals(ErrorCodes.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("Category does not exist", exception.getErrorMessage());
    }

    @Test
    void addTag_shouldRejectUnknownTagType() {
        AddTagRequest request = new AddTagRequest();
        request.setTagCode("vip");
        request.setTagName("VIP");
        request.setCategory("customer");
        request.setTagType("unknown");

        when(tagRepository.findByTagCode("VIP")).thenReturn(Optional.empty());
        when(categoryRepository.findByCategoryCode("CUSTOMER")).thenReturn(Optional.of(new Category()));
        when(tagTypeRepository.findByTypeCode("UNKNOWN")).thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(ApplicationException.class, () -> tagService.addTag(request));

        assertEquals(ErrorCodes.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("Tag type does not exist", exception.getErrorMessage());
    }

    @Test
    void linkTagToAccount_shouldCreateLinkForActiveTag() {
        Tag tag = new Tag();
        tag.setTagId(5L);
        tag.setTagCode("VIP");
        tag.setStatus("ACTIVE");

        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(userTagRepository.findByAccountIdAndTagId("101", 5L)).thenReturn(Optional.empty());
        when(userTagRepository.save(any(UserTag.class))).thenAnswer(invocation -> {
            UserTag userTag = invocation.getArgument(0);
            userTag.setId(100L);
            userTag.setCreatedAt(LocalDateTime.now());
            userTag.setStatus("ACTIVE");
            return userTag;
        });

        try (MockedStatic<JWTUtils> jwtUtils = mockStatic(JWTUtils.class)) {
            jwtUtils.when(JWTUtils::getCurrentAccountId).thenReturn("ADMIN001");

            UserTagResponse response = tagService.linkTagToAccount(5L, "101");

            assertEquals(100L, response.getId());
            assertEquals("101", response.getAccountId());
            assertEquals(5L, response.getTagId());
            assertEquals("ADMIN001", response.getCreatedBy());
            assertEquals("ACTIVE", response.getStatus());
            assertNotNull(response.getCreatedAt());
        }
    }

    @Test
    void unlinkTagFromAccount_shouldRejectMissingLink() {
        Tag tag = new Tag();
        tag.setTagId(5L);

        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(userTagRepository.findByAccountIdAndTagId("101", 5L)).thenReturn(Optional.empty());

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> tagService.unlinkTagFromAccount(5L, "101")
        );

        assertEquals(ErrorCodes.TAG_LINK_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getAllTags_shouldReturnMappedTags() {
        Tag tag = new Tag();
        tag.setTagId(1L);
        tag.setTagCode("VIP");
        tag.setTagName("VIP");
        tag.setCategory("CUSTOMER");
        tag.setIsDefault(Boolean.FALSE);
        tag.setTagType("BASE");
        tag.setStatus("ACTIVE");

        when(tagRepository.findAll()).thenReturn(List.of(tag));

        List<TagResponse> response = tagService.getAllTags();

        assertEquals(1, response.size());
        assertEquals(1L, response.get(0).getTagId());
        assertEquals("VIP", response.get(0).getTagCode());
        assertEquals("CUSTOMER", response.get(0).getCategory());
        assertEquals("ACTIVE", response.get(0).getStatus());
    }

    @Test
    void getTagsForAccount_shouldReturnMappedTagsForAccount() {
        UserTag userTag = new UserTag();
        userTag.setId(11L);
        userTag.setAccountId("ACC001");
        userTag.setTagId(1L);
        userTag.setStatus("ACTIVE");
        userTag.setCreatedAt(LocalDateTime.now());
        userTag.setCreatedBy("ADMIN001");

        Tag tag = new Tag();
        tag.setTagId(1L);
        tag.setTagCode("VIP");
        tag.setTagName("VIP");
        tag.setCategory("CUSTOMER");
        tag.setIsDefault(Boolean.FALSE);
        tag.setTagType("BASE");
        tag.setStatus("ACTIVE");

        when(userTagRepository.findByAccountId("ACC001")).thenReturn(List.of(userTag));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

        List<AccountTagResponse> response = tagService.getTagsForAccount("ACC001");

        assertEquals(1, response.size());
        assertEquals("ACC001", response.get(0).getAccountId());
        assertEquals(1L, response.get(0).getTagId());
        assertEquals("VIP", response.get(0).getTagCode());
    }

    @Test
    void getAccountsForTag_shouldReturnMappedAccountsForTag() {
        Tag tag = new Tag();
        tag.setTagId(1L);

        UserTag userTag = new UserTag();
        userTag.setId(11L);
        userTag.setAccountId("ACC001");
        userTag.setTagId(1L);
        userTag.setStatus("ACTIVE");
        userTag.setCreatedAt(LocalDateTime.now());
        userTag.setCreatedBy("ADMIN001");

        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(userTagRepository.findByTagId(1L)).thenReturn(List.of(userTag));

        List<TagAccountResponse> response = tagService.getAccountsForTag(1L);

        assertEquals(1, response.size());
        assertEquals(11L, response.get(0).getId());
        assertEquals("ACC001", response.get(0).getAccountId());
        assertEquals(1L, response.get(0).getTagId());
    }
}
