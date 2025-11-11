package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.*;
import ru.practicum.service.EventService;
import ru.practicum.service.RequestService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}")
@RequiredArgsConstructor
@Slf4j
public class PrivateController {

    private final EventService eventService;
    private final RequestService requestService;

    // Events
    @GetMapping("/events")
    public List<EventShortDto> getUserEvents(@PathVariable Long userId,
                                             @RequestParam(defaultValue = "0") int from,
                                             @RequestParam(defaultValue = "10") int size) {
        log.info("Private: получение событий пользователя с id={}, from={}, size={}", userId, from, size);
        Pageable pageable = PageRequest.of(from / size, size);
        return eventService.getUserEvents(userId, pageable);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventFullDto createEvent(@PathVariable Long userId,
                                    @Valid @RequestBody NewEventDto newEventDto,
                                    HttpServletRequest request) { // ДОБАВЛЕНО
        log.info("Private: создание события пользователем с id={}", userId);
        return eventService.createEvent(userId, newEventDto, request);
    }

    @GetMapping("/events/{eventId}")
    public EventFullDto getUserEvent(@PathVariable Long userId,
                                     @PathVariable Long eventId) {
        log.info("Private: получение события с id={} пользователя с id={}", eventId, userId);
        return eventService.getUserEventById(userId, eventId);
    }

    @PatchMapping("/events/{eventId}")
    public EventFullDto updateEvent(@PathVariable Long userId,
                                    @PathVariable Long eventId,
                                    @Valid @RequestBody UpdateEventUserRequest updateRequest) {
        log.info("Private: обновление события с id={} пользователя с id={}", eventId, userId);
        log.info("Request body: {}", updateRequest);
        return eventService.updateEventByUser(userId, eventId, updateRequest);
    }

    @PatchMapping("/events/{eventId}/cancel")
    public EventFullDto cancelEvent(@PathVariable Long userId,
                                    @PathVariable Long eventId) {
        log.info("Private: отмена события с id={} пользователя с id={}", eventId, userId);
        UpdateEventUserRequest updateRequest = UpdateEventUserRequest.builder()
                .stateAction("CANCEL_REVIEW") // ИСПРАВЛЕНО: должно быть CANCEL_REVIEW
                .build();
        log.info("Created update request: {}", updateRequest);
        return eventService.updateEventByUser(userId, eventId, updateRequest);
    }

    // Requests
    @GetMapping("/events/{eventId}/requests")
    public List<ParticipationRequestDto> getEventRequests(@PathVariable Long userId,
                                                          @PathVariable Long eventId) {
        log.info("Private: получение запросов на участие в событии с id={} пользователя с id={}", eventId, userId);
        return requestService.getEventRequests(userId, eventId);
    }

    @PatchMapping("/events/{eventId}/requests/{reqId}/confirm")
    public ParticipationRequestDto confirmRequest(@PathVariable Long userId,
                                                  @PathVariable Long eventId,
                                                  @PathVariable Long reqId) {
        log.info("Private: подтверждение запроса с id={} на событие с id={}", reqId, eventId);
        return requestService.confirmRequest(userId, eventId, reqId);
    }

    @PatchMapping("/events/{eventId}/requests/{reqId}/reject")
    public ParticipationRequestDto rejectRequest(@PathVariable Long userId,
                                                 @PathVariable Long eventId,
                                                 @PathVariable Long reqId) {
        log.info("Private: отклонение запроса с id={} на событие с id={}", reqId, eventId);
        return requestService.rejectRequest(userId, eventId, reqId);
    }

    @GetMapping("/requests")
    public List<ParticipationRequestDto> getUserRequests(@PathVariable Long userId) {
        log.info("Private: получение запросов пользователя с id={}", userId);
        return requestService.getUserRequests(userId);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto createRequest(@PathVariable Long userId,
                                                 @RequestParam Long eventId) {
        log.info("Private: создание запроса на участие пользователем с id={} в событии с id={}", userId, eventId);
        return requestService.createRequest(userId, eventId);
    }

    @PatchMapping("/requests/{requestId}/cancel")
    public ParticipationRequestDto cancelRequest(@PathVariable Long userId,
                                                 @PathVariable Long requestId) {
        log.info("Private: отмена запроса с id={} пользователем с id={}", requestId, userId);
        return requestService.cancelRequest(userId, requestId);
    }

    @PatchMapping("/events/{eventId}/requests")
    public EventRequestStatusUpdateResult updateRequestStatuses(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody EventRequestStatusUpdateRequest updateRequest) {

        log.info("Private: обновление статусов запросов для события с id={}, userId={}, requestIds={}, status={}",
                eventId, userId, updateRequest.getRequestIds(), updateRequest.getStatus());

        return requestService.updateRequestStatuses(userId, eventId, updateRequest);
    }
}