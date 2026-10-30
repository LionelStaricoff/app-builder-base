package com.openlab.mx.movo.user.controller;

import com.openlab.mx.movo.shared.CurrentUser;
import com.openlab.mx.movo.user.dto.ProfileUpdateRequest;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Base64;
import java.util.List;


/**
 * Configuración (spec §6.5): nombre, avatar base64, vista de SP y logout.
 */
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    /** Atributos flash y destino de vuelta de la pagina de ajustes. */
    private static final String FLASH_ERROR = "error";
    private static final String FLASH_OK = "ok";
    private static final String REDIRECT_SETTINGS = "redirect:/settings";

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    @GetMapping("/settings")
    public String settings(Model model) {
        User user = currentUser.require();
        model.addAttribute("user", user);
        model.addAttribute("intereses", List.of());
        return "profile/settings";
    }

    @PostMapping("/settings/update")
    public String update(@Valid @ModelAttribute ProfileUpdateRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) throws IOException {
        User user = currentUser.require();
        // Campos fuera de rango (nombre > 30, país > 60, moneda > 3) → no se guarda nada.
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR,
                    "Revisá los datos: nombre máximo 30 caracteres, país 60 y moneda 3");
            return REDIRECT_SETTINGS;
        }
        String username = request.username();
        String country = request.country();
        String currencyCode = request.currencyCode();
        MultipartFile avatar = request.avatar();
        String nuevo = username == null ? "" : username.trim();

        // FASE 1 — validar todo sin persistir nada (el form es atómico:
        // o se guarda todo o no se guarda nada, sin estados a medias).
        boolean usernameChanged = !nuevo.isEmpty() && !nuevo.equals(user.getUsername());
        if (usernameChanged && userRepository.findByUsername(nuevo).isPresent()) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, "Ese nombre de usuario ya existe");
            return REDIRECT_SETTINGS;
        }

        String nuevoAvatarBase64 = null;
        boolean avatarChanged = false;
        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.getSize() > MAX_AVATAR_BYTES) {
                redirectAttributes.addFlashAttribute(FLASH_ERROR, "El avatar no puede superar 5 MB");
                return REDIRECT_SETTINGS;
            }
            byte[] normalized =
                    com.openlab.mx.movo.user.service.AvatarImage.normalize(avatar.getBytes());
            nuevoAvatarBase64 = Base64.getEncoder().encodeToString(normalized);
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
                : com.openlab.mx.movo.user.service.UserService.normalizeCurrency(currencyCode);
        boolean currencyChanged = normalizedCurrency != null
                && !normalizedCurrency.equals(user.getCurrencyCode());

        if (!usernameChanged && !avatarChanged && !countryChanged && !currencyChanged) {
            redirectAttributes.addFlashAttribute(FLASH_OK, "Sin cambios para guardar");
            return REDIRECT_SETTINGS;
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

        redirectAttributes.addFlashAttribute(FLASH_OK, "Perfil actualizado");
        return REDIRECT_SETTINGS;
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
