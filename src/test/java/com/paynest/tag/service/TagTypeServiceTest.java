package com.paynest.tag.service;

import com.paynest.tag.dto.response.TagTypeResponse;
import com.paynest.tag.entity.TagType;
import com.paynest.tag.repository.CategoryRepository;
import com.paynest.tag.repository.TagRepository;
import com.paynest.tag.repository.TagTypeRepository;
import com.paynest.tag.repository.UserTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagTypeServiceTest {

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
    void getAllTagTypes_shouldReturnMappedTagTypes() {
        TagType tagType = new TagType();
        tagType.setTagTypeId(1L);
        tagType.setTypeCode("BASE");
        tagType.setTypeName("Base");
        tagType.setDescription("Base tag type");
        tagType.setStatus("ACTIVE");

        when(tagTypeRepository.findAll()).thenReturn(List.of(tagType));

        List<TagTypeResponse> response = tagService.getAllTagTypes();

        assertEquals(1, response.size());
        assertEquals(1L, response.get(0).getTagTypeId());
        assertEquals("BASE", response.get(0).getTypeCode());
        assertEquals("Base", response.get(0).getTypeName());
        assertEquals("Base tag type", response.get(0).getDescription());
        assertEquals("ACTIVE", response.get(0).getStatus());
    }
}
