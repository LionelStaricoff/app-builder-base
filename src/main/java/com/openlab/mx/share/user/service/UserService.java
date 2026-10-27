package com.openlab.mx.share.user.service;

import com.openlab.mx.share.shared.exception.DuplicateEmailException;
import com.openlab.mx.share.shared.exception.DuplicateUsernameException;
import com.openlab.mx.share.shared.exception.MenorDeEdadException;
import com.openlab.mx.share.user.dto.RegisterRequest;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.ZoneId;import java.time.LocalDate;
import java.time.ZoneId;import java.time.Period;

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
        // Sin esta verificación el insert pega contra el unique de email y la
        // excepción llega como 500 en vez de un error en el formulario.
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .birthDate(request.birthDate())
                .country(normalizeCountry(request.country()))
                .currencyCode(normalizeCurrency(request.currencyCode()))
                .role(Role.USER)
                .spPoints(0)
                .build();
        return userRepository.save(user);
    }

    private int age(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now(ZoneId.systemDefault())).getYears();
    }

    /** Cuentas viejas o tests sin país: default México para no romper. */
    public static String normalizeCountry(String country) {
        return country == null || country.isBlank() ? "México" : country.trim();
    }

    /** Normaliza a ISO en mayúsculas; default MXN. */
    public static String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return "MXN";
        String code = currencyCode.trim().toUpperCase();
        return code.length() == 3 ? code : "MXN";
    }
}
