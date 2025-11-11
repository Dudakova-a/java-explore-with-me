package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.CategoryDto;
import ru.practicum.dto.CompilationDto;
import ru.practicum.dto.EventFullDto;
import ru.practicum.dto.EventShortDto;
import ru.practicum.service.CategoryService;
import ru.practicum.service.CompilationService;
import ru.practicum.service.EventService;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
public class PublicController {

    private final CategoryService categoryService;
    private final EventService eventService;
    private final CompilationService compilationService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/categories")
    public List<CategoryDto> getCategories(@RequestParam(defaultValue = "0") int from,
                                           @RequestParam(defaultValue = "10") int size) {
        log.info("Public: получение категорий from={}, size={}", from, size);

        // Прямой вызов сервиса с from/size
        return categoryService.getCategories(from, size);
    }

    @GetMapping("/categories/{catId}")
    public CategoryDto getCategory(@PathVariable Long catId) {
        log.info("Public: получение категории с id={}", catId);
        return categoryService.getCategoryById(catId);
    }

    @GetMapping("/events")
    public List<EventShortDto> getEvents(@RequestParam(required = false) String text,
                                         @RequestParam(required = false) List<Long> categories,
                                         @RequestParam(required = false) Boolean paid,
                                         @RequestParam(required = false) String rangeStart,
                                         @RequestParam(required = false) String rangeEnd,
                                         @RequestParam(defaultValue = "false") Boolean onlyAvailable,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(defaultValue = "0") int from,
                                         @RequestParam(defaultValue = "10") int size,
                                         HttpServletRequest request) {
        log.info("Public: поиск событий text={}, categories={}, paid={}, rangeStart={}, rangeEnd={}",
                text, categories, paid, rangeStart, rangeEnd);

        LocalDateTime start = parseDateTime(rangeStart);
        LocalDateTime end = parseDateTime(rangeEnd);

        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("RangeStart must be before rangeEnd");
        }

        Pageable pageable = PageRequest.of(from / size, size);
        return eventService.getPublicEvents(text, categories, paid, start, end, onlyAvailable, sort, pageable, request);
    }

    @GetMapping("/events/{id}")
    public EventFullDto getEvent(@PathVariable Long id,
                                 HttpServletRequest request) {
        log.info("Public: получение события с id={}", id);
        return eventService.getPublicEventById(id, request);
    }

    @GetMapping("/compilations")
    public List<CompilationDto> getCompilations(@RequestParam(required = false) Boolean pinned,
                                                @RequestParam(defaultValue = "0") int from,
                                                @RequestParam(defaultValue = "10") int size) {
        log.info("Public: получение подборок pinned={}, from={}, size={}", pinned, from, size);
        return compilationService.getCompilations(pinned, from, size);
    }

    @GetMapping("/compilations/{compId}")
    public CompilationDto getCompilation(@PathVariable Long compId) {
        log.info("Public: получение подборки с id={}", compId);
        return compilationService.getCompilation(compId);
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null) {
            return null;
        }

        try {
            // Сначала пробуем стандартный ISO формат (с 'T')
            return LocalDateTime.parse(dateTimeString);
        } catch (DateTimeParseException e1) {
            try {
                // Если не получилось, пробуем формат с пробелом
                return LocalDateTime.parse(dateTimeString, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e2) {
                log.warn("Неверный формат даты: {}. Ожидается: 'yyyy-MM-ddTHH:mm:ss' или 'yyyy-MM-dd HH:mm:ss'", dateTimeString);
                throw new IllegalArgumentException("Неверный формат даты. Используйте: 'yyyy-MM-dd HH:mm:ss'");
            }
        }
    }
}