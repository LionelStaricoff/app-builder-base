package com.openlab.mx.share.user.controller;

import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.shared.exception.DomainException;
import com.openlab.mx.share.user.entity.User;
import com.openlab.mx.share.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Base64;

/**
 * Configuración (spec §6.5): nombre, avatar base64, vista de SP y logout.
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024;

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("user", currentUser.require());
        return "profile/settings";
    }

    @PostMapping("/settings/update")
    public String update(@RequestParam String username,
                         @RequestParam(required = false) String country,
                         @RequestParam(required = false) String currencyCode,
                         @RequestParam(required = false) MultipartFile avatar,
                         RedirectAttributes redirectAttributes) throws IOException {
        User user = currentUser.require();
        String nuevo = username == null ? "" : username.trim();

        // FASE 1 — validar todo sin persistir nada (el form es atómico:
        // o se guarda todo o no se guarda nada, sin estados a medias).
        boolean usernameChanged = !nuevo.isEmpty() && !nuevo.equals(user.getUsername());
        if (usernameChanged && userRepository.findByUsername(nuevo).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Ese nombre de usuario ya existe");
            return "redirect:/settings";
        }

        String nuevoAvatarBase64 = null;
        boolean avatarChanged = false;
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.getSize() > MAX_AVATAR_BYTES) {
                redirectAttributes.addFlashAttribute("error", "El avatar no puede superar 5 MB");
                return "redirect:/settings";
            }
            nuevoAvatarBase64 = Base64.getEncoder().encodeToString(avatar.getBytes());
            avatarChanged = true;
        }

        String nuevoCountry = null;
        boolean countryChanged = country != null && !country.isBlank()
                && !country.trim().equalsIgnoreCase(
                        user.getCountry() == null ? "" : user.getCountry());
        if (countryChanged) {
            nuevoCountry = country.trim();
        }
        String normalizedCurrency = currencyCode == null || currencyCode.isBlank()
                ? null
                : com.openlab.mx.share.user.service.UserService.normalizeCurrency(currencyCode);
        boolean currencyChanged = normalizedCurrency != null
                && !normalizedCurrency.equals(user.getCurrencyCode());

        if (!usernameChanged && !avatarChanged && !countryChanged && !currencyChanged) {
            redirectAttributes.addFlashAttribute("ok", "Sin cambios para guardar");
            return "redirect:/settings";
        }

        // FASE 2 — un solo save con todo lo validado.
        String anterior = user.getUsername();
        if (usernameChanged) {
            user.setUsername(nuevo);
        }
        if (avatarChanged) {
            user.setAvatarBase64(nuevoAvatarBase64);
        }
        if (countryChanged) {
            user.setCountry(nuevoCountry);
        }
        if (currencyChanged) {
            user.setCurrencyCode(normalizedCurrency);
        }
        userRepository.save(user);
        if (usernameChanged) {
            sincronizarSesion(anterior, nuevo);
        }

        redirectAttributes.addFlashAttribute("ok", "Perfil actualizado");
        return "redirect:/settings";
    }

    @PostMapping("/settings/logout")
    public String logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return "redirect:/login";
    }

    /** El usuario autenticado se resuelve por username: hay que renombrarlo también en la sesión. */
    private void sincronizarSesion(String anterior, String nuevo) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && anterior.equals(authentication.getName())) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(nuevo, authentication.getCredentials(),
                            authentication.getAuthorities()));
        }
    }
}
