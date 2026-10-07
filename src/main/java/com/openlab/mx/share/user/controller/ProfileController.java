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

    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("user", currentUser.require());
        return "profile/settings";
    }

    @PostMapping("/settings/update")
    public String update(@RequestParam String username,
                         @RequestParam(required = false) MultipartFile avatar,
                         RedirectAttributes redirectAttributes) throws IOException {
        User user = currentUser.require();
        String nuevo = username == null ? "" : username.trim();

        if (!nuevo.isEmpty() && !nuevo.equals(user.getUsername())) {
            if (userRepository.findByUsername(nuevo).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Ese nombre de usuario ya existe");
                return "redirect:/settings";
            }
            String anterior = user.getUsername();
            user.setUsername(nuevo);
            userRepository.save(user);
            sincronizarSesion(anterior, nuevo);
        }

        if (avatar != null && !avatar.isEmpty()) {
            if (avatar.getSize() > MAX_AVATAR_BYTES) {
                redirectAttributes.addFlashAttribute("error", "El avatar no puede superar 2 MB");
                return "redirect:/settings";
            }
            user.setAvatarBase64(Base64.getEncoder().encodeToString(avatar.getBytes()));
            userRepository.save(user);
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
