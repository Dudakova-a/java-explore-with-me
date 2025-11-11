package ru.practicum.service;

import ru.practicum.dto.*;
import org.springframework.data.domain.Pageable;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;

public interface EventService {


    EventFullDto createEvent(Long userId, NewEventDto newEventDto, HttpServletRequest request);

    List<EventShortDto> getUserEvents(Long userId, Pageable pageable);

    EventFullDto getUserEventById(Long userId, Long eventId);

    EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest);

    List<EventFullDto> getAdminEvents(List<Long> users, List<String> states, List<Long> categories,
                                      LocalDateTime rangeStart, LocalDateTime rangeEnd, Pageable pageable);

    EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest);

    // ИЗМЕНЕНИЕ: Добавляем HttpServletRequest
    List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                        LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                        Boolean onlyAvailable, String sort, Pageable pageable,
                                        HttpServletRequest request);

    // ИЗМЕНЕНИЕ: Добавляем HttpServletRequest
    EventFullDto getPublicEventById(Long eventId, HttpServletRequest request);
}