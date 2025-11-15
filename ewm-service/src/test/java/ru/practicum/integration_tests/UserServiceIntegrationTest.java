package ru.practicum.integration_tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.NewUserRequest;
import ru.practicum.dto.UserDto;
import ru.practicum.exception.NotFoundException;
import ru.practicum.model.User;
import ru.practicum.repository.UserRepository;
import ru.practicum.service.UserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    // ========== ИНТЕГРАЦИОННЫЕ ТЕСТЫ ДЛЯ СОЗДАНИЯ ПОЛЬЗОВАТЕЛЕЙ ==========

    @Test
    void createUser_WithValidData_ShouldCreateAndSaveUser() {
        // Given
        NewUserRequest newUserRequest = new NewUserRequest();
        newUserRequest.setName("Integration User");
        newUserRequest.setEmail("integration@example.com");

        // When
        UserDto result = userService.createUser(newUserRequest);

        // Then
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("Integration User", result.getName());
        assertEquals("integration@example.com", result.getEmail());

        // Проверяем, что пользователь действительно сохранен в БД
        User savedUser = userRepository.findById(result.getId()).orElse(null);
        assertNotNull(savedUser);
        assertEquals("Integration User", savedUser.getName());
        assertEquals("integration@example.com", savedUser.getEmail());
        assertNotNull(savedUser.getCreatedAt());
    }


    // ========== ИНТЕГРАЦИОННЫЕ ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ ПОЛЬЗОВАТЕЛЕЙ ==========

    @Test
    void getUsers_WithEmptyIds_ShouldReturnAllUsers() {
        // Given - создаем тестовых пользователей
        createTestUser("User1", "user1@example.com");
        createTestUser("User2", "user2@example.com");
        createTestUser("User3", "user3@example.com");

        Pageable pageable = Pageable.unpaged();

        // When
        List<UserDto> result = userService.getUsers(null, pageable);

        // Then
        assertNotNull(result);
        assertTrue(result.size() >= 3); // Могут быть другие пользователи в БД
        assertTrue(result.stream().anyMatch(user -> "User1".equals(user.getName())));
        assertTrue(result.stream().anyMatch(user -> "User2".equals(user.getName())));
        assertTrue(result.stream().anyMatch(user -> "User3".equals(user.getName())));
    }

    @Test
    void getUsers_WithSpecificIds_ShouldReturnFilteredUsers() {
        // Given - создаем тестовых пользователей
        UserDto user1 = createTestUser("Filtered1", "filtered1@example.com");
        UserDto user2 = createTestUser("Filtered2", "filtered2@example.com");
        createTestUser("Filtered3", "filtered3@example.com"); // Этот не должен попасть в результат

        List<Long> ids = List.of(user1.getId(), user2.getId());
        Pageable pageable = Pageable.unpaged();

        // When
        List<UserDto> result = userService.getUsers(ids, pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(user -> user.getId().equals(user1.getId())));
        assertTrue(result.stream().anyMatch(user -> user.getId().equals(user2.getId())));
        assertTrue(result.stream().noneMatch(user -> "Filtered3".equals(user.getName())));
    }

    @Test
    void getUsers_WithPagination_ShouldReturnPaginatedResults() {
        // Given - создаем несколько пользователей
        for (int i = 1; i <= 5; i++) {
            createTestUser("PageUser" + i, "pageuser" + i + "@example.com");
        }

        Pageable pageable = PageRequest.of(0, 2); // первая страница, 2 элемента

        // When
        List<UserDto> result = userService.getUsers(null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size()); // Должны получить ровно 2 пользователя
    }

    @Test
    void getUsers_WithEmptyListIds_ShouldReturnAllUsers() {
        // Given - создаем тестовых пользователей
        createTestUser("EmptyList1", "emptylist1@example.com");
        createTestUser("EmptyList2", "emptylist2@example.com");

        List<Long> ids = List.of(); // пустой список
        Pageable pageable = Pageable.unpaged();

        // When
        List<UserDto> result = userService.getUsers(ids, pageable);

        // Then
        assertNotNull(result);
        assertTrue(result.size() >= 2); // Должны получить всех пользователей
    }

    // ========== ИНТЕГРАЦИОННЫЕ ТЕСТЫ ДЛЯ ПОЛУЧЕНИЯ ПОЛЬЗОВАТЕЛЯ ПО ID ==========

    @Test
    void getUserById_WithExistingUser_ShouldReturnUser() {
        // Given
        UserDto createdUser = createTestUser("GetByIdUser", "getbyid@example.com");

        // When
        UserDto result = userService.getUserById(createdUser.getId());

        // Then
        assertNotNull(result);
        assertEquals(createdUser.getId(), result.getId());
        assertEquals("GetByIdUser", result.getName());
        assertEquals("getbyid@example.com", result.getEmail());
    }

    @Test
    void getUserById_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long nonExistingId = 999999L;

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userService.getUserById(nonExistingId));

        assertEquals("User with id=999999 was not found", exception.getMessage());
    }

    // ========== ИНТЕГРАЦИОННЫЕ ТЕСТЫ ДЛЯ УДАЛЕНИЯ ПОЛЬЗОВАТЕЛЕЙ ==========

    @Test
    void deleteUser_WithExistingUser_ShouldDeleteUser() {
        // Given
        UserDto userToDelete = createTestUser("ToDelete", "todelete@example.com");
        Long userId = userToDelete.getId();

        // Проверяем, что пользователь существует
        assertTrue(userRepository.existsById(userId));

        // When
        userService.deleteUser(userId);

        // Then - пользователь должен быть удален из БД
        assertFalse(userRepository.existsById(userId));

        // Попытка получить удаленного пользователя должна упасть
        assertThrows(NotFoundException.class, () -> userService.getUserById(userId));
    }

    @Test
    void deleteUser_WithNonExistingUser_ShouldThrowNotFoundException() {
        // Given
        Long nonExistingId = 888888L;

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userService.deleteUser(nonExistingId));

        assertEquals("User with id=888888 was not found", exception.getMessage());
    }

    @Test
    void deleteUser_AndThenCreateNewUser_ShouldWorkCorrectly() {
        // Given
        UserDto user = createTestUser("CycleUser", "cycle@example.com");
        Long userId = user.getId();

        // When - удаляем пользователя
        userService.deleteUser(userId);

        // Then - создаем нового пользователя с тем же email (должно работать)
        NewUserRequest newUserRequest = new NewUserRequest();
        newUserRequest.setName("New Cycle User");
        newUserRequest.setEmail("cycle@example.com");

        UserDto newUser = userService.createUser(newUserRequest);

        assertNotNull(newUser);
        assertNotEquals(userId, newUser.getId()); // Должен быть новый ID
        assertEquals("New Cycle User", newUser.getName());
        assertEquals("cycle@example.com", newUser.getEmail());
    }

    // ========== ИНТЕГРАЦИОННЫЕ ТЕСТЫ ДЛЯ КОМБИНИРОВАННЫХ СЦЕНАРИЕВ ==========

    @Test
    void createGetAndDeleteUser_IntegrationFlow_ShouldWorkCorrectly() {
        // Phase 1: Create
        NewUserRequest newUserRequest = new NewUserRequest();
        newUserRequest.setName("Flow User");
        newUserRequest.setEmail("flow@example.com");

        UserDto createdUser = userService.createUser(newUserRequest);
        assertNotNull(createdUser.getId());

        // Phase 2: Get by ID
        UserDto retrievedUser = userService.getUserById(createdUser.getId());
        assertEquals(createdUser.getId(), retrievedUser.getId());
        assertEquals("Flow User", retrievedUser.getName());

        // Phase 3: Get in list
        List<UserDto> users = userService.getUsers(List.of(createdUser.getId()), Pageable.unpaged());
        assertEquals(1, users.size());
        assertEquals(createdUser.getId(), users.get(0).getId());

        // Phase 4: Delete
        userService.deleteUser(createdUser.getId());

        // Phase 5: Verify deletion
        assertThrows(NotFoundException.class, () -> userService.getUserById(createdUser.getId()));

        List<UserDto> usersAfterDeletion = userService.getUsers(List.of(createdUser.getId()), Pageable.unpaged());
        assertTrue(usersAfterDeletion.isEmpty());
    }

    @Test
    void getUsers_WithMixedExistingAndNonExistingIds_ShouldReturnOnlyExisting() {
        // Given
        UserDto existingUser1 = createTestUser("Mixed1", "mixed1@example.com");
        UserDto existingUser2 = createTestUser("Mixed2", "mixed2@example.com");

        Long nonExistingId1 = 111111L;
        Long nonExistingId2 = 222222L;

        List<Long> mixedIds = List.of(existingUser1.getId(), nonExistingId1, existingUser2.getId(), nonExistingId2);

        // When
        List<UserDto> result = userService.getUsers(mixedIds, Pageable.unpaged());

        // Then - должны получить только существующих пользователей
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(user -> user.getId().equals(existingUser1.getId())));
        assertTrue(result.stream().anyMatch(user -> user.getId().equals(existingUser2.getId())));
        assertTrue(result.stream().noneMatch(user -> user.getId().equals(nonExistingId1)));
        assertTrue(result.stream().noneMatch(user -> user.getId().equals(nonExistingId2)));
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private UserDto createTestUser(String name, String email) {
        NewUserRequest request = new NewUserRequest();
        request.setName(name);
        request.setEmail(email);
        return userService.createUser(request);
    }
}