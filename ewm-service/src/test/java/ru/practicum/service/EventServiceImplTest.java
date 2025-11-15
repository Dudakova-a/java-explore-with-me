package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import ru.practicum.dto.*;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.model.*;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.ParticipationRequestRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.StatsClient;
import ru.practicum.statsdto.EndpointHit;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ParticipationRequestRepository requestRepository;

    @Mock
    private StatsClient statsClient;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private EventServiceImpl eventService;

    // ========== ТЕСТЫ ДЛЯ СОЗДАНИЯ СОБЫТИЙ ==========


    @Test
    void createEvent_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long userId = 999L;
        NewEventDto newEventDto = createNewEventDto();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.createEvent(userId, newEventDto));

        assertEquals("User with id=999 was not found", exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void createEvent_WithNonExistingCategory_ShouldThrowNotFoundException() {
        // Given
        Long userId = 1L;
        NewEventDto newEventDto = createNewEventDto();
        User user = createUser(1L, "Организатор");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.createEvent(userId, newEventDto));

        assertEquals("Category with id=1 was not found", exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void createEvent_WithPastEventDate_ShouldThrowValidationException() {
        // Given
        Long userId = 1L;
        NewEventDto newEventDto = createNewEventDto();
        newEventDto.setEventDate(LocalDateTime.now().plusHours(1)); // Меньше 2 часов

        User user = createUser(1L, "Организатор");
        Category category = createCategory(1L, "Концерты");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> eventService.createEvent(userId, newEventDto));

        assertTrue(exception.getMessage().contains("должно содержать дату, которая еще не наступила"));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void createEvent_WithNullLocation_ShouldThrowValidationException() {
        // Given
        Long userId = 1L;
        NewEventDto newEventDto = createNewEventDto();
        newEventDto.setLocation(null);

        User user = createUser(1L, "Организатор");
        Category category = createCategory(1L, "Концерты");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> eventService.createEvent(userId, newEventDto));

        assertEquals("Location is required", exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ СОБЫТИЙ ПОЛЬЗОВАТЕЛЯ ==========

    @Test
    void getUserEvents_WithExistingUser_ShouldReturnEvents() {
        // Given
        Long userId = 1L;
        Pageable pageable = Pageable.unpaged();
        User user = createUser(userId, "Организатор");
        Category category = createCategory(1L, "Концерты");
        List<Event> events = List.of(
                createEvent(1L, "Событие 1", EventState.PENDING, user, category),
                createEvent(2L, "Событие 2", EventState.PUBLISHED, user, category)
        );
        Page<Event> eventPage = new PageImpl<>(events);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(eventRepository.findByInitiatorId(userId, pageable)).thenReturn(eventPage);
        when(requestRepository.countConfirmedRequestsByEventIds(anyList())).thenReturn(List.of());
        when(statsClient.getStats(any(), any(), anyList(), anyBoolean())).thenReturn(List.of());

        // When
        List<EventShortDto> result = eventService.getUserEvents(userId, pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(userRepository).existsById(userId);
        verify(eventRepository).findByInitiatorId(userId, pageable);
    }

    @Test
    void getUserEvents_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long userId = 999L;
        Pageable pageable = Pageable.unpaged();

        when(userRepository.existsById(userId)).thenReturn(false);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.getUserEvents(userId, pageable));

        assertEquals("User with id=999 was not found", exception.getMessage());
        verify(eventRepository, never()).findByInitiatorId(anyLong(), any());
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ СОБЫТИЯ ПОЛЬЗОВАТЕЛЯ ПО ID ==========

    @Test
    void getUserEventById_WithExistingEvent_ShouldReturnEvent() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;
        User user = createUser(userId, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PENDING, user, category);

        when(eventRepository.findByIdAndInitiatorId(eventId, userId)).thenReturn(Optional.of(event));
        when(requestRepository.countConfirmedRequestsByEventId(eventId)).thenReturn(5L);
        when(statsClient.getStats(any(), any(), anyList(), anyBoolean())).thenReturn(List.of());

        // When
        EventFullDto result = eventService.getUserEventById(userId, eventId);

        // Then
        assertNotNull(result);
        assertEquals(eventId, result.getId());
        assertEquals("Событие", result.getTitle());
        verify(eventRepository).findByIdAndInitiatorId(eventId, userId);
    }

    @Test
    void getUserEventById_WithNonExistingEvent_ShouldThrowNotFoundException() {
        // Given
        Long userId = 1L;
        Long eventId = 999L;

        when(eventRepository.findByIdAndInitiatorId(eventId, userId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.getUserEventById(userId, eventId));

        assertEquals("Event with id=999 was not found", exception.getMessage());
    }

    // ========== ТЕСТЫ ДЛЯ ОБНОВЛЕНИЯ СОБЫТИЙ ПОЛЬЗОВАТЕЛЕМ ==========

    @Test
    void updateEventByUser_WithValidData_ShouldUpdateEvent() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;
        UpdateEventUserRequest updateRequest = new UpdateEventUserRequest();
        updateRequest.setTitle("Обновленное название");
        updateRequest.setStateAction("SEND_TO_REVIEW");

        User user = createUser(userId, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Старое название", EventState.PENDING, user, category);
        Event updatedEvent = createEvent(eventId, "Обновленное название", EventState.PENDING, user, category);

        when(eventRepository.findByIdAndInitiatorId(eventId, userId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenReturn(updatedEvent);
        when(requestRepository.countConfirmedRequestsByEventId(eventId)).thenReturn(5L);
        when(statsClient.getStats(any(), any(), anyList(), anyBoolean())).thenReturn(List.of());

        // When
        EventFullDto result = eventService.updateEventByUser(userId, eventId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals("Обновленное название", result.getTitle());
        verify(eventRepository).findByIdAndInitiatorId(eventId, userId);
        verify(eventRepository).save(event);
    }

    @Test
    void updateEventByUser_WithPublishedEvent_ShouldThrowConflictException() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;
        UpdateEventUserRequest updateRequest = new UpdateEventUserRequest();
        updateRequest.setTitle("Новое название");

        User user = createUser(userId, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, user, category);

        when(eventRepository.findByIdAndInitiatorId(eventId, userId)).thenReturn(Optional.of(event));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> eventService.updateEventByUser(userId, eventId, updateRequest));

        assertEquals("Only pending or canceled events can be changed", exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void updateEventByUser_WithInvalidParticipantLimit_ShouldThrowValidationException() {
        // Given
        Long userId = 1L;
        Long eventId = 1L;
        UpdateEventUserRequest updateRequest = new UpdateEventUserRequest();
        updateRequest.setParticipantLimit(-1);

        User user = createUser(userId, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PENDING, user, category);

        when(eventRepository.findByIdAndInitiatorId(eventId, userId)).thenReturn(Optional.of(event));

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> eventService.updateEventByUser(userId, eventId, updateRequest));

        assertTrue(exception.getMessage().contains("must be at least 0"));
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== ТЕСТЫ ДЛЯ АДМИНИСТРАТИВНЫХ МЕТОДОВ ==========

    @Test
    void getAdminEvents_WithFilters_ShouldReturnEvents() {
        // Given
        AdminEventSearchRequest searchRequest = new AdminEventSearchRequest();
        searchRequest.setUsers(List.of(1L));
        searchRequest.setStates(List.of("PENDING"));
        searchRequest.setCategories(List.of(1L));
        searchRequest.setRangeStart(LocalDateTime.now());
        searchRequest.setRangeEnd(LocalDateTime.now().plusDays(1));


        User user = createUser(1L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        List<Event> events = List.of(
                createEvent(1L, "Событие", EventState.PENDING, user, category)
        );


        Page<Event> eventPage = new PageImpl<>(events);


        when(eventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(eventPage);
        when(requestRepository.countConfirmedRequestsByEventIds(anyList())).thenReturn(List.of());
        when(statsClient.getStats(any(), any(), anyList(), anyBoolean())).thenReturn(List.of());

        // When
        List<EventFullDto> result = eventService.getAdminEvents(searchRequest);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll(any(Specification.class), any(Pageable.class));
    }


    @Test
    void updateEventByAdmin_WithValidData_ShouldPublishEvent() {
        // Given
        Long eventId = 1L;
        UpdateEventAdminRequest updateRequest = new UpdateEventAdminRequest();
        updateRequest.setStateAction("PUBLISH_EVENT");

        User user = createUser(1L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PENDING, user, category);
        Event publishedEvent = createEvent(eventId, "Событие", EventState.PUBLISHED, user, category);
        publishedEvent.setPublishedOn(LocalDateTime.now());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenReturn(publishedEvent);
        when(requestRepository.countConfirmedRequestsByEventId(eventId)).thenReturn(5L);
        when(statsClient.getStats(any(), any(), anyList(), anyBoolean())).thenReturn(List.of());

        // When
        EventFullDto result = eventService.updateEventByAdmin(eventId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(EventState.PUBLISHED.name(), result.getState());
        assertNotNull(result.getPublishedOn());
        verify(eventRepository).findById(eventId);
        verify(eventRepository).save(event);
    }

    @Test
    void updateEventByAdmin_WithNonPendingEvent_ShouldThrowConflictException() {
        // Given
        Long eventId = 1L;
        UpdateEventAdminRequest updateRequest = new UpdateEventAdminRequest();
        updateRequest.setStateAction("PUBLISH_EVENT");

        User user = createUser(1L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.CANCELED, user, category);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> eventService.updateEventByAdmin(eventId, updateRequest));

        assertTrue(exception.getMessage().contains("not in the right state"));
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== ТЕСТЫ ДЛЯ ПУБЛИЧНЫХ МЕТОДОВ ==========

    @Test
    void getAdminEvents_WithDefaultPagination_ShouldReturnEvents() {
        // Given
        AdminEventSearchRequest searchRequest = new AdminEventSearchRequest();
        searchRequest.setUsers(List.of(1L));

        Pageable defaultPageable = Pageable.ofSize(10); // Предполагаем дефолтный размер

        User user = createUser(1L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        List<Event> events = List.of(
                createEvent(1L, "Событие", EventState.PENDING, user, category)
        );
        Page<Event> eventPage = new PageImpl<>(events);

        when(eventRepository.findAll(any(Specification.class), eq(defaultPageable))).thenReturn(eventPage);
        when(requestRepository.countConfirmedRequestsByEventIds(anyList())).thenReturn(List.of());
        when(statsClient.getStats(any(), any(), anyList(), anyBoolean())).thenReturn(List.of());

        // When
        List<EventFullDto> result = eventService.getAdminEvents(searchRequest);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll(any(Specification.class), eq(defaultPageable));
    }

    @Test
    void getPublicEventById_WithExistingEvent_ShouldReturnEvent() {
        // Given
        Long eventId = 1L;
        User user = createUser(1L, "Организатор");
        Category category = createCategory(1L, "Концерты");
        Event event = createEvent(eventId, "Событие", EventState.PUBLISHED, user, category);

        when(eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)).thenReturn(Optional.of(event));
        when(requestRepository.countConfirmedRequestsByEventId(eventId)).thenReturn(5L);
        when(statsClient.getStats(any(), any(), anyList(), anyBoolean())).thenReturn(List.of());
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        // When
        EventFullDto result = eventService.getPublicEventById(eventId, httpServletRequest);

        // Then
        assertNotNull(result);
        assertEquals(eventId, result.getId());
        assertEquals("Событие", result.getTitle());
        verify(eventRepository).findByIdAndState(eventId, EventState.PUBLISHED);
        verify(statsClient).hit(any(EndpointHit.class));
    }

    @Test
    void getPublicEventById_WithNonExistingEvent_ShouldThrowNotFoundException() {
        // Given
        Long eventId = 999L;

        when(eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.getPublicEventById(eventId, httpServletRequest));

        assertEquals("Event with id=999 was not found", exception.getMessage());
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private NewEventDto createNewEventDto() {
        NewEventDto dto = new NewEventDto();
        dto.setAnnotation("Аннотация");
        dto.setCategory(1L);
        dto.setDescription("Описание");
        dto.setEventDate(LocalDateTime.now().plusHours(3));
        dto.setLocation(LocationDto.builder().lat(55.7558f).lon(37.6173f).build());
        dto.setPaid(false);
        dto.setParticipantLimit(100);
        dto.setRequestModeration(true);
        dto.setTitle("Новое событие");
        return dto;
    }

    private Event createEvent(Long id, String title, EventState state, User user, Category category) {
        return Event.builder()
                .id(id)
                .title(title)
                .annotation("Аннотация " + title)
                .description("Описание " + title)
                .eventDate(LocalDateTime.now().plusDays(1))
                .createdOn(LocalDateTime.now())
                .publishedOn(state == EventState.PUBLISHED ? LocalDateTime.now() : null)
                .paid(false)
                .participantLimit(100)
                .requestModeration(true)
                .state(state)
                .initiator(user)
                .category(category)
                .lat(55.7558f)
                .lon(37.6173f)
                .confirmedRequests(0)
                .build();
    }

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
}