package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import ru.practicum.dto.CategoryDto;
import ru.practicum.dto.NewCategoryDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.model.Category;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    // Тесты для createCategory
    @Test
    void createCategory_WithValidData_ShouldCreateCategory() {
        // Given
        NewCategoryDto newCategoryDto = new NewCategoryDto("Концерты");
        Category savedCategory = createCategory(1L, "Концерты");

        when(categoryRepository.existsByName("Концерты")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        // When
        CategoryDto result = categoryService.createCategory(newCategoryDto);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Концерты", result.getName());

        verify(categoryRepository).existsByName("Концерты");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createCategory_WithDuplicateName_ShouldThrowConflictException() {
        // Given
        NewCategoryDto newCategoryDto = new NewCategoryDto("Дубликат");

        when(categoryRepository.existsByName("Дубликат")).thenReturn(true);

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.createCategory(newCategoryDto));

        assertEquals("Category with name Дубликат already exists", exception.getMessage());
        verify(categoryRepository).existsByName("Дубликат");
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createCategory_WithDataIntegrityViolation_ShouldThrowConflictException() {
        // Given
        NewCategoryDto newCategoryDto = new NewCategoryDto("Конфликт");

        when(categoryRepository.existsByName("Конфликт")).thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.createCategory(newCategoryDto));

        assertEquals("Category with name Конфликт already exists", exception.getMessage());
        verify(categoryRepository).existsByName("Конфликт");
        verify(categoryRepository).save(any(Category.class));
    }

    // Тесты для getCategories
    @Test
    void getCategories_WithPagination_ShouldReturnCategories() {
        // Given
        int from = 0;
        int size = 10;
        List<Category> categories = List.of(
                createCategory(1L, "Концерты"),
                createCategory(2L, "Выставки")
        );
        Page<Category> categoryPage = new PageImpl<>(categories);

        when(categoryRepository.findAll(any(Pageable.class))).thenReturn(categoryPage);

        // When
        List<CategoryDto> result = categoryService.getCategories(from, size);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Концерты", result.get(0).getName());
        assertEquals("Выставки", result.get(1).getName());

        verify(categoryRepository).findAll(any(Pageable.class));
    }

    @Test
    void getCategories_WithEmptyResult_ShouldReturnEmptyList() {
        // Given
        int from = 0;
        int size = 10;
        Page<Category> emptyPage = new PageImpl<>(List.of());

        when(categoryRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        // When
        List<CategoryDto> result = categoryService.getCategories(from, size);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(categoryRepository).findAll(any(Pageable.class));
    }

    @Test
    void getCategories_WithDifferentPagination_ShouldUseCorrectPageable() {
        // Given
        int from = 10;
        int size = 5;
        Page<Category> categoryPage = new PageImpl<>(List.of(createCategory(1L, "Категория")));

        when(categoryRepository.findAll(any(Pageable.class))).thenReturn(categoryPage);

        // When
        categoryService.getCategories(from, size);

        // Then
        verify(categoryRepository).findAll(argThat((Pageable pageable) -> {
            PageRequest pageRequest = (PageRequest) pageable;
            return pageRequest.getPageNumber() == 2 && // from / size = 10 / 5 = 2
                    pageRequest.getPageSize() == 5 &&
                    pageRequest.getSort().equals(Sort.by("id"));
        }));
    }

    // Тесты для getCategoryById
    @Test
    void getCategoryById_WithExistingId_ShouldReturnCategory() {
        // Given
        Long categoryId = 1L;
        Category category = createCategory(categoryId, "Концерты");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        // When
        CategoryDto result = categoryService.getCategoryById(categoryId);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Концерты", result.getName());
        verify(categoryRepository).findById(categoryId);
    }

    @Test
    void getCategoryById_WithNonExistingId_ShouldThrowNotFoundException() {
        // Given
        Long categoryId = 999L;

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> categoryService.getCategoryById(categoryId));

        assertEquals("Category with id=999 was not found", exception.getMessage());
        verify(categoryRepository).findById(categoryId);
    }

    @Test
    void getCategoryById_WithNullId_ShouldThrowNotFoundException() {
        // Given
        Long categoryId = null;

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> categoryService.getCategoryById(categoryId));

        assertEquals("Category with id=null was not found", exception.getMessage());
        verify(categoryRepository).findById(categoryId);
    }

    // Тесты для updateCategory
    @Test
    void updateCategory_WithValidData_ShouldUpdateCategory() {
        // Given
        Long categoryId = 1L;
        CategoryDto updateDto = new CategoryDto(categoryId, "Новое название");
        Category existingCategory = createCategory(categoryId, "Старое название");
        Category updatedCategory = createCategory(categoryId, "Новое название");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.existsByName("Новое название")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);

        // When
        CategoryDto result = categoryService.updateCategory(categoryId, updateDto);

        // Then
        assertNotNull(result);
        assertEquals(categoryId, result.getId());
        assertEquals("Новое название", result.getName());

        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository).existsByName("Новое название");
        verify(categoryRepository).save(existingCategory);
    }

    @Test
    void updateCategory_WithSameName_ShouldUpdateWithoutNameCheck() {
        // Given
        Long categoryId = 1L;
        CategoryDto updateDto = new CategoryDto(categoryId, "То же название");
        Category existingCategory = createCategory(categoryId, "То же название");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(existingCategory);

        // When
        CategoryDto result = categoryService.updateCategory(categoryId, updateDto);

        // Then
        assertNotNull(result);
        verify(categoryRepository, never()).existsByName(anyString());
        verify(categoryRepository).save(existingCategory);
    }

    @Test
    void updateCategory_WithDuplicateName_ShouldThrowConflictException() {
        // Given
        Long categoryId = 1L;
        CategoryDto updateDto = new CategoryDto(categoryId, "Дубликат");
        Category existingCategory = createCategory(categoryId, "Старое название");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.existsByName("Дубликат")).thenReturn(true);

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.updateCategory(categoryId, updateDto));

        assertEquals("Category with name Дубликат already exists", exception.getMessage());
        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository).existsByName("Дубликат");
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_WithNonExistingId_ShouldThrowNotFoundException() {
        // Given
        Long categoryId = 999L;
        CategoryDto updateDto = new CategoryDto(categoryId, "Новое название");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> categoryService.updateCategory(categoryId, updateDto));

        assertEquals("Category with id=999 was not found", exception.getMessage());
        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository, never()).existsByName(anyString());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_WithDataIntegrityViolation_ShouldThrowConflictException() {
        // Given
        Long categoryId = 1L;
        CategoryDto updateDto = new CategoryDto(categoryId, "Конфликт");
        Category existingCategory = createCategory(categoryId, "Старое название");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
        when(categoryRepository.existsByName("Конфликт")).thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.updateCategory(categoryId, updateDto));

        assertEquals("Category with name Конфликт already exists", exception.getMessage());
        verify(categoryRepository).save(any(Category.class));
    }

    // Тесты для deleteCategory
    @Test
    void deleteCategory_WithNoEvents_ShouldDeleteCategory() {
        // Given
        Long categoryId = 1L;
        Category category = createCategory(categoryId, "Для удаления");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(eventRepository.countByCategoryId(categoryId)).thenReturn(0L);

        // When
        categoryService.deleteCategory(categoryId);

        // Then
        verify(categoryRepository).findById(categoryId);
        verify(eventRepository).countByCategoryId(categoryId);
        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategory_WithEvents_ShouldThrowConflictException() {
        // Given
        Long categoryId = 1L;
        Category category = createCategory(categoryId, "С событиями");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(eventRepository.countByCategoryId(categoryId)).thenReturn(5L);

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.deleteCategory(categoryId));

        assertEquals("The category is not empty", exception.getMessage());
        verify(categoryRepository).findById(categoryId);
        verify(eventRepository).countByCategoryId(categoryId);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void deleteCategory_WithNonExistingId_ShouldThrowNotFoundException() {
        // Given
        Long categoryId = 999L;

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> categoryService.deleteCategory(categoryId));

        assertEquals("Category with id=999 was not found", exception.getMessage());
        verify(categoryRepository).findById(categoryId);
        verify(eventRepository, never()).countByCategoryId(anyLong());
        verify(categoryRepository, never()).delete(any(Category.class));
    }


    // Вспомогательные методы
    private Category createCategory(Long id, String name) {
        return Category.builder()
                .id(id)
                .name(name)
                .createdAt(LocalDateTime.now())
                .build();
    }
}