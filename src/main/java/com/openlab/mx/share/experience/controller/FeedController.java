package com.openlab.mx.share.experience.controller;

import com.openlab.mx.share.experience.dto.ExperienceFeedDTO;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.service.FeedService;
import com.openlab.mx.share.shared.CurrentUser;
import com.openlab.mx.share.user.entity.Role;
import com.openlab.mx.share.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feed: render inicial de la página 0 (Thymeleaf) y las páginas siguientes
 * para el scroll infinito.
 *
 * <p>El scroll infinito pide HTML ya renderizado a {@code /feed/cards}, no
 * JSON: la card vive en un solo lugar ({@code fragments/feed-card.html}), así
 * que el servidor manda exactamente lo mismo que ya renderizó para la página 0.
 *
 * <p>El navegador puede mandar lat/lng (GPS) que pisan los del perfil.
 */
@Controller
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final CurrentUser currentUser;

    @GetMapping("/feed")
    public String feed(@RequestParam(required = false) ExperienceCategory category,
                       @RequestParam(required = false) String city,
                       @RequestParam(required = false) String country,
                       Model model) {
        User user = currentUser.get();
        // Onboarding de intereses: el login entra directo acá
        // (defaultSuccessUrl("/feed", true)), así que este es el único punto de
        // gate. /feed/cards NO se gatea: el scroll infinito necesita el HTML.
        if (user != null && !user.isInterestsOnboarded()) {
            return "redirect:/onboarding/intereses";
        }
        // Búsqueda manual de otro lugar (país y/o ciudad) pisa la ciudad del perfil.
        String effectiveCity = ciudadEfectiva(user, city);
        Page<ExperienceFeedDTO> feedPage = pagina(user, 0, category, country, city, null, null);
        model.addAttribute("feedPage", feedPage);
        model.addAttribute("categories", ExperienceCategory.values());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedCountry", country);
        model.addAttribute("countries", feedService.countryOptions());
        model.addAttribute("searchedCity", effectiveCity);
        model.addAttribute("searchedQuery", city != null && !city.isBlank() ? city.trim() : null);
        model.addAttribute("savedCity", user != null ? user.getCity() : null);
        model.addAttribute("savedCountry", user != null ? user.getLocationCountry() : null);
        model.addAttribute("savedLat", user != null ? user.getLat() : null);
        model.addAttribute("savedLng", user != null ? user.getLng() : null);
        // Publicar exige KYC completado, y completarlo promueve a CREATOR
        // (DiditWebhookService). El CTA del empty state se muestra según esto:
        // sin el atributo, un USER vería un botón que lo rebota al feed.
        model.addAttribute("puedePublicar", user != null && user.getRole() == Role.CREATOR);
        // "Activado" = hay ubicación guardada (coords o ciudad), no que se haya
        // resuelto la ciudad: el GPS puede no traer ciudad y igual estar conectado.
        model.addAttribute("hasLocation", user != null
                && (user.getLat() != null || user.getCity() != null));
        return "feed";
    }

    /**
     * HTML de las cards de una página, para el scroll infinito.
     *
     * <p>Devuelve HTML y no JSON a propósito: la card vive en
     * {@code fragments/feed-card.html} y el servidor manda exactamente lo
     * mismo que ya renderizó para la página 0. Antes el JS reconstruía el
     * markup en 190 líneas que se desincronizaron en silencio: clases de
     * carrusel viejas, un {@code meetingPointLabel} que el DTO no tiene, y
     * otro cálculo de ocupación. Ahora el markup existe una sola vez.
     *
     * <p>El view name usa la sintaxis {@code template :: selector}: Spring la
     * parsea en {@code ThymeleafView.renderFragment} (busca {@code "::"} en el
     * nombre) y renderiza sólo ese fragmento, sin {@code <html>} ni
     * {@code <body>}. Ojo: {@code TemplateEngine.process(String, ...)} NO
     * interpreta ese atajo — arma el TemplateSpec con el string entero como
     * nombre de template.
     *
     * <p>Cuerpo vacío significa "no hay más páginas": el JS corta ahí y no
     * necesita un contrato extra ni adivinar por el tamaño de un array.
     */
    @GetMapping("/feed/cards")
    public String feedCards(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) ExperienceCategory category,
                            @RequestParam(required = false) String country,
                            @RequestParam(required = false) String city,
                            @RequestParam(required = false) Double lat,
                            @RequestParam(required = false) Double lng,
                            Model model) {
        model.addAttribute("page", pagina(currentUser.get(), page, category, country, city, lat, lng));
        return "fragments/feed-card :: cards";
    }

    /**
     * Filtros que llegan del navegador resueltos contra el perfil: si el
     * request no trae ubicación, se usa la guardada; si trae, la pisa.
     */
    private Page<ExperienceFeedDTO> pagina(User user, int page, ExperienceCategory category,
                                          String country, String city, Double lat, Double lng) {
        return feedService.page(
                user != null ? user.getId() : null,
                ciudadEfectiva(user, city),
                lat != null ? lat : (user != null ? user.getLat() : null),
                lng != null ? lng : (user != null ? user.getLng() : null),
                category, country, city, page);
    }

    private static String ciudadEfectiva(User user, String city) {
        return city != null && !city.isBlank() ? city.trim() : (user != null ? user.getCity() : null);
    }
}
