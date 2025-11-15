package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.CommentDto;
import ru.practicum.dto.NewCommentDto;
import ru.practicum.dto.UpdateCommentDto;
import ru.practicum.service.CommentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // ========== ПУБЛИЧНЫЕ ЭНДПОИНТЫ ==========

    @GetMapping("/events/{eventId}/comments")
    public List<CommentDto> getEventComments(@PathVariable Long eventId,
                                             @PositiveOrZero @RequestParam(defaultValue = "0") Integer from,
                                             @Positive @RequestParam(defaultValue = "10") Integer size) {
        log.info("Public: получение комментариев события с id={}", eventId);
        return commentService.getPublishedComments(eventId, PageRequest.of(from / size, size));
    }

    // ========== ПРИВАТНЫЕ ЭНДПОИНТЫ (пользователь) ==========

    @PostMapping("/users/{userId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto createComment(@PathVariable Long userId,
                                    @RequestBody @Valid NewCommentDto newCommentDto) {
        log.info("Private: создание комментария пользователем с id={} для события с id={}",
                userId, newCommentDto.getEventId());
        return commentService.createComment(userId, newCommentDto);
    }

    @PatchMapping("/users/{userId}/comments/{commentId}")
    public CommentDto updateComment(@PathVariable Long userId,
                                    @PathVariable Long commentId,
                                    @RequestBody @Valid UpdateCommentDto updateCommentDto) {
        log.info("Private: обновление комментария с id={} пользователем с id={}", commentId, userId);
        return commentService.updateComment(userId, commentId, updateCommentDto);
    }

    @DeleteMapping("/users/{userId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long userId,
                              @PathVariable Long commentId) {
        log.info("Private: удаление комментария с id={} пользователем с id={}", commentId, userId);
        commentService.deleteComment(userId, commentId);
    }

    @GetMapping("/users/{userId}/comments")
    public List<CommentDto> getUserComments(@PathVariable Long userId,
                                            @PositiveOrZero @RequestParam(defaultValue = "0") Integer from,
                                            @Positive @RequestParam(defaultValue = "10") Integer size) {
        log.info("Private: получение комментариев пользователя с id={}", userId);
        return commentService.getUserComments(userId, PageRequest.of(from / size, size));
    }
}
