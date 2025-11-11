package ru.practicum.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.Compilation;

import java.util.List;
import java.util.Optional;

public interface CompilationRepository extends JpaRepository<Compilation, Long> {

    /**
     * Найти подборки по закрепленному статусу с пагинацией
     */
    Page<Compilation> findByPinned(Boolean pinned, Pageable pageable);

    /**
     * Найти все подборки с пагинацией
     */
    Page<Compilation> findAll(Pageable pageable);

    /**
     * Проверить существование подборки по названию
     */
    boolean existsByTitle(String title);

    /**
     * Найти подборки, содержащие определенное событие
     */
    @Query("SELECT c FROM Compilation c JOIN c.events e WHERE e.id = :eventId")
    List<Compilation> findByEventId(@Param("eventId") Long eventId);

    /**
     * Найти подборки по списку ID событий
     */
    @Query("SELECT DISTINCT c FROM Compilation c JOIN c.events e WHERE e.id IN :eventIds")
    List<Compilation> findByEventIds(@Param("eventIds") List<Long> eventIds);

    /**
     * Найти подборку по названию
     */
    Optional<Compilation> findByTitle(String title);

    /**
     * Найти подборки по названию (поиск по частичному совпадению)
     */
    @Query("SELECT c FROM Compilation c WHERE LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%'))")
    Page<Compilation> findByTitleContainingIgnoreCase(@Param("title") String title, Pageable pageable);

    /**
     * Найти закрепленные подборки
     */
    List<Compilation> findByPinnedTrue();

    /**
     * Найти незакрепленные подборки
     */
    List<Compilation> findByPinnedFalse();

    /**
     * Найти подборки, созданные после указанной даты
     */
    List<Compilation> findByCreatedAtAfter(java.time.LocalDateTime createdAt);

    /**
     * Подсчитать количество подборок
     */
    @Query("SELECT COUNT(c) FROM Compilation c")
    long countCompilations();

    /**
     * Найти подборки с минимальным количеством событий
     */
    @Query("SELECT c FROM Compilation c WHERE SIZE(c.events) >= :minEvents")
    Page<Compilation> findByMinEventsCount(@Param("minEvents") int minEvents, Pageable pageable);
}