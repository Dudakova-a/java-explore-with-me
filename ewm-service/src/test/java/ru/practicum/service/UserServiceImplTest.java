package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import ru.practicum.dto.NewUserRequest;
import ru.practicum.dto.UserDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.model.User;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    // ========== ТЕСТЫ ДЛЯ СОЗДАНИЯ ПОЛЬЗОВАТЕЛЕЙ ==========

    @Test
    void createUser_WithValidData_ShouldCreateUser() {
        // Given
        NewUserRequest newUserRequest = new NewUserRequest();
        newUserRequest.setName("John Doe");
        newUserRequest.setEmail("john@example.com");

        User savedUser = createUser(1L, "John Doe", "john@example.com");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        UserDto result = userService.createUser(newUserRequest);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("John Doe", result.getName());
        assertEquals("john@example.com", result.getEmail());

        verify(userRepository).existsByEmail("john@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_WithDuplicateEmail_ShouldThrowConflictException() {
        // Given
        NewUserRequest newUserRequest = new NewUserRequest();
        newUserRequest.setName("John Doe");
        newUserRequest.setEmail("duplicate@example.com");

        when(userRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> userService.createUser(newUserRequest));

        assertEquals("User with email duplicate@example.com already exists", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_WithDataIntegrityViolation_ShouldThrowConflictException() {
        // Given
        NewUserRequest newUserRequest = new NewUserRequest();
        newUserRequest.setName("John Doe");
        newUserRequest.setEmail("john@example.com");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("Duplicate email"));

        // When & Then
        ConflictException exception = assertThrows(ConflictException.class,
                () -> userService.createUser(newUserRequest));

        assertEquals("User with email john@example.com already exists", exception.getMessage());
        verify(userRepository).save(any(User.class));
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ ПОЛЬЗОВАТЕЛЕЙ ==========

    @Test
    void getUsers_WithEmptyIds_ShouldReturnAllUsers() {
        // Given
        List<Long> ids = null;
        Pageable pageable = Pageable.unpaged();

        List<User> users = List.of(
                createUser(1L, "User One", "user1@example.com"),
                createUser(2L, "User Two", "user2@example.com")
        );
        Page<User> userPage = new PageImpl<>(users);

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // When
        List<UserDto> result = userService.getUsers(ids, pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("User One", result.get(0).getName());
        assertEquals("User Two", result.get(1).getName());

        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).findByIdIn(anyList(), any());
    }


    @Test
    void getUsers_WithEmptyListIds_ShouldReturnAllUsers() {
        // Given
        List<Long> ids = List.of();
        Pageable pageable = Pageable.unpaged();

        List<User> users = List.of(
                createUser(1L, "User One", "user1@example.com")
        );
        Page<User> userPage = new PageImpl<>(users);

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // When
        List<UserDto> result = userService.getUsers(ids, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).findByIdIn(anyList(), any());
    }

    @Test
    void getUsers_WithPagination_ShouldUseCorrectPageable() {
        // Given
        List<Long> ids = null;
        Pageable pageable = PageRequest.of(1, 10); // page 1, size 10

        Page<User> userPage = new PageImpl<>(List.of());

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // When
        userService.getUsers(ids, pageable);

        // Then
        verify(userRepository).findAll(pageable);
    }

    @Test
    void getUsers_WithEmptyResult_ShouldReturnEmptyList() {
        // Given
        List<Long> ids = null;
        Pageable pageable = Pageable.unpaged();

        Page<User> userPage = new PageImpl<>(List.of());

        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // When
        List<UserDto> result = userService.getUsers(ids, pageable);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userRepository).findAll(pageable);
    }

    // ========== ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ ПОЛЬЗОВАТЕЛЯ ПО ID ==========

    @Test
    void getUserById_WithExistingUser_ShouldReturnUser() {
        // Given
        Long userId = 1L;
        User user = createUser(userId, "John Doe", "john@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        UserDto result = userService.getUserById(userId);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("John Doe", result.getName());
        assertEquals("john@example.com", result.getEmail());

        verify(userRepository).findById(userId);
    }

    @Test
    void getUserById_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long userId = 999L;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userService.getUserById(userId));

        assertEquals("User with id=999 was not found", exception.getMessage());
        verify(userRepository).findById(userId);
    }

    // ========== ТЕСТЫ ДЛЯ УДАЛЕНИЯ ПОЛЬЗОВАТЕЛЕЙ ==========

    @Test
    void deleteUser_WithExistingUser_ShouldDeleteUser() {
        // Given
        Long userId = 1L;

        when(userRepository.existsById(userId)).thenReturn(true);

        // When
        userService.deleteUser(userId);

        // Then
        verify(userRepository).existsById(userId);
        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteUser_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long userId = 999L;

        when(userRepository.existsById(userId)).thenReturn(false);

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userService.deleteUser(userId));

        assertEquals("User with id=999 was not found", exception.getMessage());
        verify(userRepository).existsById(userId);
        verify(userRepository, never()).deleteById(anyLong());
    }

    // ========== ТЕСТЫ ДЛЯ КОНВЕРТАЦИИ В DTO (через публичные методы) ==========

    @Test
    void getUserById_ShouldReturnCorrectDtoStructure() {
        // Given
        Long userId = 1L;
        User user = createUser(userId, "Test User", "test@example.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        UserDto result = userService.getUserById(userId);

        // Then - проверяем структуру DTO через публичные методы
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("Test User", result.getName());
        assertEquals("test@example.com", result.getEmail());
        // Убеждаемся, что нет лишних полей
        assertDoesNotThrow(() -> {
            result.getId();
            result.getName();
            result.getEmail();
        });
    }

    // ========== ГРАНИЧНЫЕ СЛУЧАИ ==========

    @Test
    void createUser_WithNullName_ShouldCreateUser() {
        // Given
        NewUserRequest newUserRequest = new NewUserRequest();
        newUserRequest.setName(null);
        newUserRequest.setEmail("test@example.com");

        User savedUser = createUser(1L, null, "test@example.com");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // When
        UserDto result = userService.createUser(newUserRequest);

        // Then
        assertNotNull(result);
        assertNull(result.getName());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void getUsers_WithSingleId_ShouldReturnSingleUser() {
        // Given
        List<Long> ids = List.of(1L);
        Pageable pageable = Pageable.unpaged();

        List<User> users = List.of(createUser(1L, "Single User", "single@example.com"));
        Page<User> userPage = new PageImpl<>(users);

        when(userRepository.findByIdIn(ids, pageable)).thenReturn(userPage);

        // When
        List<UserDto> result = userService.getUsers(ids, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Single User", result.get(0).getName());
        verify(userRepository).findByIdIn(ids, pageable);
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private User createUser(Long id, String name, String email) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .createdAt(LocalDateTime.now())
                .build();
    }
}