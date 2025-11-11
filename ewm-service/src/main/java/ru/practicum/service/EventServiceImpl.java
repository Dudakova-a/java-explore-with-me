package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ru.practicum.statsdto.ViewStats;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.criteria.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipationRequestRepository requestRepository;
    private final StatsClient statsClient;

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto, HttpServletRequest request) {
        log.info("Creating new event for user id: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));

        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category with id=" + newEventDto.getCategory() + " was not found"));


        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. " +
                    "Value: " + newEventDto.getEventDate());
        }

        if (newEventDto.getLocation() == null) {
            throw new ValidationException("Location is required");
        }
        log.info("Event creation data - annotation: '{}', description: '{}', title: '{}', category: {}",
                newEventDto.getAnnotation(),
                newEventDto.getDescription(),
                newEventDto.getTitle(),
                newEventDto.getCategory());
        Event event = Event.builder()
                .annotation(newEventDto.getAnnotation())
                .category(category)
                .description(newEventDto.getDescription())
                .eventDate(newEventDto.getEventDate())
                .initiator(user)
                .paid(Objects.requireNonNullElse(newEventDto.getPaid(), false))
                .participantLimit(Objects.requireNonNullElse(newEventDto.getParticipantLimit(), 0))
                .requestModeration(Objects.requireNonNullElse(newEventDto.getRequestModeration(), true))
                .title(newEventDto.getTitle())
                .state(EventState.PENDING)
                .createdOn(LocalDateTime.now())
                .confirmedRequests(0)
                .views(0L)
                .lat(newEventDto.getLocation().getLat())
                .lon(newEventDto.getLocation().getLon())
                .build();

        Event savedEvent = eventRepository.save(event);

        log.info("Event saved successfully - id: {}, annotation: '{}', description: '{}'",
                savedEvent.getId(),
                savedEvent.getAnnotation(),
                savedEvent.getDescription());

        log.info("Event created successfully with id: {}", savedEvent.getId());
        return convertToFullDto(savedEvent, 0L, 0L);
    }

    @Override
    public List<EventShortDto> getUserEvents(Long userId, Pageable pageable) {
        log.info("Getting events for user id: {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }

        List<Event> events = eventRepository.findByInitiatorId(userId, pageable).getContent();
        Map<Long, Long> confirmedRequests = getConfirmedRequestsCount(events);
        Map<Long, Long> views = getViewsCount(events);

        return events.stream()
                .map(event -> convertToShortDto(event,
                        confirmedRequests.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        log.info("Getting event id: {} for user id: {}", eventId, userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        Long confirmedRequests = requestRepository.countConfirmedRequestsByEventId(eventId);
        Long views = getViewsCount(List.of(event)).getOrDefault(eventId, 0L);

        return convertToFullDto(event, confirmedRequests, views);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        log.info("Updating event id: {} by user id: {}", eventId, userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }


        if (updateRequest.getParticipantLimit() != null && updateRequest.getParticipantLimit() < 0) {
            throw new ValidationException("Field: participantLimit. Error: must be at least 0. Value: " + updateRequest.getParticipantLimit());
        }

        if (updateRequest.getEventDate() != null) {
            if (updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ValidationException("Field: eventDate. Error: должно содержать дату, которая еще не наступила. " +
                        "Value: " + updateRequest.getEventDate());
            }
        }

        updateEventFields(event, updateRequest);

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case "SEND_TO_REVIEW":
                    event.setState(EventState.PENDING);
                    break;
                case "CANCEL_REVIEW":
                    event.setState(EventState.CANCELED);
                    break;
                default:
                    throw new ValidationException("Invalid state action: " + updateRequest.getStateAction());
            }
        }

        Event updatedEvent = eventRepository.save(event);
        Long confirmedRequests = requestRepository.countConfirmedRequestsByEventId(eventId);
        Long views = getViewsCount(List.of(updatedEvent)).getOrDefault(eventId, 0L);

        log.info("Event id: {} updated successfully by user id: {}", eventId, userId);
        return convertToFullDto(updatedEvent, confirmedRequests, views);
    }

    @Override
    public List<EventFullDto> getAdminEvents(List<Long> users, List<String> states, List<Long> categories,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd, Pageable pageable) {
        log.info("Getting events for admin with filters");

        // Используем Specification вместо @Query
        Specification<Event> spec = buildAdminSpecification(users, states, categories, rangeStart, rangeEnd);
        Page<Event> events = eventRepository.findAll(spec, pageable);

        Map<Long, Long> confirmedRequests = getConfirmedRequestsCount(events.getContent());
        Map<Long, Long> views = getViewsCount(events.getContent());

        return events.stream()
                .map(event -> convertToFullDto(event,
                        confirmedRequests.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private Specification<Event> buildAdminSpecification(List<Long> users, List<String> states,
                                                         List<Long> categories, LocalDateTime rangeStart,
                                                         LocalDateTime rangeEnd) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Фильтр по пользователям
            if (users != null && !users.isEmpty()) {
                predicates.add(root.get("initiator").get("id").in(users));
            }

            // Фильтр по статусам
            if (states != null && !states.isEmpty()) {
                List<EventState> eventStates = states.stream()
                        .map(state -> EventState.valueOf(state.toUpperCase()))
                        .collect(Collectors.toList());
                predicates.add(root.get("state").in(eventStates));
            }

            // Фильтр по категориям
            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categories));
            }

            // Фильтр по дате начала
            if (rangeStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
            }

            // Фильтр по дате окончания
            if (rangeEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        log.info("Updating event id: {} by admin", eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (updateRequest.getEventDate() != null) {
            if (updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ValidationException("Event date must be at least 1 hour from now");
            }
        }

        updateEventFields(event, updateRequest);

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case "PUBLISH_EVENT":
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case "REJECT_EVENT":
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Cannot reject the event because it's already published");
                    }
                    event.setState(EventState.CANCELED);
                    break;
                default:
                    throw new ValidationException("Invalid state action: " + updateRequest.getStateAction());
            }
        }

        Event updatedEvent = eventRepository.save(event);
        Long confirmedRequests = requestRepository.countConfirmedRequestsByEventId(eventId);
        Long views = getViewsCount(List.of(updatedEvent)).getOrDefault(eventId, 0L);

        log.info("Event id: {} updated successfully by admin", eventId);
        return convertToFullDto(updatedEvent, confirmedRequests, views);
    }

    @Override
    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, String sort, Pageable pageable,
                                               HttpServletRequest request) {
        log.info("Public: поиск событий text='{}', categories={}, paid={}", text, categories, paid);

        // ИСПОЛЬЗУЕМ Specification ДЛЯ ФИЛЬТРАЦИИ
        Specification<Event> spec = buildPublicSpecification(text, categories, paid, rangeStart, rangeEnd, onlyAvailable);
        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        saveEndpointHit(request, "/events");

        Map<Long, Long> confirmedRequests = getConfirmedRequestsCount(events);
        Map<Long, Long> views = getViewsCount(events);

        List<EventShortDto> result = events.stream()
                .map(event -> convertToShortDto(event,
                        confirmedRequests.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());

        if ("VIEWS".equals(sort)) {
            result.sort(Comparator.comparing(EventShortDto::getViews).reversed());
        } else if ("EVENT_DATE".equals(sort)) {
            result.sort(Comparator.comparing(EventShortDto::getEventDate));
        }

        log.info("Found {} events after filtering", result.size());
        return result;
    }


    private Specification<Event> buildPublicSpecification(String text, List<Long> categories, Boolean paid,
                                                          LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                          Boolean onlyAvailable) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ОСНОВНОЙ ФИЛЬТР: только опубликованные события
            predicates.add(cb.equal(root.get("state"), EventState.PUBLISHED));

            // ФИЛЬТР ПО ТЕКСТУ (аннотация + описание)
            if (text != null && !text.isBlank()) {
                String searchText = "%" + text.toLowerCase() + "%";
                Predicate textPredicate = cb.or(
                        cb.like(cb.lower(root.get("annotation")), searchText),
                        cb.like(cb.lower(root.get("description")), searchText)
                );
                predicates.add(textPredicate);
            }

            // ФИЛЬТР ПО КАТЕГОРИЯМ
            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categories));
            }

            // ФИЛЬТР ПО ПЛАТНОСТИ
            if (paid != null) {
                predicates.add(cb.equal(root.get("paid"), paid));
            }

            // ФИЛЬТР ПО ДАТЕ НАЧАЛА
            if (rangeStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
            }

            // ФИЛЬТР ПО ДАТЕ ОКОНЧАНИЯ
            if (rangeEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
            }

            // ФИЛЬТР ПО ДОСТУПНОСТИ (если onlyAvailable = true)
            if (Boolean.TRUE.equals(onlyAvailable)) {
                Predicate availablePredicate = cb.or(
                        cb.equal(root.get("participantLimit"), 0), // нет лимита
                        cb.greaterThan(root.get("participantLimit"), root.get("confirmedRequests")) // есть свободные места
                );
                predicates.add(availablePredicate);
            }

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public EventFullDto getPublicEventById(Long eventId, HttpServletRequest request) {
        log.info("Getting public event by id: {}", eventId);

        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        // ОТПРАВЛЯЕМ ХИТ В СТАТИСТИКУ
        saveEndpointHit(request, "/events/" + eventId);


        Long confirmedRequests = requestRepository.countConfirmedRequestsByEventId(eventId);


        Long views = getViewsCount(List.of(event)).getOrDefault(eventId, 0L);

        log.info("Event {} has {} views from stats service", eventId, views);

        return convertToFullDto(event, confirmedRequests, views);
    }

    private void updateEventFields(Event event, UpdateEventUserRequest updateRequest) {
        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + updateRequest.getCategory() + " was not found"));
            event.setCategory(category);
        }
        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLat(updateRequest.getLocation().getLat());
            event.setLon(updateRequest.getLocation().getLon());
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private void updateEventFields(Event event, UpdateEventAdminRequest updateRequest) {
        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category with id=" + updateRequest.getCategory() + " was not found"));
            event.setCategory(category);
        }
        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLat(updateRequest.getLocation().getLat());
            event.setLon(updateRequest.getLocation().getLon());
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle());
        }
    }

    private Map<Long, Long> getConfirmedRequestsCount(List<Event> events) {
        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());

        if (eventIds.isEmpty()) {
            return new HashMap<>();
        }

        List<Object[]> results = requestRepository.countConfirmedRequestsByEventIds(eventIds);
        Map<Long, Long> counts = new HashMap<>();

        for (Object[] result : results) {
            counts.put((Long) result[0], (Long) result[1]);
        }

        for (Event event : events) {
            counts.putIfAbsent(event.getId(), 0L);
        }

        return counts;
    }

    private Map<Long, Long> getViewsCount(List<Event> events) {
        if (events.isEmpty()) {
            return new HashMap<>();
        }

        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());


        LocalDateTime start = LocalDateTime.now().minusYears(10);
        LocalDateTime end = LocalDateTime.now().plusYears(10);

        try {
            List<ViewStats> stats = statsClient.getStats(start, end, uris, true);

            Map<Long, Long> views = new HashMap<>();
            for (ViewStats stat : stats) {
                Long eventId = extractEventIdFromUri(stat.getUri());
                views.put(eventId, stat.getHits());
            }

            for (Event event : events) {
                views.putIfAbsent(event.getId(), 0L);
            }

            return views;
        } catch (Exception e) {
            log.error("Error getting views count from stats service", e);

            Map<Long, Long> fallback = new HashMap<>();
            for (Event event : events) {
                fallback.put(event.getId(), 0L);
            }
            return fallback;
        }
    }

    private Long extractEventIdFromUri(String uri) {
        try {
            return Long.parseLong(uri.substring(uri.lastIndexOf("/") + 1));
        } catch (NumberFormatException e) {
            log.warn("Invalid event ID in URI: {}", uri);
            return -1L;
        }
    }

    private void saveEndpointHit(HttpServletRequest request, String uri) {
        try {
            EndpointHit endpointHit = EndpointHit.builder()
                    .app("ewm-main-service")
                    .uri(uri)
                    .ip(getClientIp(request))
                    .timestamp(LocalDateTime.now())
                    .build();

            statsClient.hit(endpointHit);
        } catch (Exception e) {
            log.error("Failed to save endpoint hit for URI: {}", uri, e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    private EventFullDto convertToFullDto(Event event, Long confirmedRequests, Long views) {
        if (event == null) {
            return null;
        }

        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(convertToDto(event.getCategory()))
                .confirmedRequests(confirmedRequests != null ? confirmedRequests : 0L)
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .initiator(convertToShortDto(event.getInitiator()))
                .location(event.getLat() != null && event.getLon() != null ?
                        LocationDto.builder()
                                .lat(event.getLat())
                                .lon(event.getLon())
                                .build() :
                        LocationDto.builder().lat(0f).lon(0f).build())
                .paid(event.getPaid() != null ? event.getPaid() : false)
                .participantLimit(event.getParticipantLimit() != null ? event.getParticipantLimit() : 0)
                .publishedOn(event.getPublishedOn())
                .requestModeration(event.getRequestModeration() != null ? event.getRequestModeration() : true)
                .state(event.getState() != null ? event.getState().name() : EventState.PENDING.name())
                .title(event.getTitle())
                .views(views != null ? views : 0L)
                .build();
    }

    private EventShortDto convertToShortDto(Event event, Long confirmedRequests, Long views) {
        if (event == null) {
            return null;
        }

        return EventShortDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(convertToDto(event.getCategory()))
                .confirmedRequests(confirmedRequests != null ? confirmedRequests : 0L)
                .eventDate(event.getEventDate())
                .initiator(convertToShortDto(event.getInitiator()))
                .paid(event.getPaid() != null ? event.getPaid() : false)
                .title(event.getTitle())
                .views(views != null ? views : 0L)
                .build();
    }

    private UserShortDto convertToShortDto(User user) {
        return UserShortDto.builder()
                .id(user.getId())
                .name(user.getName())
                .build();
    }

    private CategoryDto convertToDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .build();
    }
}