package ru.practicum.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Найти пользователей по списку ID с пагинацией
     */
    Page<User> findByIdIn(List<Long> ids, Pageable pageable);

    /**
     * Проверить существование пользователя по email
     */
    boolean existsByEmail(String email);

    /**
     * Найти пользователя по email
     */
    User findByEmail(String email);

    /**
     * Получить количество пользователей
     */
    @Query("SELECT COUNT(u) FROM User u")
    long countUsers();

    /**
     * Найти пользователей по имени (поиск по частичному совпадению)
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<User> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);

    /**
     * Найти пользователей, созданных после указанной даты
     */
    List<User> findByCreatedAtAfter(java.time.LocalDateTime createdAfter);
}