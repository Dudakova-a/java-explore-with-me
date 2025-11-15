package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.*;
import ru.practicum.service.RequestService;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RequestController {

    private final RequestService requestService;

    // PRIVATE endpoints
    @GetMapping("/users/{userId}/events/{eventId}/requests")
    public List<ParticipationRequestDto> getEventRequests(@PathVariable Long userId,
                                                          @PathVariable Long eventId) {
        log.info("Private: получение запросов на участие в событии с id={} пользователя с id={}", eventId, userId);
        return requestService.getEventRequests(userId, eventId);
    }

    @PatchMapping("/users/{userId}/events/{eventId}/requests/{reqId}/confirm")
    public ParticipationRequestDto confirmRequest(@PathVariable Long userId,
                                                  @PathVariable Long eventId,
                                                  @PathVariable Long reqId) {
        log.info("Private: подтверждение запроса с id={} на событие с id={}", reqId, eventId);
        return requestService.confirmRequest(userId, eventId, reqId);
    }

    @PatchMapping("/users/{userId}/events/{eventId}/requests/{reqId}/reject")
    public ParticipationRequestDto rejectRequest(@PathVariable Long userId,
                                                 @PathVariable Long eventId,
                                                 @PathVariable Long reqId) {
        log.info("Private: отклонение запроса с id={} на событие с id={}", reqId, eventId);
        return requestService.rejectRequest(userId, eventId, reqId);
    }

    @GetMapping("/users/{userId}/requests")
    public List<ParticipationRequestDto> getUserRequests(@PathVariable Long userId) {
        log.info("Private: получение запросов пользователя с id={}", userId);
        return requestService.getUserRequests(userId);
    }

    @PostMapping("/users/{userId}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto createRequest(@PathVariable Long userId,
                                                 @RequestParam Long eventId) {
        log.info("Private: создание запроса на участие пользователем с id={} в событии с id={}", userId, eventId);
        return requestService.createRequest(userId, eventId);
    }

    @PatchMapping("/users/{userId}/requests/{requestId}/cancel")
    public ParticipationRequestDto cancelRequest(@PathVariable Long userId,
                                                 @PathVariable Long requestId) {
        log.info("Private: отмена запроса с id={} пользователем с id={}", requestId, userId);
        return requestService.cancelRequest(userId, requestId);
    }

    @PatchMapping("/users/{userId}/events/{eventId}/requests")
    public EventRequestStatusUpdateResult updateRequestStatuses(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody EventRequestStatusUpdateRequest updateRequest) {

        log.info("Private: обновление статусов запросов для события с id={}, userId={}, requestIds={}, status={}",
                eventId, userId, updateRequest.getRequestIds(), updateRequest.getStatus());

        return requestService.updateRequestStatuses(userId, eventId, updateRequest);
    }
}