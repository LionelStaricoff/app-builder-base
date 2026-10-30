package com.openlab.mx.movo.mobility.rule.controller;

import com.openlab.mx.movo.mobility.rule.entity.CityRuleOverride;
import com.openlab.mx.movo.mobility.rule.entity.CountryRule;
import com.openlab.mx.movo.mobility.rule.repository.CityRuleOverrideRepository;
import com.openlab.mx.movo.mobility.rule.repository.CountryRuleRepository;
import com.openlab.mx.movo.shared.audit.AdminAuditService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@PreAuthorize("hasRole('ADMIN')")
@Controller
@RequestMapping("/admin/geo-rules")
public class GeoRuleAdminController {

    private final CountryRuleRepository countryRules;
    private final CityRuleOverrideRepository cityOverrides;

    private final AdminAuditService audit;

    public GeoRuleAdminController(CountryRuleRepository countryRules,
                                  CityRuleOverrideRepository cityOverrides,
                                  AdminAuditService audit) {
        this.countryRules = countryRules;
        this.cityOverrides = cityOverrides;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("countries", countryRules.findAll());
        model.addAttribute("cityOverrides", cityOverrides.findAll());
        return "admin/geo-rules";
    }

    @GetMapping("/new")
    public String newCountry(Model model) {
        model.addAttribute("country", new CountryRule());
        return "admin/country-rule-form";
    }

    @PostMapping
    public String saveCountry(@Valid @ModelAttribute("country") CountryRule country,
                              BindingResult binding,
                              Model model,
                              RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("country", country);
            return "admin/country-rule-form";
        }
        countryRules.save(country);
        audit.record("GEO_RULE_SAVED", "CountryRule", String.valueOf(country.getId()), country.getCountryCode());
        ra.addFlashAttribute("message", "Regla de país guardada");
        return "redirect:/admin/geo-rules";
    }

    @GetMapping("/{id}/city/new")
    public String newCity(@PathVariable Long id, Model model) {
        CountryRule country = countryRules.findById(id).orElseThrow();
        CityRuleOverride override = CityRuleOverride.builder().countryRule(country).build();
        model.addAttribute("override", override);
        model.addAttribute("country", country);
        return "admin/city-rule-form";
    }

    @PostMapping("/{id}/city")
    public String saveCity(@PathVariable Long id,
                           @Valid @ModelAttribute("override") CityRuleOverride override,
                           BindingResult binding,
                           Model model,
                           RedirectAttributes ra) {
        CountryRule country = countryRules.findById(id).orElseThrow();
        override.setCountryRule(country);
        if (binding.hasErrors()) {
            model.addAttribute("override", override);
            model.addAttribute("country", country);
            return "admin/city-rule-form";
        }
        cityOverrides.save(override);
        audit.record("GEO_CITY_OVERRIDE_SAVED", "CityRuleOverride", String.valueOf(override.getId()),
                country.getCountryCode() + "/" + override.getCityName());
        ra.addFlashAttribute("message", "Override de ciudad guardado");
        return "redirect:/admin/geo-rules";
    }
}