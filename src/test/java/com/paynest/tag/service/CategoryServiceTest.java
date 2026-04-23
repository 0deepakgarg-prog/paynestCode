package com.paynest.tag.service;

import com.paynest.tag.dto.response.CategoryResponse;
import com.paynest.tag.entity.Category;
import com.paynest.tag.repository.CategoryRepository;
import com.paynest.tag.repository.TagRepository;
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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserTagRepository userTagRepository;

    @InjectMocks
    private TagService tagService;

    @Test
    void getAllCategories_shouldReturnMappedCategories() {
        Category category = new Category();
        category.setCategoryId(1L);
        category.setCategoryCode("CUSTOMER");
        category.setCategoryName("Customer");
        category.setDescription("Customer category");
        category.setStatus("ACTIVE");

        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<CategoryResponse> response = tagService.getAllCategories();

        assertEquals(1, response.size());
        assertEquals(1L, response.get(0).getCategoryId());
        assertEquals("CUSTOMER", response.get(0).getCategoryCode());
        assertEquals("Customer", response.get(0).getCategoryName());
        assertEquals("Customer category", response.get(0).getDescription());
        assertEquals("ACTIVE", response.get(0).getStatus());
    }
}
