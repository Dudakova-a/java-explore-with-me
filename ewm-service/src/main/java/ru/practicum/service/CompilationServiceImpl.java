package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.*;
import ru.practicum.exception.NotFoundException;
import ru.practicum.model.Compilation;
import ru.practicum.model.Event;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.repository.EventRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto newCompilationDto) {
        Set<Event> events = new HashSet<>();
        if (newCompilationDto.getEvents() != null && !newCompilationDto.getEvents().isEmpty()) {
            List<Event> eventList = eventRepository.findAllById(newCompilationDto.getEvents());
            events = new HashSet<>(eventList);
        }

        Compilation compilation = Compilation.builder()
                .title(newCompilationDto.getTitle())
                .pinned(newCompilationDto.getPinned() != null ? newCompilationDto.getPinned() : false)
                .events(events)
                .build();

        Compilation savedCompilation = compilationRepository.save(compilation);
        log.info("Создана подборка с id={}", savedCompilation.getId());

        return mapToCompilationDto(savedCompilation);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));

        compilationRepository.delete(compilation);
        log.info("Удалена подборка с id={}", compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest) {
        // Находим подборку
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));

        // Обновляем поля, если они предоставлены в запросе
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            compilation.setTitle(updateRequest.getTitle());
        }

        if (updateRequest.getPinned() != null) {
            compilation.setPinned(updateRequest.getPinned());
        }

        if (updateRequest.getEvents() != null) {
            List<Event> eventList = eventRepository.findAllById(updateRequest.getEvents());
            Set<Event> events = new HashSet<>(eventList);
            compilation.setEvents(events);
        }

        // Сохраняем обновленную подборку
        Compilation updatedCompilation = compilationRepository.save(compilation);
        log.info("Обновлена подборка с id={}", compId);

        return mapToCompilationDto(updatedCompilation);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        Page<Compilation> compilationPage;

        if (pinned != null) {
            compilationPage = compilationRepository.findByPinned(pinned, pageable);
        } else {
            compilationPage = compilationRepository.findAll(pageable);
        }

        List<Compilation> compilations = compilationPage.getContent();
        log.info("Получено {} подборок", compilations.size());

        return compilations.stream()
                .map(this::mapToCompilationDto)
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilation(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));

        log.info("Получена подборка с id={}", compId);
        return mapToCompilationDto(compilation);
    }

    private CompilationDto mapToCompilationDto(Compilation compilation) {
        // Преобразуем Set<Event> в List<EventShortDto>
        List<EventShortDto> eventDtos = compilation.getEvents().stream()
                .map(this::mapToEventShortDto)
                .collect(Collectors.toList());

        return CompilationDto.builder()
                .id(compilation.getId())
                .title(compilation.getTitle())
                .pinned(compilation.getPinned())
                .events(eventDtos) // Теперь List<EventShortDto>
                .build();
    }

    private EventShortDto mapToEventShortDto(Event event) {
        // Создаем CategoryDto
        CategoryDto categoryDto = CategoryDto.builder()
                .id(event.getCategory().getId())
                .name(event.getCategory().getName())
                .build();

        // Создаем UserShortDto
        UserShortDto userShortDto = UserShortDto.builder()
                .id(event.getInitiator().getId())
                .name(event.getInitiator().getName())
                .build();

        return EventShortDto.builder()
                .id(event.getId())
                .title(event.getTitle())
                .annotation(event.getAnnotation())
                .category(categoryDto)
                .eventDate(event.getEventDate())
                .initiator(userShortDto)
                .paid(event.getPaid())
                .views(event.getViews() != null ? event.getViews() : 0L)
                .confirmedRequests(event.getConfirmedRequests() != null ? event.getConfirmedRequests() : 0L)
                .build();
    }
}