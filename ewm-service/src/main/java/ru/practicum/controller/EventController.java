package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.*;
import ru.practicum.service.EventService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EventController {

    private final EventService eventService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ADMIN endpoints
    @GetMapping("/admin/events")
    public List<EventFullDto> getEvents(@RequestParam(required = false) List<Long> users,
                                        @RequestParam(required = false) List<String> states,
                                        @RequestParam(required = false) List<Long> categories,
                                        @RequestParam(required = false) String rangeStart,
                                        @RequestParam(required = false) String rangeEnd,
                                        @RequestParam(defaultValue = "0") int from,
                                        @RequestParam(defaultValue = "10") int size) {
        log.info("Admin: поиск событий users={}, states={}, categories={}, rangeStart={}, rangeEnd={}, from={}, size={}",
                users, states, categories, rangeStart, rangeEnd, from, size);

        LocalDateTime start = parseDateTime(rangeStart);
        LocalDateTime end = parseDateTime(rangeEnd);

        AdminEventSearchRequest searchRequest = AdminEventSearchRequest.builder()
                .users(users)
                .states(states)
                .categories(categories)
                .rangeStart(start)
                .rangeEnd(end)
                .from(from)
                .size(size)
                .build();

        return eventService.getAdminEvents(searchRequest);
    }

    @PatchMapping("/admin/events/{eventId}")
    public EventFullDto updateEventByAdmin(@PathVariable Long eventId,
                                           @RequestBody @Valid UpdateEventAdminRequest updateRequest) {
        log.info("Admin: обновление события с id={}", eventId);
        return eventService.updateEventByAdmin(eventId, updateRequest);
    }

    // PRIVATE endpoints
    @GetMapping("/users/{userId}/events")
    public List<EventShortDto> getUserEvents(@PathVariable Long userId,
                                             @RequestParam(defaultValue = "0") int from,
                                             @RequestParam(defaultValue = "10") int size) {
        log.info("Private: получение событий пользователя с id={}, from={}, size={}", userId, from, size);
        Pageable pageable = PageRequest.of(from / size, size);
        return eventService.getUserEvents(userId, pageable);
    }

    @PostMapping("/users/{userId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventFullDto createEvent(@PathVariable Long userId,
                                    @Valid @RequestBody NewEventDto newEventDto) {
        log.info("Private: создание события пользователем с id={}", userId);
        return eventService.createEvent(userId, newEventDto);
    }

    @GetMapping("/users/{userId}/events/{eventId}")
    public EventFullDto getUserEvent(@PathVariable Long userId,
                                     @PathVariable Long eventId) {
        log.info("Private: получение события с id={} пользователя с id={}", eventId, userId);
        return eventService.getUserEventById(userId, eventId);
    }

    @PatchMapping("/users/{userId}/events/{eventId}")
    public EventFullDto updateEvent(@PathVariable Long userId,
                                    @PathVariable Long eventId,
                                    @Valid @RequestBody UpdateEventUserRequest updateRequest) {
        log.info("Private: обновление события с id={} пользователя с id={}", eventId, userId);
        log.info("Request body: {}", updateRequest);
        return eventService.updateEventByUser(userId, eventId, updateRequest);
    }

    @PatchMapping("/users/{userId}/events/{eventId}/cancel")
    public EventFullDto cancelEvent(@PathVariable Long userId,
                                    @PathVariable Long eventId) {
        log.info("Private: отмена события с id={} пользователя с id={}", eventId, userId);
        UpdateEventUserRequest updateRequest = UpdateEventUserRequest.builder()
                .stateAction("CANCEL_REVIEW")
                .build();
        log.info("Created update request: {}", updateRequest);
        return eventService.updateEventByUser(userId, eventId, updateRequest);
    }

    // PUBLIC endpoints
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
        log.info("Public: поиск событий text={}, categories={}, paid={}, rangeStart={}, rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);

        LocalDateTime start = parseDateTime(rangeStart);
        LocalDateTime end = parseDateTime(rangeEnd);

        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("RangeStart must be before rangeEnd");
        }

        PublicEventSearchRequest searchRequest = PublicEventSearchRequest.builder()
                .text(text)
                .categories(categories)
                .paid(paid)
                .rangeStart(start)
                .rangeEnd(end)
                .onlyAvailable(onlyAvailable)
                .sort(sort)
                .from(from)
                .size(size)
                .build();

        return eventService.getPublicEvents(searchRequest, request);
    }

    @GetMapping("/events/{id}")
    public EventFullDto getEvent(@PathVariable Long id,
                                 HttpServletRequest request) {
        log.info("Public: получение события с id={}", id);
        return eventService.getPublicEventById(id, request);
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(dateTimeString);
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(dateTimeString, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e2) {
                log.warn("Неверный формат даты: {}. Ожидается: 'yyyy-MM-ddTHH:mm:ss' или 'yyyy-MM-dd HH:mm:ss'", dateTimeString);
                throw new IllegalArgumentException("Неверный формат даты. Используйте: 'yyyy-MM-dd HH:mm:ss'");
            }
        }
    }
}