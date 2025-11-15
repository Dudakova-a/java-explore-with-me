package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import ru.practicum.dto.*;
import ru.practicum.exception.NotFoundException;
import ru.practicum.model.*;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompilationServiceImplTest {

    @Mock
    private CompilationRepository compilationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventService eventService;

    @InjectMocks
    private CompilationServiceImpl compilationService;

    // ========== ТЕСТЫ ДЛЯ СОЗДАНИЯ ПОДБОРОК ==========

    @Test
    void createCompilation_WithValidData_ShouldCreateCompilation() {
        // Given
        NewCompilationDto newCompilationDto = new NewCompilationDto();
        newCompilationDto.setTitle("Летние мероприятия");
        newCompilationDto.setPinned(true);
        newCompilationDto.setEvents(List.of(1L, 2L));

        Event event1 = createEvent(1L, "Концерт");
        Event event2 = createEvent(2L, "Выставка");
        List<Event> events = List.of(event1, event2);
        Compilation savedCompilation = createCompilation(1L, "Летние мероприятия", true, new HashSet<>(events));

        when(eventRepository.findAllById(List.of(1L, 2L))).thenReturn(events);
        when(compilationRepository.save(any(Compilation.class))).thenReturn(savedCompilation);
        when(eventService.getViewsCount(anyList())).thenReturn(Map.of(1L, 100L, 2L, 150L));

        // When
        CompilationDto result = compilationService.createCompilation(newCompilationDto);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Летние мероприятия", result.getTitle());
        assertTrue(result.getPinned());
        assertEquals(2, result.getEvents().size());

        verify(eventRepository).findAllById(List.of(1L, 2L));
        verify(compilationRepository).save(any(Compilation.class));
        verify(eventService).getViewsCount(events);
    }

    @Test
    void createCompilation_WithEmptyEvents_ShouldCreateCompilation() {
        // Given
        NewCompilationDto newCompilationDto = new NewCompilationDto();
        newCompilationDto.setTitle("Пустая подборка");
        newCompilationDto.setPinned(false);
        newCompilationDto.setEvents(Collections.emptyList());

        Compilation savedCompilation = createCompilation(1L, "Пустая подборка", false, Collections.emptySet());

        when(compilationRepository.save(any(Compilation.class))).thenReturn(savedCompilation);

        // When
        CompilationDto result = compilationService.createCompilation(newCompilationDto);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Пустая подборка", result.getTitle());
        assertFalse(result.getPinned());
        assertTrue(result.getEvents().isEmpty());

        verify(eventRepository, never()).findAllById(any());
        verify(compilationRepository).save(any(Compilation.class));
    }

    @Test
    void createCompilation_WithNullEvents_ShouldCreateCompilation() {
        // Given
        NewCompilationDto newCompilationDto = new NewCompilationDto();
        newCompilationDto.setTitle("Подборка без событий");
        newCompilationDto.setPinned(null);

        Compilation savedCompilation = createCompilation(1L, "Подборка без событий", false, Collections.emptySet());

        when(compilationRepository.save(any(Compilation.class))).thenReturn(savedCompilation);

        // When
        CompilationDto result = compilationService.createCompilation(newCompilationDto);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Подборка без событий", result.getTitle());
        assertFalse(result.getPinned()); // default value
        assertTrue(result.getEvents().isEmpty());

        verify(eventRepository, never()).findAllById(any());
        verify(compilationRepository).save(any(Compilation.class));
    }

    // ========== ТЕСТЫ ДЛЯ УДАЛЕНИЯ ПОДБОРОК ==========

    @Test
    void deleteCompilation_WithExistingId_ShouldDeleteCompilation() {
        // Given
        Long compId = 1L;
        Compilation compilation = createCompilation(compId, "Для удаления", false, Collections.emptySet());

        when(compilationRepository.findById(compId)).thenReturn(Optional.of(compilation));

        // When
        compilationService.deleteCompilation(compId);

        // Then
        verify(compilationRepository).findById(compId);
        verify(compilationRepository).delete(compilation);
    }

    @Test
    void deleteCompilation_WithNonExistingId_ShouldThrowNotFoundException() {
        // Given
        Long compId = 999L;

        when(compilationRepository.findById(compId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> compilationService.deleteCompilation(compId));

        assertEquals("Подборка с id=999 не найдена", exception.getMessage());
        verify(compilationRepository, never()).delete(any(Compilation.class));
    }

    // ========== ТЕСТЫ ДЛЯ ОБНОВЛЕНИЯ ПОДБОРОК ==========

    @Test
    void updateCompilation_WithValidData_ShouldUpdateCompilation() {
        // Given
        Long compId = 1L;
        UpdateCompilationRequest updateRequest = new UpdateCompilationRequest();
        updateRequest.setTitle("Обновленное название");
        updateRequest.setPinned(true);
        updateRequest.setEvents(List.of(3L, 4L));

        Compilation existingCompilation = createCompilation(compId, "Старое название", false, Collections.emptySet());
        Event event3 = createEvent(3L, "Новое событие 1");
        Event event4 = createEvent(4L, "Новое событие 2");
        List<Event> newEvents = List.of(event3, event4);
        Compilation updatedCompilation = createCompilation(compId, "Обновленное название", true, new HashSet<>(newEvents));

        when(compilationRepository.findById(compId)).thenReturn(Optional.of(existingCompilation));
        when(eventRepository.findAllById(List.of(3L, 4L))).thenReturn(newEvents);
        when(compilationRepository.save(any(Compilation.class))).thenReturn(updatedCompilation);
        when(eventService.getViewsCount(anyList())).thenReturn(Map.of(3L, 200L, 4L, 250L));

        // When
        CompilationDto result = compilationService.updateCompilation(compId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(compId, result.getId());
        assertEquals("Обновленное название", result.getTitle());
        assertTrue(result.getPinned());
        assertEquals(2, result.getEvents().size());

        verify(compilationRepository).findById(compId);
        verify(eventRepository).findAllById(List.of(3L, 4L));
        verify(compilationRepository).save(existingCompilation);
        verify(eventService).getViewsCount(newEvents);
    }

    @Test
    void updateCompilation_WithPartialData_ShouldUpdateOnlyProvidedFields() {
        // Given
        Long compId = 1L;
        UpdateCompilationRequest updateRequest = new UpdateCompilationRequest();
        updateRequest.setTitle("Только название");

        Compilation existingCompilation = createCompilation(compId, "Старое название", false, Collections.emptySet());
        Compilation updatedCompilation = createCompilation(compId, "Только название", false, Collections.emptySet());

        when(compilationRepository.findById(compId)).thenReturn(Optional.of(existingCompilation));
        when(compilationRepository.save(any(Compilation.class))).thenReturn(updatedCompilation);

        // When
        CompilationDto result = compilationService.updateCompilation(compId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals(compId, result.getId());
        assertEquals("Только название", result.getTitle());
        assertFalse(result.getPinned()); // осталось прежним
        assertTrue(result.getEvents().isEmpty()); // осталось прежним

        verify(compilationRepository).findById(compId);
        verify(eventRepository, never()).findAllById(any());
        verify(compilationRepository).save(existingCompilation);
    }

    @Test
    void updateCompilation_WithEmptyTitle_ShouldNotUpdateTitle() {
        // Given
        Long compId = 1L;
        UpdateCompilationRequest updateRequest = new UpdateCompilationRequest();
        updateRequest.setTitle("   "); // пустой title
        updateRequest.setPinned(true);

        Compilation existingCompilation = createCompilation(compId, "Оригинальное название", false, Collections.emptySet());
        Compilation updatedCompilation = createCompilation(compId, "Оригинальное название", true, Collections.emptySet());

        when(compilationRepository.findById(compId)).thenReturn(Optional.of(existingCompilation));
        when(compilationRepository.save(any(Compilation.class))).thenReturn(updatedCompilation);

        // When
        CompilationDto result = compilationService.updateCompilation(compId, updateRequest);

        // Then
        assertNotNull(result);
        assertEquals("Оригинальное название", result.getTitle()); // title не изменился
        assertTrue(result.getPinned()); // pinned обновился

        verify(compilationRepository).findById(compId);
        verify(compilationRepository).save(existingCompilation);
    }

    @Test
    void updateCompilation_WithNonExistingId_ShouldThrowNotFoundException() {
        // Given
        Long compId = 999L;
        UpdateCompilationRequest updateRequest = new UpdateCompilationRequest();
        updateRequest.setTitle("Новое название");

        when(compilationRepository.findById(compId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> compilationService.updateCompilation(compId, updateRequest));

        assertEquals("Подборка с id=999 не найдена", exception.getMessage());
        verify(compilationRepository, never()).save(any(Compilation.class));
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ ПОДБОРОК ==========

    @Test
    void getCompilations_WithPinnedTrue_ShouldReturnPinnedCompilations() {
        // Given
        Boolean pinned = true;
        int from = 0;
        int size = 10;
        List<Compilation> compilations = List.of(
                createCompilation(1L, "Закрепленная 1", true, Collections.emptySet()),
                createCompilation(2L, "Закрепленная 2", true, Collections.emptySet())
        );
        Page<Compilation> compilationPage = new PageImpl<>(compilations);

        when(compilationRepository.findByPinned(pinned, PageRequest.of(0, 10))).thenReturn(compilationPage);

        // When
        List<CompilationDto> result = compilationService.getCompilations(pinned, from, size);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(CompilationDto::getPinned));

        verify(compilationRepository).findByPinned(pinned, PageRequest.of(0, 10));
    }

    @Test
    void getCompilations_WithPinnedFalse_ShouldReturnNotPinnedCompilations() {
        // Given
        Boolean pinned = false;
        int from = 0;
        int size = 10;
        List<Compilation> compilations = List.of(
                createCompilation(1L, "Не закрепленная 1", false, Collections.emptySet()),
                createCompilation(2L, "Не закрепленная 2", false, Collections.emptySet())
        );
        Page<Compilation> compilationPage = new PageImpl<>(compilations);

        when(compilationRepository.findByPinned(pinned, PageRequest.of(0, 10))).thenReturn(compilationPage);

        // When
        List<CompilationDto> result = compilationService.getCompilations(pinned, from, size);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(CompilationDto::getPinned));

        verify(compilationRepository).findByPinned(pinned, PageRequest.of(0, 10));
    }

    @Test
    void getCompilations_WithNullPinned_ShouldReturnAllCompilations() {
        // Given
        Boolean pinned = null;
        int from = 0;
        int size = 10;
        List<Compilation> compilations = List.of(
                createCompilation(1L, "Подборка 1", true, Collections.emptySet()),
                createCompilation(2L, "Подборка 2", false, Collections.emptySet())
        );
        Page<Compilation> compilationPage = new PageImpl<>(compilations);

        when(compilationRepository.findAll(PageRequest.of(0, 10))).thenReturn(compilationPage);

        // When
        List<CompilationDto> result = compilationService.getCompilations(pinned, from, size);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());

        verify(compilationRepository).findAll(PageRequest.of(0, 10));
        verify(compilationRepository, never()).findByPinned(anyBoolean(), any());
    }

    @Test
    void getCompilations_WithPagination_ShouldUseCorrectPageable() {
        // Given
        Boolean pinned = true;
        int from = 10;
        int size = 5;
        Page<Compilation> compilationPage = new PageImpl<>(Collections.emptyList());

        when(compilationRepository.findByPinned(pinned, PageRequest.of(2, 5))).thenReturn(compilationPage);

        // When
        compilationService.getCompilations(pinned, from, size);

        // Then
        verify(compilationRepository).findByPinned(pinned, PageRequest.of(2, 5));
    }

    @Test
    void getCompilations_WithEmptyResult_ShouldReturnEmptyList() {
        // Given
        Boolean pinned = true;
        int from = 0;
        int size = 10;
        Page<Compilation> compilationPage = new PageImpl<>(Collections.emptyList());

        when(compilationRepository.findByPinned(pinned, PageRequest.of(0, 10))).thenReturn(compilationPage);

        // When
        List<CompilationDto> result = compilationService.getCompilations(pinned, from, size);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(compilationRepository).findByPinned(pinned, PageRequest.of(0, 10));
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ ПОДБОРКИ ПО ID ==========

    @Test
    void getCompilation_WithExistingId_ShouldReturnCompilation() {
        // Given
        Long compId = 1L;
        Event event = createEvent(1L, "Событие в подборке");
        Compilation compilation = createCompilation(compId, "Тестовая подборка", true, Set.of(event));

        when(compilationRepository.findById(compId)).thenReturn(Optional.of(compilation));
        when(eventService.getViewsCount(anyList())).thenReturn(Map.of(1L, 100L));

        // When
        CompilationDto result = compilationService.getCompilation(compId);

        // Then
        assertNotNull(result);
        assertEquals(compId, result.getId());
        assertEquals("Тестовая подборка", result.getTitle());
        assertTrue(result.getPinned());
        assertEquals(1, result.getEvents().size());
        assertEquals(100L, result.getEvents().get(0).getViews());

        verify(compilationRepository).findById(compId);
        verify(eventService).getViewsCount(List.of(event));
    }

    @Test
    void getCompilation_WithNonExistingId_ShouldThrowNotFoundException() {
        // Given
        Long compId = 999L;

        when(compilationRepository.findById(compId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> compilationService.getCompilation(compId));

        assertEquals("Подборка с id=999 не найдена", exception.getMessage());
        verify(compilationRepository).findById(compId);
    }


    private Compilation createCompilation(Long id, String title, Boolean pinned, Set<Event> events) {
        return Compilation.builder()
                .id(id)
                .title(title)
                .pinned(pinned)
                .events(events != null ? events : Collections.emptySet())
                .build();
    }

    private Event createEvent(Long id, String title) {
        Category category = Category.builder()
                .id(1L)
                .name("Категория")
                .build();

        User user = User.builder()
                .id(1L)
                .name("Организатор")
                .email("organizer@example.com")
                .build();

        return Event.builder()
                .id(id)
                .title(title)
                .annotation("Аннотация " + title)
                .description("Описание " + title)
                .category(category)
                .initiator(user)
                .eventDate(LocalDateTime.now().plusDays(1))
                .createdOn(LocalDateTime.now())
                .publishedOn(LocalDateTime.now())
                .paid(false)
                .participantLimit(100)
                .requestModeration(true)
                .state(EventState.PUBLISHED)
                .lat(55.7558f)
                .lon(37.6173f)
                .confirmedRequests(50)
                .build();
    }
}