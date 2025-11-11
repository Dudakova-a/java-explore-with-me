package ru.practicum.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Найти категорию по имени (для проверки уникальности)
     */
    Optional<Category> findByName(String name);

    /**
     * Проверить существование категории по имени
     */
    boolean existsByName(String name);

    /**
     * Найти все категории с пагинацией
     */
    Page<Category> findAll(Pageable pageable);

    /**
     * Проверить, используется ли категория в событиях
     */
    @Query("SELECT COUNT(e) > 0 FROM Event e WHERE e.category.id = :categoryId")
    boolean isCategoryUsedInEvents(@Param("categoryId") Long categoryId);

    /**
     * Найти категории по списку ID
     */
    List<Category> findByIdIn(List<Long> ids);

    /**
     * Найти категории по имени (поиск по частичному совпадению)
     */
    @Query("SELECT c FROM Category c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Category> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);

    /**
     * Найти категории, созданные после указанной даты
     */
    List<Category> findByCreatedAtAfter(java.time.LocalDateTime createdAfter);
}