package com.openlab.mx.share.user.service;

import com.openlab.mx.share.user.dto.RegisterRequest;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import com.openlab.mx.share.shared.exception.DuplicateUsernameException;
import com.openlab.mx.share.shared.exception.MenorDeEdadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void testRegistraUsuarioMayorDeEdad() {
        when(userRepository.existsByUsername("lionel")).thenReturn(false);
        when(passwordEncoder.encode("Clave1234!")).thenReturn("hash-bcrypt");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User u = userService.register(new RegisterRequest(
                "lionel", "lionel@x.com", "Clave1234!", LocalDate.of(1990, 5, 10)));

        assertEquals("lionel", u.getUsername());
        assertEquals("hash-bcrypt", u.getPasswordHash());
        assertEquals(Role.USER, u.getRole());
        assertNotNull(u.getBirthDate());
    }

    @Test
    void testRechazaMenorDe18() {
        RegisterRequest menor = new RegisterRequest(
                "kid", "kid@x.com", "Clave1234!", LocalDate.now().minusYears(10));

        assertThrows(MenorDeEdadException.class, () -> userService.register(menor));
    }

    @Test
    void testRechazaUsernameDuplicado() {
        when(userRepository.existsByUsername("dup")).thenReturn(true);

        assertThrows(DuplicateUsernameException.class, () -> userService.register(
                new RegisterRequest("dup", "dup@x.com", "Clave1234!", LocalDate.of(1990, 1, 1))));
    }
}
