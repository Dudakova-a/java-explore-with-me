package ru.practicum.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import ru.practicum.model.Event;
import ru.practicum.model.EventState;


import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    // Для проверки существования события по категории
    boolean existsByCategoryId(Long categoryId);

    // Для подсчета событий по категории
    long countByCategoryId(Long categoryId);

    // Поиск событий по инициатору с пагинацией
    Page<Event> findByInitiatorId(Long userId, Pageable pageable);

    // Поиск события по ID и инициатору
    Optional<Event> findByIdAndInitiatorId(Long eventId, Long userId);

    // Поиск событий по списку ID
    List<Event> findByIdIn(List<Long> events);

    // В EventRepository - САМЫЙ ПРОСТОЙ ЗАПРОС
    @Query("SELECT e FROM Event e WHERE e.state = 'PUBLISHED'")
    Page<Event> findPublicEvents(Pageable pageable);

    // Поиск опубликованных событий по ID
    Optional<Event> findByIdAndState(Long eventId, EventState state);

}