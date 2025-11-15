package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import ru.practicum.model.Comment;
import ru.practicum.model.CommentStatus;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByEventIdAndStatus(Long eventId, CommentStatus status, Pageable pageable);

    List<Comment> findByAuthorIdAndEventId(Long authorId, Long eventId, Pageable pageable);

    List<Comment> findByStatus(CommentStatus status, Pageable pageable);

    boolean existsByEventIdAndAuthorId(Long eventId, Long authorId);

    Optional<Comment> findByIdAndAuthorId(Long commentId, Long authorId);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.event.id = :eventId AND c.status = 'PUBLISHED'")
    Long countPublishedCommentsByEventId(@Param("eventId") Long eventId);
}