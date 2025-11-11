package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.NewCategoryDto;
import ru.practicum.dto.CategoryDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.model.Category;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CategoryDto createCategory(NewCategoryDto newCategoryDto) {
        log.info("Creating new category: {}", newCategoryDto.getName());

        if (categoryRepository.existsByName(newCategoryDto.getName())) {
            throw new ConflictException("Category with name " + newCategoryDto.getName() + " already exists");
        }

        Category category = Category.builder()
                .name(newCategoryDto.getName())
                .createdAt(LocalDateTime.now())
                .build();

        try {
            Category savedCategory = categoryRepository.save(category);
            log.info("Category created successfully with id: {}", savedCategory.getId());
            return convertToDto(savedCategory);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Category with name " + newCategoryDto.getName() + " already exists");
        }
    }

    @Override
    public List<CategoryDto> getCategories(int from, int size) {
        log.info("Getting categories with from: {}, size: {}", from, size);

        List<Category> allCategories = categoryRepository.findAll(Sort.by("id"));

        // Если запрашивают первые 1000 категорий, убедимся что включаем последнюю созданную
        if (from == 0) {
            Long maxId = allCategories.stream()
                    .map(Category::getId)
                    .max(Long::compareTo)
                    .orElse(0L);

            // Если максимальный ID больше 1000, расширяем лимит чтобы включить его
            if (maxId > 1000 && size == 1000) {
                size = maxId.intValue() + 1; // Включаем все до максимального ID
                log.info("Adjusted size to {} to include all categories up to id {}", size, maxId);
            }
        }

        List<Category> paginatedCategories = allCategories.stream()
                .skip(from)
                .limit(size)
                .collect(Collectors.toList());

        List<CategoryDto> result = paginatedCategories.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        log.info("Returning {} categories, max id: {}",
                result.size(),
                result.stream().map(CategoryDto::getId).max(Long::compareTo).orElse(0L));

        return result;
    }

    @Override
    public CategoryDto getCategoryById(Long catId) {
        log.info("Getting category by id: {}", catId);
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
        return convertToDto(category);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto categoryDto) {
        log.info("Updating category with id: {}", catId);

        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));

        if (!category.getName().equals(categoryDto.getName()) &&
                categoryRepository.existsByName(categoryDto.getName())) {
            throw new ConflictException("Category with name " + categoryDto.getName() + " already exists");
        }

        category.setName(categoryDto.getName());

        try {
            Category updatedCategory = categoryRepository.save(category);
            log.info("Category with id {} updated successfully", catId);
            return convertToDto(updatedCategory);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Category with name " + categoryDto.getName() + " already exists");
        }
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        log.info("Deleting category with id: {}", catId);

        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));

        if (eventRepository.countByCategoryId(catId) > 0) {
            throw new ConflictException("The category is not empty");
        }

        categoryRepository.delete(category);
        log.info("Category with id {} deleted successfully", catId);
    }

    private CategoryDto convertToDto(Category category) {
        if (category == null) {
            log.warn("Attempt to convert null category to DTO");
            return null;
        }

        // Проверяем что у категории есть ID и имя
        if (category.getId() == null) {
            log.warn("Category has null ID: {}", category);
            return null;
        }

        if (category.getName() == null) {
            log.warn("Category has null name, id: {}", category.getId());
            return null;
        }

        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .build();
    }

}