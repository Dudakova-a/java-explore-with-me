package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import ru.practicum.dto.*;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ConflictException;
import ru.practicum.model.*;
import ru.practicum.repository.CommentRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.UserRepository;

import jakarta.validation.ValidationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentServiceImpl commentService;

    // ========== ТЕСТЫ ДЛЯ ПУБЛИЧНЫХ МЕТОДОВ ==========

    @Test
    void getPublishedComments_WithExistingEvent_ShouldReturnComments() {
        // Given
        Long eventId = 1L;
        Pageable pageable = Pageable.unpaged();
        List<Comment> comments = List.of(
                createComment(1L, "Отличное событие!", CommentStatus.PUBLISHED)
        );

        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(commentRepository.findByEventIdAndStatus(eventId, CommentStatus.PUBLISHED, pageable))
                .thenReturn(comments);

        // When
        List<CommentDto> result = commentService.getPublishedComments(eventId, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Отличное событие!", result.get(0).getText());
        assertEquals(CommentStatus.PUBLISHED.name(), result.get(0).getStatus());

        verify(eventRepository).existsById(eventId);
        verify(commentRepository).findByEventIdAndStatus(eventId, CommentStatus.PUBLISHED, pageable);
    }

    @Test
    void getPublishedComments_WithNonExistingEvent_ShouldThrowNotFoundException() {
        // Given
        Long eventId = 999L;
        Pageable pageable = Pageable.unpaged();

        when(eventRepository.existsById(eventId)).thenReturn(false);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> commentService.getPublishedComments(eventId, pageable));

        assertEquals("Event with id=999 was not found", exception.getMessage());
        verify(eventRepository).existsById(eventId);
        verify(commentRepository, never()).findByEventIdAndStatus(anyLong(), any(), any());
    }

    // ========== ТЕСТЫ ДЛЯ СОЗДАНИЯ КОММЕНТАРИЕВ ==========

    @Test
    void createComment_WithValidData_ShouldCreateComment() {
        // Given
        Long userId = 1L;
        NewCommentDto newCommentDto = new NewCommentDto();
        newCommentDto.setText("Отличное событие!");
        newCommentDto.setEventId(2L);

        User author = createUser(userId, "Автор");
        Event event = createEvent(2L, EventState.PUBLISHED);
        Comment savedComment = createComment(1L, "Отличное событие!", CommentStatus.PENDING);

        when(userRepository.findById(userId)).thenReturn(Optional.of(author));
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event));
        when(commentRepository.existsByEventIdAndAuthorId(2L, userId)).thenReturn(false);
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);

        // When
        CommentDto result = commentService.createComment(userId, newCommentDto);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Отличное событие!", result.getText());
        assertEquals(CommentStatus.PENDING.name(), result.getStatus());

        verify(userRepository).findById(userId);
        verify(eventRepository).findById(2L);
        verify(commentRepository).existsByEventIdAndAuthorId(2L, userId);
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void createComment_WithUnpublishedEvent_ShouldThrowConflictException() {
        // Given
        Long userId = 1L;
        NewCommentDto newCommentDto = new NewCommentDto();
        newCommentDto.setText("Текст");
        newCommentDto.setEventId(2L);

        User author = createUser(userId, "Автор");
        Event event = createEvent(2L, EventState.PENDING);

        when(userRepository.findById(userId)).thenReturn(Optional.of(author));
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> commentService.createComment(userId, newCommentDto));

        assertEquals("Cannot comment on unpublished event", exception.getMessage());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void createComment_WithDuplicateComment_ShouldThrowConflictException() {
        // Given
        Long userId = 1L;
        NewCommentDto newCommentDto = new NewCommentDto();
        newCommentDto.setText("Текст");
        newCommentDto.setEventId(2L);

        User author = createUser(userId, "Автор");
        Event event = createEvent(2L, EventState.PUBLISHED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(author));
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event));
        when(commentRepository.existsByEventIdAndAuthorId(2L, userId)).thenReturn(true);

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> commentService.createComment(userId, newCommentDto));

        assertEquals("User already has a comment for this event", exception.getMessage());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void createComment_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long userId = 999L;
        NewCommentDto newCommentDto = new NewCommentDto();
        newCommentDto.setText("Текст");
        newCommentDto.setEventId(2L);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> commentService.createComment(userId, newCommentDto));

        assertEquals("User with id=999 was not found", exception.getMessage());
        verify(eventRepository, never()).findById(anyLong());
    }

    // ========== ТЕСТЫ ДЛЯ ОБНОВЛЕНИЯ КОММЕНТАРИЕВ ==========


    @Test
    void updateComment_WithPublishedComment_ShouldThrowConflictException() {
        // Given
        Long userId = 1L;
        Long commentId = 1L;
        UpdateCommentDto updateDto = new UpdateCommentDto();
        updateDto.setText("Новый текст");

        Comment publishedComment = createComment(commentId, "Текст", CommentStatus.PUBLISHED);

        when(commentRepository.findByIdAndAuthorId(commentId, userId))
                .thenReturn(Optional.of(publishedComment));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> commentService.updateComment(userId, commentId, updateDto));

        assertEquals("Cannot update comment that is not in PENDING status", exception.getMessage());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void updateComment_WithEmptyText_ShouldThrowValidationException() {
        // Given
        Long userId = 1L;
        Long commentId = 1L;
        UpdateCommentDto updateDto = new UpdateCommentDto();
        updateDto.setText("   ");

        Comment comment = createComment(commentId, "Текст", CommentStatus.PENDING);

        when(commentRepository.findByIdAndAuthorId(commentId, userId))
                .thenReturn(Optional.of(comment));

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class,
                () -> commentService.updateComment(userId, commentId, updateDto));

        assertTrue(exception.getMessage().contains("must not be blank"));
        verify(commentRepository, never()).save(any(Comment.class));
    }


    // ========== ТЕСТЫ ДЛЯ УДАЛЕНИЯ КОММЕНТАРИЕВ ==========

    @Test
    void deleteComment_WithValidData_ShouldDeleteComment() {
        // Given
        Long userId = 1L;
        Long commentId = 1L;
        Comment comment = createComment(commentId, "Текст", CommentStatus.PENDING);

        when(commentRepository.findByIdAndAuthorId(commentId, userId))
                .thenReturn(Optional.of(comment));

        // When
        commentService.deleteComment(userId, commentId);

        // Then
        verify(commentRepository).findByIdAndAuthorId(commentId, userId);
        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_WithNonExistingComment_ShouldThrowNotFoundException() {
        // Given
        Long userId = 1L;
        Long commentId = 999L;

        when(commentRepository.findByIdAndAuthorId(commentId, userId))
                .thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> commentService.deleteComment(userId, commentId));

        assertEquals("Comment with id=999 was not found or you are not the author", exception.getMessage());
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ КОММЕНТАРИЕВ ПОЛЬЗОВАТЕЛЯ ==========

    @Test
    void getUserComments_WithExistingUser_ShouldReturnComments() {
        // Given
        Long userId = 1L;
        Pageable pageable = Pageable.unpaged();
        List<Comment> comments = List.of(
                createComment(1L, "Коммент 1", CommentStatus.PENDING),
                createComment(2L, "Коммент 2", CommentStatus.PUBLISHED)
        );

        when(userRepository.existsById(userId)).thenReturn(true);
        when(commentRepository.findByAuthorIdAndEventId(userId, null, pageable))
                .thenReturn(comments);

        // When
        List<CommentDto> result = commentService.getUserComments(userId, pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(userRepository).existsById(userId);
        verify(commentRepository).findByAuthorIdAndEventId(userId, null, pageable);
    }

    @Test
    void getUserComments_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long userId = 999L;
        Pageable pageable = Pageable.unpaged();

        when(userRepository.existsById(userId)).thenReturn(false);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> commentService.getUserComments(userId, pageable));

        assertEquals("User with id=999 was not found", exception.getMessage());
        verify(commentRepository, never()).findByAuthorIdAndEventId(anyLong(), any(), any());
    }

    // ========== ТЕСТЫ ДЛЯ АДМИНИСТРАТИВНЫХ МЕТОДОВ ==========

    @Test
    void getCommentsForModeration_ShouldReturnPendingComments() {
        // Given
        Pageable pageable = Pageable.unpaged();
        List<Comment> comments = List.of(
                createComment(1L, "На модерации", CommentStatus.PENDING)
        );

        when(commentRepository.findByStatus(CommentStatus.PENDING, pageable))
                .thenReturn(comments);

        // When
        List<CommentDto> result = commentService.getCommentsForModeration(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("На модерации", result.get(0).getText());
        verify(commentRepository).findByStatus(CommentStatus.PENDING, pageable);
    }

    @Test
    void publishComment_WithPendingComment_ShouldPublish() {
        // Given
        Long commentId = 1L;
        Comment pendingComment = createComment(commentId, "Текст", CommentStatus.PENDING);
        Comment publishedComment = createComment(commentId, "Текст", CommentStatus.PUBLISHED);
        publishedComment.setPublished(LocalDateTime.now()); // Устанавливаем дату публикации

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(pendingComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(publishedComment);

        // When
        CommentDto result = commentService.publishComment(commentId);

        // Then
        assertNotNull(result);
        assertEquals(CommentStatus.PUBLISHED.name(), result.getStatus());
        assertNotNull(result.getPublished());

        verify(commentRepository).findById(commentId);
        verify(commentRepository).save(pendingComment);
    }

    @Test
    void publishComment_WithNonPendingComment_ShouldThrowConflictException() {
        // Given
        Long commentId = 1L;
        Comment publishedComment = createComment(commentId, "Текст", CommentStatus.PUBLISHED);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(publishedComment));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> commentService.publishComment(commentId));

        assertEquals("Cannot publish comment that is not in PENDING status", exception.getMessage());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void rejectComment_WithPendingComment_ShouldReject() {
        // Given
        Long commentId = 1L;
        Comment pendingComment = createComment(commentId, "Текст", CommentStatus.PENDING);
        Comment rejectedComment = createComment(commentId, "Текст", CommentStatus.REJECTED);
        rejectedComment.setUpdated(LocalDateTime.now()); // Устанавливаем дату обновления

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(pendingComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(rejectedComment);

        // When
        CommentDto result = commentService.rejectComment(commentId);

        // Then
        assertNotNull(result);
        assertEquals(CommentStatus.REJECTED.name(), result.getStatus());
        assertNotNull(result.getUpdated());

        verify(commentRepository).findById(commentId);
        verify(commentRepository).save(pendingComment);
    }

    @Test
    void getCommentById_WithExistingComment_ShouldReturnComment() {
        // Given
        Long commentId = 1L;
        Comment comment = createComment(commentId, "Текст", CommentStatus.PUBLISHED);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        // When
        CommentDto result = commentService.getCommentById(commentId);

        // Then
        assertNotNull(result);
        assertEquals(commentId, result.getId());
        assertEquals("Текст", result.getText());
        verify(commentRepository).findById(commentId);
    }

    @Test
    void getCommentById_WithNonExistingComment_ShouldThrowNotFoundException() {
        // Given
        Long commentId = 999L;

        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> commentService.getCommentById(commentId));

        assertEquals("Comment with id=999 was not found", exception.getMessage());
    }

    @Test
    void deleteCommentByAdmin_WithExistingComment_ShouldDelete() {
        // Given
        Long commentId = 1L;

        when(commentRepository.existsById(commentId)).thenReturn(true);

        // When
        commentService.deleteCommentByAdmin(commentId);

        // Then
        verify(commentRepository).existsById(commentId);
        verify(commentRepository).deleteById(commentId);
    }

    @Test
    void deleteCommentByAdmin_WithNonExistingComment_ShouldThrowNotFoundException() {
        // Given
        Long commentId = 999L;

        when(commentRepository.existsById(commentId)).thenReturn(false);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> commentService.deleteCommentByAdmin(commentId));

        assertEquals("Comment with id=999 was not found", exception.getMessage());
        verify(commentRepository, never()).deleteById(anyLong());
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private Comment createComment(Long id, String text, CommentStatus status) {
        User author = createUser(1L, "Автор");
        Event event = createEvent(1L, EventState.PUBLISHED);

        Comment comment = Comment.builder()
                .id(id)
                .text(text)
                .event(event)
                .author(author)
                .status(status)
                .created(LocalDateTime.now())
                .build();

        // Устанавливаем updated и published в зависимости от статуса
        if (status == CommentStatus.PUBLISHED) {
            comment.setPublished(LocalDateTime.now());
        } else if (status == CommentStatus.REJECTED) {
            comment.setUpdated(LocalDateTime.now());
        }

        return comment;
    }

    private User createUser(Long id, String name) {
        return User.builder()
                .id(id)
                .name(name)
                .email(name.toLowerCase() + "@example.com")
                .build();
    }

    private Event createEvent(Long id, EventState state) {
        return Event.builder()
                .id(id)
                .state(state)
                .build();
    }
}