package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.model.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    // Базовые методы
    List<ParticipationRequest> findByRequesterId(Long userId);

    List<ParticipationRequest> findByEventId(Long eventId);

    List<ParticipationRequest> findByEventIdAndStatus(Long eventId, RequestStatus status);

    Optional<ParticipationRequest> findByEventIdAndRequesterId(Long eventId, Long userId);

    List<ParticipationRequest> findByIdIn(List<Long> requestIds);

    boolean existsByEventIdAndRequesterId(Long eventId, Long userId);


    // Проверка существования запроса по пользователю и событию
    boolean existsByRequesterIdAndEventId(Long requesterId, Long eventId);

    // Подсчет запросов по событию и статусу
    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    // Поиск запроса по ID и пользователю
    Optional<ParticipationRequest> findByIdAndRequesterId(Long id, Long requesterId);

    // Поиск запросов по списку событий
    List<ParticipationRequest> findByEventIdIn(List<Long> eventIds);

    // Поиск запросов по статусу
    List<ParticipationRequest> findByStatus(RequestStatus status);

    // Статистика
    @Query("SELECT COUNT(r) FROM ParticipationRequest r WHERE r.event.id = :eventId AND r.status = 'CONFIRMED'")
    long countConfirmedRequestsByEventId(@Param("eventId") Long eventId);

    @Query("SELECT r.event.id, COUNT(r) FROM ParticipationRequest r " +
            "WHERE r.event.id IN :eventIds AND r.status = 'CONFIRMED' " +
            "GROUP BY r.event.id")
    List<Object[]> countConfirmedRequestsByEventIds(@Param("eventIds") List<Long> eventIds);

    // Дополнительные методы для удобства
    @Query("SELECT r FROM ParticipationRequest r WHERE r.event.id = :eventId AND r.requester.id = :requesterId")
    Optional<ParticipationRequest> findRequestByEventAndRequester(@Param("eventId") Long eventId,
                                                                  @Param("requesterId") Long requesterId);

    // Подсчет всех подтвержденных запросов для списка событий
    @Query("SELECT r.event.id, COUNT(r) FROM ParticipationRequest r " +
            "WHERE r.event.id IN :eventIds AND r.status = :status " +
            "GROUP BY r.event.id")
    List<Object[]> countByEventIdInAndStatus(@Param("eventIds") List<Long> eventIds,
                                             @Param("status") RequestStatus status);
}