package ru.practicum.integration_tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.*;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.model.*;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.UserRepository;
import ru.practicum.service.EventService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventServiceIntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EventRepository eventRepository;

    private User testUser;
    private User testInitiator;
    private Category testCategory;
    private NewEventDto testEventDto;

    @BeforeEach
    void setUp() {
        // Создаем тестового пользователя
        testUser = User.builder()
                .name("Test User")
                .email("testuser@example.com")
                .createdAt(LocalDateTime.now())
                .build();
        testUser = userRepository.save(testUser);

        // Создаем инициатора события
        testInitiator = User.builder()
                .name("Event Initiator")
                .email("initiator@example.com")
                .createdAt(LocalDateTime.now())
                .build();
        testInitiator = userRepository.save(testInitiator);

        // Создаем тестовую категорию
        testCategory = Category.builder()
                .name("Test Category")
                .createdAt(LocalDateTime.now())
                .build();
        testCategory = categoryRepository.save(testCategory);

        // Создаем DTO для нового события
        testEventDto = new NewEventDto();
        testEventDto.setTitle("Test Event");
        testEventDto.setAnnotation("Test Event Annotation");
        testEventDto.setDescription("Test Event Description");
        testEventDto.setCategory(testCategory.getId());
        testEventDto.setEventDate(LocalDateTime.now().plusDays(7));
        testEventDto.setLocation(LocationDto.builder().lat(55.7558f).lon(37.6173f).build());
        testEventDto.setPaid(false);
        testEventDto.setParticipantLimit(100);
        testEventDto.setRequestModeration(true);
    }

    @Test
    void createEvent_WithValidData_ShouldCreateEvent() {
        // When
        EventFullDto result = eventService.createEvent(testInitiator.getId(), testEventDto);

        // Then
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("Test Event", result.getTitle());
        assertEquals("Test Event Annotation", result.getAnnotation());
        assertEquals(EventState.PENDING.name(), result.getState());
        assertEquals(testInitiator.getId(), result.getInitiator().getId());

        // Проверяем, что событие сохранено в БД
        Event savedEvent = eventRepository.findById(result.getId()).orElse(null);
        assertNotNull(savedEvent);
        assertEquals("Test Event", savedEvent.getTitle());
        assertEquals(EventState.PENDING, savedEvent.getState());
    }

    @Test
    void createEvent_WithPastEventDate_ShouldThrowValidationException() {
        // Given
        testEventDto.setEventDate(LocalDateTime.now().plusHours(1)); // Меньше 2 часов

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> eventService.createEvent(testInitiator.getId(), testEventDto));

        assertTrue(exception.getMessage().contains("должно содержать дату, которая еще не наступила"));
    }

    @Test
    void createEvent_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long nonExistingUserId = 999999L;

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.createEvent(nonExistingUserId, testEventDto));

        assertEquals("User with id=999999 was not found", exception.getMessage());
    }

    @Test
    void createEvent_WithNonExistingCategory_ShouldThrowNotFoundException() {
        // Given
        testEventDto.setCategory(999999L);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.createEvent(testInitiator.getId(), testEventDto));

        assertEquals("Category with id=999999 was not found", exception.getMessage());
    }

    @Test
    void getUserEvents_WithExistingUser_ShouldReturnUserEvents() {
        // Given - создаем несколько событий
        EventFullDto event1 = eventService.createEvent(testInitiator.getId(), testEventDto);

        NewEventDto anotherEventDto = createNewEventDto("Another Event", "Another annotation");
        EventFullDto event2 = eventService.createEvent(testInitiator.getId(), anotherEventDto);

        // When
        List<EventShortDto> result = eventService.getUserEvents(testInitiator.getId(), Pageable.unpaged());

        // Then
        assertNotNull(result);
        assertTrue(result.size() >= 2);
        assertTrue(result.stream().anyMatch(event -> event.getId().equals(event1.getId())));
        assertTrue(result.stream().anyMatch(event -> event.getId().equals(event2.getId())));
    }

    @Test
    void getUserEvents_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long nonExistingUserId = 888888L;

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.getUserEvents(nonExistingUserId, Pageable.unpaged()));

        assertEquals("User with id=888888 was not found", exception.getMessage());
    }

    @Test
    void getUserEventById_WithValidData_ShouldReturnEvent() {
        // Given
        EventFullDto createdEvent = eventService.createEvent(testInitiator.getId(), testEventDto);

        // When
        EventFullDto result = eventService.getUserEventById(testInitiator.getId(), createdEvent.getId());

        // Then
        assertNotNull(result);
        assertEquals(createdEvent.getId(), result.getId());
        assertEquals("Test Event", result.getTitle());
        assertEquals(testInitiator.getId(), result.getInitiator().getId());
    }

    @Test
    void getUserEventById_WithWrongInitiator_ShouldThrowNotFoundException() {
        // Given
        EventFullDto createdEvent = eventService.createEvent(testInitiator.getId(), testEventDto);

        // When & Then - другой пользователь пытается получить событие
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.getUserEventById(testUser.getId(), createdEvent.getId()));

        assertEquals("Event with id=" + createdEvent.getId() + " was not found", exception.getMessage());
    }

    @Test
    void updateEventByUser_WithValidData_ShouldUpdateEvent() {
        // Given
        EventFullDto createdEvent = eventService.createEvent(testInitiator.getId(), testEventDto);

        UpdateEventUserRequest updateRequest = new UpdateEventUserRequest();
        updateRequest.setTitle("Updated Event Title");
        updateRequest.setAnnotation("Updated Annotation");
        updateRequest.setDescription("Updated Description");

        // When
        EventFullDto result = eventService.updateEventByUser(testInitiator.getId(), createdEvent.getId(), updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(createdEvent.getId(), result.getId());
        assertEquals("Updated Event Title", result.getTitle());
        assertEquals("Updated Annotation", result.getAnnotation());
        assertEquals("Updated Description", result.getDescription());

        // Проверяем в БД
        Event updatedEvent = eventRepository.findById(createdEvent.getId()).orElseThrow();
        assertEquals("Updated Event Title", updatedEvent.getTitle());
    }

    @Test
    void updateEventByUser_WithPublishedEvent_ShouldThrowConflictException() {
        // Given - создаем и публикуем событие
        EventFullDto createdEvent = eventService.createEvent(testInitiator.getId(), testEventDto);

        // Публикуем событие через админа
        UpdateEventAdminRequest publishRequest = new UpdateEventAdminRequest();
        publishRequest.setStateAction("PUBLISH_EVENT");
        eventService.updateEventByAdmin(createdEvent.getId(), publishRequest);

        UpdateEventUserRequest updateRequest = new UpdateEventUserRequest();
        updateRequest.setTitle("Try to update published event");

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> eventService.updateEventByUser(testInitiator.getId(), createdEvent.getId(), updateRequest));

        assertEquals("Only pending or canceled events can be changed", exception.getMessage());
    }

    @Test
    void updateEventByAdmin_WithValidData_ShouldPublishEvent() {
        // Given
        EventFullDto createdEvent = eventService.createEvent(testInitiator.getId(), testEventDto);

        UpdateEventAdminRequest updateRequest = new UpdateEventAdminRequest();
        updateRequest.setStateAction("PUBLISH_EVENT");

        // When
        EventFullDto result = eventService.updateEventByAdmin(createdEvent.getId(), updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(EventState.PUBLISHED.name(), result.getState());
        assertNotNull(result.getPublishedOn());

        // Проверяем в БД
        Event publishedEvent = eventRepository.findById(createdEvent.getId()).orElseThrow();
        assertEquals(EventState.PUBLISHED, publishedEvent.getState());
        assertNotNull(publishedEvent.getPublishedOn());
    }


    @Test
    void getPublicEventById_WithPublishedEvent_ShouldReturnEvent() {
        // Given - создаем и публикуем событие
        EventFullDto createdEvent = eventService.createEvent(testInitiator.getId(), testEventDto);

        UpdateEventAdminRequest publishRequest = new UpdateEventAdminRequest();
        publishRequest.setStateAction("PUBLISH_EVENT");
        EventFullDto publishedEvent = eventService.updateEventByAdmin(createdEvent.getId(), publishRequest);

        // When
        EventFullDto result = eventService.getPublicEventById(publishedEvent.getId(), null);

        // Then
        assertNotNull(result);
        assertEquals(publishedEvent.getId(), result.getId());
        assertEquals("Test Event", result.getTitle());
        assertEquals(EventState.PUBLISHED.name(), result.getState());
    }

    @Test
    void getPublicEventById_WithUnpublishedEvent_ShouldThrowNotFoundException() {
        // Given - создаем событие, но не публикуем
        EventFullDto createdEvent = eventService.createEvent(testInitiator.getId(), testEventDto);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> eventService.getPublicEventById(createdEvent.getId(), null));

        assertEquals("Event with id=" + createdEvent.getId() + " was not found", exception.getMessage());
    }

    @Test
    void eventLifecycle_CreateUpdatePublish_ShouldWorkCorrectly() {
        // Phase 1: Create event
        EventFullDto createdEvent = eventService.createEvent(testInitiator.getId(), testEventDto);
        assertEquals(EventState.PENDING.name(), createdEvent.getState());
        assertNull(createdEvent.getPublishedOn());

        // Phase 2: Update event by user
        UpdateEventUserRequest userUpdate = new UpdateEventUserRequest();
        userUpdate.setTitle("User Updated Title");
        EventFullDto userUpdatedEvent = eventService.updateEventByUser(testInitiator.getId(), createdEvent.getId(), userUpdate);
        assertEquals("User Updated Title", userUpdatedEvent.getTitle());
        assertEquals(EventState.PENDING.name(), userUpdatedEvent.getState());

        // Phase 3: Publish event by admin
        UpdateEventAdminRequest adminUpdate = new UpdateEventAdminRequest();
        adminUpdate.setStateAction("PUBLISH_EVENT");
        EventFullDto publishedEvent = eventService.updateEventByAdmin(createdEvent.getId(), adminUpdate);
        assertEquals(EventState.PUBLISHED.name(), publishedEvent.getState());
        assertNotNull(publishedEvent.getPublishedOn());

        // Phase 4: Get as public
        EventFullDto publicEvent = eventService.getPublicEventById(publishedEvent.getId(), null);
        assertEquals("User Updated Title", publicEvent.getTitle());
        assertEquals(EventState.PUBLISHED.name(), publicEvent.getState());
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private NewEventDto createNewEventDto(String title, String annotation) {
        NewEventDto dto = new NewEventDto();
        dto.setTitle(title);
        dto.setAnnotation(annotation);
        dto.setDescription("Description for " + title);
        dto.setCategory(testCategory.getId());
        dto.setEventDate(LocalDateTime.now().plusDays(10));
        dto.setLocation(LocationDto.builder().lat(55.7558f).lon(37.6173f).build());
        dto.setPaid(true);
        dto.setParticipantLimit(50);
        dto.setRequestModeration(false);
        return dto;
    }
}