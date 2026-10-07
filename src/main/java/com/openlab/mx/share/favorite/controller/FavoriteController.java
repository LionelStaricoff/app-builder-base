package com.openlab.mx.share.favorite.controller;

import com.openlab.mx.share.favorite.service.FavoriteService;
import com.openlab.mx.share.shared.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentUser currentUser;

    @GetMapping("/favorites")
    public String favorites(Model model) {
        var user = currentUser.require();
        model.addAttribute("favorites", favoriteService.listFavorites(user));
        model.addAttribute("lived", java.util.List.of()); // se llena en Task 5 (Experiencias Vividas)
        return "favorites/favorites";
    }

    @PostMapping("/favorites/{id}/add")
    public String add(@PathVariable Long id) {
        favoriteService.add(id, currentUser.require());
        return CurrentUser.backOr("/favorites");
    }

    @PostMapping("/favorites/{id}/remove")
    public String remove(@PathVariable Long id) {
        favoriteService.remove(id, currentUser.require());
        return CurrentUser.backOr("/favorites");
    }
}
