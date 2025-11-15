package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.practicum.dto.ParticipationRequestDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.model.*;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestServiceImplTest {

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private RequestServiceImpl requestService;

    // ========== ТЕСТЫ ДЛЯ СОЗДАНИЯ ЗАПРОСОВ ==========

    @Test
    void createRequest_WithValidData_ShouldCreateRequest() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;

        User requester = createUser(userId, "Участник");
        User initiator = createUser(2L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, initiator, category, 10, true);
        ParticipationRequest savedRequest = createRequest(1L, requester, event, RequestStatus.PENDING);

        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(requestRepository.existsByRequesterIdAndEventId(userId, eventId)).thenReturn(false);
        when(requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED)).thenReturn(5L);
        when(requestRepository.save(any(ParticipationRequest.class))).thenReturn(savedRequest);

        // When
        ParticipationRequestDto result = requestService.createRequest(userId, eventId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getRequester());
        assertEquals(eventId, result.getEvent());
        assertEquals(RequestStatus.PENDING, result.getStatus());

        verify(userRepository).findById(userId);
        verify(eventRepository).findById(eventId);
        verify(requestRepository).save(any(ParticipationRequest.class));
    }

    @Test
    void createRequest_WithInitiator_ShouldThrowConflictException() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;

        User initiator = createUser(userId, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, initiator, category, 10, true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(initiator));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> requestService.createRequest(userId, eventId));

        assertEquals("Инициатор события не может подать заявку на участие в своём событии", exception.getMessage());
        verify(requestRepository, never()).save(any(ParticipationRequest.class));
    }

    @Test
    void createRequest_WithUnpublishedEvent_ShouldThrowConflictException() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;

        User requester = createUser(userId, "Участник");
        User initiator = createUser(2L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PENDING, initiator, category, 10, true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> requestService.createRequest(userId, eventId));

        assertEquals("Нельзя участвовать в неопубликованном событии", exception.getMessage());
        verify(requestRepository, never()).save(any(ParticipationRequest.class));
    }

    @Test
    void createRequest_WithDuplicateRequest_ShouldThrowConflictException() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;

        User requester = createUser(userId, "Участник");
        User initiator = createUser(2L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, initiator, category, 10, true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(requestRepository.existsByRequesterIdAndEventId(userId, eventId)).thenReturn(true);

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> requestService.createRequest(userId, eventId));

        assertEquals("Нельзя добавить повторный запрос", exception.getMessage());
        verify(requestRepository, never()).save(any(ParticipationRequest.class));
    }

    @Test
    void createRequest_WithFullEvent_ShouldThrowConflictException() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;

        User requester = createUser(userId, "Участник");
        User initiator = createUser(2L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, initiator, category, 5, true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(requestRepository.existsByRequesterIdAndEventId(userId, eventId)).thenReturn(false);
        when(requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED)).thenReturn(5L);

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> requestService.createRequest(userId, eventId));

        assertEquals("Достигнут лимит запросов на участие", exception.getMessage());
        verify(requestRepository, never()).save(any(ParticipationRequest.class));
    }

    @Test
    void createRequest_WithoutModeration_ShouldCreateConfirmedRequest() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;

        User requester = createUser(userId, "Участник");
        User initiator = createUser(2L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, initiator, category, 0, false);
        ParticipationRequest savedRequest = createRequest(1L, requester, event, RequestStatus.CONFIRMED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(requestRepository.existsByRequesterIdAndEventId(userId, eventId)).thenReturn(false);
        when(requestRepository.save(any(ParticipationRequest.class))).thenReturn(savedRequest);

        // When
        ParticipationRequestDto result = requestService.createRequest(userId, eventId);

        // Then
        assertNotNull(result);
        assertEquals(RequestStatus.CONFIRMED, result.getStatus());
        verify(requestRepository).save(any(ParticipationRequest.class));
    }

    // ========== ТЕСТЫ ДЛЯ ОТМЕНЫ ЗАПРОСОВ ==========

    @Test
    void cancelRequest_WithValidData_ShouldCancelRequest() {
        // Given
        Long userId = 1L;
        Long requestId = 1L;

        User requester = createUser(userId, "Участник");
        User initiator = createUser(2L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(1L, "Событие", EventState.PUBLISHED, initiator, category, 10, true);
        ParticipationRequest request = createRequest(requestId, requester, event, RequestStatus.PENDING);
        ParticipationRequest canceledRequest = createRequest(requestId, requester, event, RequestStatus.CANCELED);

        when(requestRepository.findByIdAndRequesterId(requestId, userId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(ParticipationRequest.class))).thenReturn(canceledRequest);

        // When
        ParticipationRequestDto result = requestService.cancelRequest(userId, requestId);

        // Then
        assertNotNull(result);
        assertEquals(RequestStatus.CANCELED, result.getStatus());
        verify(requestRepository).findByIdAndRequesterId(requestId, userId);
        verify(requestRepository).save(request);
    }

    @Test
    void cancelRequest_WithNonExistingRequest_ShouldThrowNotFoundException() {
        // Given
        Long userId = 1L;
        Long requestId = 999L;

        when(requestRepository.findByIdAndRequesterId(requestId, userId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> requestService.cancelRequest(userId, requestId));

        assertEquals("Запрос с id=999 не найден", exception.getMessage());
        verify(requestRepository, never()).save(any(ParticipationRequest.class));
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ ЗАПРОСОВ ==========

    @Test
    void getUserRequests_WithExistingUser_ShouldReturnRequests() {
        // Given
        Long userId = 1L;

        User requester = createUser(userId, "Участник");
        User initiator = createUser(2L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(1L, "Событие", EventState.PUBLISHED, initiator, category, 10, true);
        List<ParticipationRequest> requests = List.of(
                createRequest(1L, requester, event, RequestStatus.PENDING),
                createRequest(2L, requester, event, RequestStatus.CONFIRMED)
        );

        when(userRepository.existsById(userId)).thenReturn(true);
        when(requestRepository.findByRequesterId(userId)).thenReturn(requests);

        // When
        List<ParticipationRequestDto> result = requestService.getUserRequests(userId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(userRepository).existsById(userId);
        verify(requestRepository).findByRequesterId(userId);
    }

    @Test
    void getUserRequests_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long userId = 999L;

        when(userRepository.existsById(userId)).thenReturn(false);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> requestService.getUserRequests(userId));

        assertEquals("Пользователь с id=999 не найден", exception.getMessage());
        verify(requestRepository, never()).findByRequesterId(anyLong());
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ ЗАПРОСОВ СОБЫТИЯ ==========

    @Test
    void getEventRequests_WithValidData_ShouldReturnRequests() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;

        User initiator = createUser(userId, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, initiator, category, 10, true);
        List<ParticipationRequest> requests = List.of(
                createRequest(1L, createUser(2L, "Участник1"), event, RequestStatus.PENDING),
                createRequest(2L, createUser(3L, "Участник2"), event, RequestStatus.CONFIRMED)
        );

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(requestRepository.findByEventId(eventId)).thenReturn(requests);

        // When
        List<ParticipationRequestDto> result = requestService.getEventRequests(userId, eventId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(eventRepository).findById(eventId);
        verify(requestRepository).findByEventId(eventId);
    }

    @Test
    void getEventRequests_WithNonInitiator_ShouldThrowValidationException() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;

        User initiator = createUser(2L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, initiator, category, 10, true);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> requestService.getEventRequests(userId, eventId));

        assertEquals("Пользователь не является инициатором события", exception.getMessage());
        verify(requestRepository, never()).findByEventId(anyLong());
    }

    // ========== ТЕСТЫ ДЛЯ ПОДТВЕРЖДЕНИЯ/ОТКЛОНЕНИЯ ЗАПРОСОВ ==========


    @Test
    void rejectRequest_WithValidData_ShouldRejectRequest() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;
        Long requestId = 1L;

        User initiator = createUser(userId, "Организатор");
        User requester = createUser(2L, "Участник");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, initiator, category, 10, true);
        ParticipationRequest request = createRequest(requestId, requester, event, RequestStatus.PENDING);
        ParticipationRequest rejectedRequest = createRequest(requestId, requester, event, RequestStatus.REJECTED);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(ParticipationRequest.class))).thenReturn(rejectedRequest);

        // When
        ParticipationRequestDto result = requestService.rejectRequest(userId, eventId, requestId);

        // Then
        assertNotNull(result);
        assertEquals(RequestStatus.REJECTED, result.getStatus());
        verify(requestRepository).save(request);
    }


    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private User createUser(Long id, String name) {
        return User.builder()
                .id(id)
                .name(name)
                .email(name.toLowerCase() + "@example.com")
                .build();
    }

    private Category createCategory(Long id, String name) {
        return Category.builder()
                .id(id)
                .name(name)
                .build();
    }

    private Event createEvent(Long id, String title, EventState state, User initiator, Category category,
                              Integer participantLimit, Boolean requestModeration) {
        return Event.builder()
                .id(id)
                .title(title)
                .annotation("Аннотация")
                .description("Описание")
                .eventDate(LocalDateTime.now().plusDays(1))
                .createdOn(LocalDateTime.now())
                .publishedOn(state == EventState.PUBLISHED ? LocalDateTime.now() : null)
                .paid(false)
                .participantLimit(participantLimit)
                .requestModeration(requestModeration)
                .state(state)
                .initiator(initiator)
                .category(category)
                .lat(55.7558f)
                .lon(37.6173f)
                .confirmedRequests(0)
                .build();
    }

    private ParticipationRequest createRequest(Long id, User requester, Event event, RequestStatus status) {
        return ParticipationRequest.builder()
                .id(id)
                .requester(requester)
                .event(event)
                .created(LocalDateTime.now())
                .status(status)
                .build();
    }
}