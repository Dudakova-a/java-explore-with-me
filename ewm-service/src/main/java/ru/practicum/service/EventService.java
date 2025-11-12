package ru.practicum.service;

import ru.practicum.dto.*;
import org.springframework.data.domain.Pageable;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.model.Event;


import java.util.List;
import java.util.Map;

public interface EventService {

    EventFullDto createEvent(Long userId, NewEventDto newEventDto);

    List<EventShortDto> getUserEvents(Long userId, Pageable pageable);

    EventFullDto getUserEventById(Long userId, Long eventId);

    EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest);

    // НОВЫЙ метод с DTO для админского поиска
    List<EventFullDto> getAdminEvents(AdminEventSearchRequest searchRequest);

    EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest);

    Map<Long, Long> getViewsCount(List<Event> events);

    //  новый метод с DTO для публичного поиска
    List<EventShortDto> getPublicEvents(PublicEventSearchRequest searchRequest, HttpServletRequest request);

    EventFullDto getPublicEventById(Long eventId, HttpServletRequest request);
}