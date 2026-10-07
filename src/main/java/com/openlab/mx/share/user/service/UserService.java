package com.openlab.mx.share.user.service;

import com.openlab.mx.share.shared.exception.DuplicateUsernameException;
import com.openlab.mx.share.shared.exception.MenorDeEdadException;
import com.openlab.mx.share.user.dto.RegisterRequest;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User register(RegisterRequest request) {
        if (age(request.birthDate()) < 18) {
            throw new MenorDeEdadException();
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUsernameException();
        }
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .birthDate(request.birthDate())
                .role(Role.USER)
                .spPoints(0)
                .build();
        return userRepository.save(user);
    }

    private int age(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
