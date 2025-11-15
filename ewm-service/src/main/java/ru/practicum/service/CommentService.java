package ru.practicum.service;

import ru.practicum.dto.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommentService {

    // Публичный API - получение опубликованных комментариев события
    List<CommentDto> getPublishedComments(Long eventId, Pageable pageable);

    // Приватный API - операции пользователя
    CommentDto createComment(Long userId, NewCommentDto newCommentDto);

    CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto updateCommentDto);

    void deleteComment(Long userId, Long commentId);

    List<CommentDto> getUserComments(Long userId, Pageable pageable);

    // Админский API - модерация
    List<CommentDto> getCommentsForModeration(Pageable pageable);

    CommentDto publishComment(Long commentId);

    CommentDto rejectComment(Long commentId);

    void deleteCommentByAdmin(Long commentId);

    CommentDto getCommentById(Long commentId);
}