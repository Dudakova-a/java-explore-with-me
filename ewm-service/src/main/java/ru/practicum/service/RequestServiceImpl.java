package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EventRequestStatusUpdateRequest;
import ru.practicum.dto.EventRequestStatusUpdateResult;
import ru.practicum.dto.ParticipationRequestDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.model.Event;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.model.RequestStatus;
import ru.practicum.model.User;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверка, что пользователь не является инициатором события
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Инициатор события не может подать заявку на участие в своём событии");
        }

        // Проверка, что событие опубликовано
        if (!event.getState().equals(ru.practicum.model.EventState.PUBLISHED)) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии");
        }

        // Проверка на повторный запрос - ИСПРАВЛЕННЫЙ ВЫЗОВ
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictException("Нельзя добавить повторный запрос");
        }

        // Проверка лимита участников - ИСПРАВЛЕННЫЙ ВЫЗОВ
        if (event.getParticipantLimit() > 0) {
            long confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            if (confirmedRequests >= event.getParticipantLimit()) {
                throw new ConflictException("Достигнут лимит запросов на участие");
            }
        }

        // Определяем статус заявки
        RequestStatus status = RequestStatus.PENDING;
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            status = RequestStatus.CONFIRMED;
        }

        ParticipationRequest request = ParticipationRequest.builder()
                .requester(user)
                .event(event)
                .created(LocalDateTime.now())
                .status(status)
                .build();

        ParticipationRequest savedRequest = requestRepository.save(request);
        log.info("Создан запрос на участие с id={} для события с id={}", savedRequest.getId(), eventId);

        return mapToParticipationRequestDto(savedRequest);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        // ИСПРАВЛЕННЫЙ ВЫЗОВ - используем правильное название метода
        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Запрос с id=" + requestId + " не найден"));

        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest updatedRequest = requestRepository.save(request);

        log.info("Отменен запрос на участие с id={}", requestId);
        return mapToParticipationRequestDto(updatedRequest);
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        List<ParticipationRequest> requests = requestRepository.findByRequesterId(userId);
        log.info("Получено {} запросов пользователя с id={}", requests.size(), userId);

        return requests.stream()
                .map(this::mapToParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверка, что пользователь является инициатором события
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("Пользователь не является инициатором события");
        }

        List<ParticipationRequest> requests = requestRepository.findByEventId(eventId);
        log.info("Получено {} запросов на событие с id={}", requests.size(), eventId);

        return requests.stream()
                .map(this::mapToParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto confirmRequest(Long userId, Long eventId, Long reqId) {
        log.info("Начало подтверждения заявки: userId={}, eventId={}, reqId={}", userId, eventId, reqId);

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));
            log.debug("Событие найдено: id={}, initiatorId={}, limit={}",
                    eventId, event.getInitiator().getId(), event.getParticipantLimit());

            // Проверка, что пользователь является инициатором события
            if (!event.getInitiator().getId().equals(userId)) {
                log.warn("Пользователь {} не является инициатором события {}", userId, event.getInitiator().getId());
                throw new ValidationException("Пользователь не является инициатором события");
            }

            ParticipationRequest request = requestRepository.findById(reqId)
                    .orElseThrow(() -> new NotFoundException("Запрос с id=" + reqId + " не найден"));
            log.debug("Запрос найден: id={}, status={}, eventId={}",
                    reqId, request.getStatus(), request.getEvent().getId());

            if (!request.getStatus().equals(RequestStatus.PENDING)) {
                log.warn("Некорректный статус заявки: ожидался PENDING, получен {}", request.getStatus());
                throw new ConflictException("Статус заявки должен быть PENDING");
            }

            // Проверка лимита участников
            if (event.getParticipantLimit() > 0) {
                long confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
                log.debug("Проверка лимита: limit={}, confirmed={}",
                        event.getParticipantLimit(), confirmedRequests);

                if (confirmedRequests >= event.getParticipantLimit()) {
                    log.warn("Лимит участников достигнут: eventId={}, limit={}, confirmed={}",
                            eventId, event.getParticipantLimit(), confirmedRequests);
                    throw new ConflictException("Достигнут лимит участников");
                }
            } else {
                log.debug("Лимит участников не установлен или равен 0");
            }

            request.setStatus(RequestStatus.CONFIRMED);
            ParticipationRequest confirmedRequest = requestRepository.save(request);
            log.debug("Заявка подтверждена: id={}", reqId);

            // Если лимит достигнут, отклонить все оставшиеся заявки
            if (event.getParticipantLimit() > 0) {
                long currentConfirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
                log.debug("Проверка лимита после подтверждения: limit={}, currentConfirmed={}",
                        event.getParticipantLimit(), currentConfirmed);

                if (currentConfirmed >= event.getParticipantLimit()) {
                    log.info("Лимит достигнут после подтверждения, отклоняем ожидающие заявки");
                    rejectPendingRequests(eventId);
                }
            }

            log.info("Заявка успешно подтверждена: id={}", reqId);
            return mapToParticipationRequestDto(confirmedRequest);

        } catch (ConflictException e) {
            log.error("ConflictException в confirmRequest: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Неожиданная ошибка в confirmRequest: {}", e.getMessage(), e);
            throw new RuntimeException("Внутренняя ошибка сервера", e);
        }
    }

    @Override
    @Transactional
    public ParticipationRequestDto rejectRequest(Long userId, Long eventId, Long reqId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверка, что пользователь является инициатором события
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("Пользователь не является инициатором события");
        }

        ParticipationRequest request = requestRepository.findById(reqId)
                .orElseThrow(() -> new NotFoundException("Запрос с id=" + reqId + " не найден"));

        if (!request.getStatus().equals(RequestStatus.PENDING)) {
            throw new ConflictException("Статус заявки должен быть PENDING");
        }

        request.setStatus(RequestStatus.REJECTED);
        ParticipationRequest rejectedRequest = requestRepository.save(request);

        log.info("Отклонен запрос на участие с id={}", reqId);
        return mapToParticipationRequestDto(rejectedRequest);
    }

    private void rejectPendingRequests(Long eventId) {
        try {
            List<ParticipationRequest> pendingRequests = requestRepository.findByEventIdAndStatus(eventId, RequestStatus.PENDING);
            log.debug("Найдено {} ожидающих заявок для события {}", pendingRequests.size(), eventId);

            if (!pendingRequests.isEmpty()) {
                for (ParticipationRequest request : pendingRequests) {
                    request.setStatus(RequestStatus.REJECTED);
                    log.debug("Заявка {} отклонена автоматически", request.getId());
                }
                requestRepository.saveAll(pendingRequests);
                log.info("Отклонены {} ожидающих заявок для события {}", pendingRequests.size(), eventId);
            }
        } catch (Exception e) {
            log.error("Ошибка при отклонении ожидающих заявок: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatuses(Long userId, Long eventId,
                                                                EventRequestStatusUpdateRequest updateRequest) {
        log.info("Обновление статусов заявок: userId={}, eventId={}, requestIds={}, status={}",
                userId, eventId, updateRequest.getRequestIds(), updateRequest.getStatus());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверка, что пользователь является инициатором события
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ValidationException("Пользователь не является инициатором события");
        }

        List<ParticipationRequest> requests = requestRepository.findAllById(updateRequest.getRequestIds());

        // Проверка, что найдены все запросы
        if (requests.size() != updateRequest.getRequestIds().size()) {
            throw new NotFoundException("Некоторые заявки не найдены");
        }

        // Проверка, что все заявки принадлежат событию и в статусе PENDING
        for (ParticipationRequest request : requests) {
            if (!request.getEvent().getId().equals(eventId)) {
                throw new ValidationException("Заявка с id=" + request.getId() + " не принадлежит событию с id=" + eventId);
            }
            if (!request.getStatus().equals(RequestStatus.PENDING)) {
                throw new ConflictException("Заявка с id=" + request.getId() + " должна быть в статусе PENDING");
            }
        }

        List<ParticipationRequestDto> confirmedRequests = new ArrayList<>();
        List<ParticipationRequestDto> rejectedRequests = new ArrayList<>();

        if (updateRequest.getStatus().equals("CONFIRMED")) {
            // Логика подтверждения заявок с учетом лимита участников
            long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            long availableSlots = event.getParticipantLimit() - confirmedCount;

            for (int i = 0; i < requests.size(); i++) {
                ParticipationRequest request = requests.get(i);
                if (i < availableSlots) {
                    request.setStatus(RequestStatus.CONFIRMED);
                    confirmedRequests.add(mapToParticipationRequestDto(request));
                } else {
                    request.setStatus(RequestStatus.REJECTED);
                    rejectedRequests.add(mapToParticipationRequestDto(request));
                }
            }

            requestRepository.saveAll(requests);

            // Если после подтверждения лимит достигнут, отклоняем все оставшиеся PENDING заявки
            if (event.getParticipantLimit() > 0 &&
                    (confirmedCount + confirmedRequests.size() >= event.getParticipantLimit())) {
                rejectPendingRequests(eventId);
            }

        } else if (updateRequest.getStatus().equals("REJECTED")) {
            // Логика отклонения заявок
            for (ParticipationRequest request : requests) {
                request.setStatus(RequestStatus.REJECTED);
                rejectedRequests.add(mapToParticipationRequestDto(request));
            }
            requestRepository.saveAll(requests);
        } else {
            throw new ValidationException("Некорректный статус: " + updateRequest.getStatus());
        }

        log.info("Обновлены статусы заявок: подтверждено={}, отклонено={}",
                confirmedRequests.size(), rejectedRequests.size());

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmedRequests)
                .rejectedRequests(rejectedRequests)
                .build();
    }

    private ParticipationRequestDto mapToParticipationRequestDto(ParticipationRequest request) {
        return ParticipationRequestDto.builder()
                .id(request.getId())
                .requester(request.getRequester().getId())
                .event(request.getEvent().getId())
                .created(request.getCreated())
                .status(request.getStatus())
                .build();
    }
}