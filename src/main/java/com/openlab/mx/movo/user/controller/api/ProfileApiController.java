package com.openlab.mx.movo.user.controller.api;

import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.user.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST read-only del perfil autenticado para un front separado.
 * No expone password ni campos internos sensibles.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileApiController {

    private final CurrentUser currentUser;

    public ProfileApiController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public record UserProfileResponse(
            Long id,
            String username,
            String email,
            String avatarBase64,
            String city,
            String country,
            String currencyCode,
            String role,
            int spPoints,
            boolean interestsOnboarded,
            boolean proposalOnboarded) {
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> me() {
        User u = currentUser.require();
        return ResponseEntity.ok(new UserProfileResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getAvatarBase64(),
                u.getCity(),
                u.getCountry(),
                u.getCurrencyCode(),
                u.getRole() == null ? null : u.getRole().name(),
                u.getSpPoints(),
                u.isInterestsOnboarded(),
                u.isProposalOnboarded()));
    }
}
