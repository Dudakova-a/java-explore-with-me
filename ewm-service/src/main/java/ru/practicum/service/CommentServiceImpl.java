package ru.practicum.service;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.*;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ConflictException;
import ru.practicum.model.*;
import ru.practicum.repository.CommentRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    // ========== ПУБЛИЧНЫЕ МЕТОДЫ ==========

    @Override
    public List<CommentDto> getPublishedComments(Long eventId, Pageable pageable) {
        log.info("Getting published comments for event id: {}", eventId);

        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        List<Comment> comments = commentRepository.findByEventIdAndStatus(eventId, CommentStatus.PUBLISHED, pageable);
        return comments.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // ========== ПРИВАТНЫЕ МЕТОДЫ (пользователь) ==========

    @Override
    @Transactional
    public CommentDto createComment(Long userId, NewCommentDto newCommentDto) {
        log.info("Creating comment by user id: {} for event id: {}", userId, newCommentDto.getEventId());

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));

        Event event = eventRepository.findById(newCommentDto.getEventId())
                .orElseThrow(() -> new NotFoundException("Event with id=" + newCommentDto.getEventId() + " was not found"));

        // Проверяем, что событие опубликовано
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot comment on unpublished event");
        }

        // Проверяем, не оставлял ли пользователь уже комментарий к этому событию
        if (commentRepository.existsByEventIdAndAuthorId(event.getId(), userId)) {
            throw new ConflictException("User already has a comment for this event");
        }

        Comment comment = Comment.builder()
                .text(newCommentDto.getText().trim())
                .event(event)
                .author(author)
                .status(CommentStatus.PENDING)
                .created(LocalDateTime.now())
                .build();

        Comment savedComment = commentRepository.save(comment);
        log.info("Comment created successfully with id: {}", savedComment.getId());

        return convertToDto(savedComment);
    }

    @Override
    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto updateCommentDto) {
        log.info("Updating comment id: {} by user id: {}", commentId, userId);

        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found or you are not the author"));

        // Можно редактировать только комментарии на модерации
        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Cannot update comment that is not in PENDING status");
        }

        String newText = updateCommentDto.getText();
        if (newText == null || newText.trim().isEmpty()) {
            throw new ValidationException("Field: text. Error: must not be blank. Value: " + newText);
        }

        comment.setText(newText.trim());
        comment.setUpdated(LocalDateTime.now());

        Comment updatedComment = commentRepository.save(comment);
        log.info("Comment id: {} updated successfully", commentId);

        return convertToDto(updatedComment);
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        log.info("Deleting comment id: {} by user id: {}", commentId, userId);

        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found or you are not the author"));

        commentRepository.delete(comment);
        log.info("Comment id: {} deleted successfully by user", commentId);
    }

    @Override
    public List<CommentDto> getUserComments(Long userId, Pageable pageable) {
        log.info("Getting comments for user id: {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }

        // Получаем все комментарии пользователя (всех статусов)
        List<Comment> comments = commentRepository.findByAuthorIdAndEventId(userId, null, pageable);
        return comments.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // ========== АДМИНИСТРАТИВНЫЕ МЕТОДЫ ==========

    @Override
    public List<CommentDto> getCommentsForModeration(Pageable pageable) {
        log.info("Getting comments for moderation");

        List<Comment> comments = commentRepository.findByStatus(CommentStatus.PENDING, pageable);
        return comments.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CommentDto publishComment(Long commentId) {
        log.info("Publishing comment id: {}", commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Cannot publish comment that is not in PENDING status");
        }

        comment.setStatus(CommentStatus.PUBLISHED);
        comment.setPublished(LocalDateTime.now());

        Comment publishedComment = commentRepository.save(comment);
        log.info("Comment id: {} published successfully", commentId);

        return convertToDto(publishedComment);
    }

    @Override
    @Transactional
    public CommentDto rejectComment(Long commentId) {
        log.info("Rejecting comment id: {}", commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment with id=" + commentId + " was not found"));

        if (comment.getStatus() != CommentStatus.PENDING) {
            throw new ConflictException("Cannot reject comment that is not in PENDING status");
        }

        comment.setStatus(CommentStatus.REJECTED);
        comment.setUpdated(LocalDateTime.now());

        Comment rejectedComment = commentRepository.save(comment);
        log.info("Comment id: {} rejected successfully", commentId);

        return convertToDto(rejectedComment);
    }

    @Override
    public CommentDto getCommentById(Long commentId) {
        log.info("=== GET COMMENT BY ID START ===");
        log.info("Looking for comment id: {}", commentId);

        try {
            Comment comment = commentRepository.findById(commentId)
                    .orElseThrow(() -> {
                        log.info("Comment not found, throwing exception");
                        return new NotFoundException("Comment with id=" + commentId + " was not found");
                    });

            log.info("Comment found: {}", comment);
            return convertToDto(comment);

        } catch (Exception e) {
            log.error("Error getting comment: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        log.info("Deleting comment id: {} by admin", commentId);

        if (!commentRepository.existsById(commentId)) {
            throw new NotFoundException("Comment with id=" + commentId + " was not found");
        }

        commentRepository.deleteById(commentId);
        log.info("Comment id: {} deleted successfully by admin", commentId);
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private CommentDto convertToDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .eventId(comment.getEvent().getId())
                .author(convertToShortDto(comment.getAuthor()))
                .status(comment.getStatus().name())
                .created(comment.getCreated())
                .updated(comment.getUpdated())
                .published(comment.getPublished())
                .build();
    }

    private UserShortDto convertToShortDto(User user) {
        return UserShortDto.builder()
                .id(user.getId())
                .name(user.getName())
                .build();
    }
}