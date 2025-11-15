package ru.practicum.controller;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.CommentDto;
import ru.practicum.service.CommentService;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class CommentAdminController {

    private final CommentService commentService;

    // GET /admin/comments/{commentId} - получить комментарий
    @GetMapping("/comments/{commentId}")
    public CommentDto getCommentById(@PathVariable Long commentId) {
        log.info("Admin: получение комментария с id={}", commentId);
        return commentService.getCommentById(commentId);
    }

    // PATCH /admin/comments/{commentId} - обновление комментария (публикация/отклонение)
    @PatchMapping("/comments/{commentId}")
    public CommentDto updateCommentStatus(@PathVariable Long commentId,
                                          @RequestBody Map<String, String> updateRequest) {
        log.info("Admin: обновление статуса комментария с id={}, request={}", commentId, updateRequest);


        String status = updateRequest.get("status");
        if ("PUBLISHED".equals(status)) {
            return commentService.publishComment(commentId);
        } else if ("REJECTED".equals(status)) {
            return commentService.rejectComment(commentId);
        } else {
            throw new ValidationException("Invalid status: " + status + ". Use 'PUBLISHED' or 'REJECTED'");
        }
    }


    // Альтернативные отдельные эндпоинты (если нужно)
    @PatchMapping("/comments/{commentId}/publish")
    public CommentDto publishComment(@PathVariable Long commentId) {
        log.info("Admin: публикация комментария с id={}", commentId);
        return commentService.publishComment(commentId);
    }

    @PatchMapping("/comments/{commentId}/reject")
    public CommentDto rejectComment(@PathVariable Long commentId) {
        log.info("Admin: отклонение комментария с id={}", commentId);
        return commentService.rejectComment(commentId);
    }

    @GetMapping("/comments/moderation")
    public List<CommentDto> getCommentsForModeration(@PositiveOrZero @RequestParam(defaultValue = "0") Integer from,
                                                     @Positive @RequestParam(defaultValue = "10") Integer size) {
        log.info("Admin: получение комментариев для модерации");
        return commentService.getCommentsForModeration(PageRequest.of(from / size, size));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCommentByAdmin(@PathVariable Long commentId) {
        log.info("Admin: удаление комментария с id={}", commentId);
        commentService.deleteCommentByAdmin(commentId);
    }
}