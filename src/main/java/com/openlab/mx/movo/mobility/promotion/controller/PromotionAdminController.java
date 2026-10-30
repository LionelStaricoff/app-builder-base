package com.openlab.mx.movo.mobility.promotion.controller;

import com.openlab.mx.movo.mobility.promotion.entity.Discount;
import com.openlab.mx.movo.mobility.promotion.entity.DiscountStatus;
import com.openlab.mx.movo.mobility.promotion.entity.DiscountType;
import com.openlab.mx.movo.mobility.promotion.entity.Promotion;
import com.openlab.mx.movo.mobility.promotion.entity.PromotionStatus;
import com.openlab.mx.movo.mobility.promotion.repository.DiscountRepository;
import com.openlab.mx.movo.mobility.promotion.repository.PromotionRepository;
import com.openlab.mx.movo.shared.audit.AdminAuditService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

@PreAuthorize("hasRole('ADMIN')")
@Controller
@RequestMapping("/admin/promotions")
public class PromotionAdminController {

    private final PromotionRepository promotions;
    private final DiscountRepository discounts;

    private final AdminAuditService audit;

    public PromotionAdminController(PromotionRepository promotions, DiscountRepository discounts, AdminAuditService audit) {
        this.promotions = promotions;
        this.discounts = discounts;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("promotions", promotions.findAll());
        model.addAttribute("discounts", discounts.findAll());
        return "admin/promotions";
    }

    @GetMapping("/new")
    public String newPromotion(Model model) {
        model.addAttribute("promotion", new Promotion());
        return "admin/promotion-form";
    }

    @PostMapping
    public String save(@ModelAttribute Promotion promotion, RedirectAttributes ra) {
        promotions.save(promotion);
        audit.record("PROMOTION_SAVED", "Promotion", String.valueOf(promotion.getId()), promotion.getName());
        ra.addFlashAttribute("message", "Promoción guardada");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes ra) {
        Promotion p = promotions.findById(id).orElseThrow();
        p.setStatus(PromotionStatus.ACTIVE);
        promotions.save(p);
        audit.record("PROMOTION_ACTIVATED", "Promotion", String.valueOf(id), p.getName());
        ra.addFlashAttribute("message", "Promoción activada");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/{id}/expire")
    public String expire(@PathVariable Long id, RedirectAttributes ra) {
        Promotion p = promotions.findById(id).orElseThrow();
        p.setStatus(PromotionStatus.EXPIRED);
        promotions.save(p);
        audit.record("PROMOTION_EXPIRED", "Promotion", String.valueOf(id), p.getName());
        ra.addFlashAttribute("message", "Promoción expirada");
        return "redirect:/admin/promotions";
    }

    @GetMapping("/discounts/new")
    public String newDiscount(Model model) {
        model.addAttribute("discount", new Discount());
        return "admin/discount-form";
    }

    @PostMapping("/discounts")
    public String saveDiscount(@ModelAttribute Discount discount, RedirectAttributes ra) {
        discounts.save(discount);
        audit.record("DISCOUNT_SAVED", "Discount", String.valueOf(discount.getId()), discount.getType() == null ? null : discount.getType().name());
        ra.addFlashAttribute("message", "Descuento guardado");
        return "redirect:/admin/promotions";
    }

    @PostMapping("/discounts/{id}/disable")
    public String disableDiscount(@PathVariable Long id, RedirectAttributes ra) {
        Discount d = discounts.findById(id).orElseThrow();
        d.setStatus(DiscountStatus.DISABLED);
        discounts.save(d);
        audit.record("DISCOUNT_DISABLED", "Discount", String.valueOf(id), null);
        ra.addFlashAttribute("message", "Descuento deshabilitado");
        return "redirect:/admin/promotions";
    }
}