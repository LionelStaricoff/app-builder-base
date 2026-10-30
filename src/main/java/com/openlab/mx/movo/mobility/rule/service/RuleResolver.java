package com.openlab.mx.movo.mobility.rule.service;

import com.openlab.mx.movo.mobility.rule.entity.CityRuleOverride;
import com.openlab.mx.movo.mobility.rule.entity.CountryRule;
import com.openlab.mx.movo.mobility.rule.repository.CityRuleOverrideRepository;
import com.openlab.mx.movo.mobility.rule.repository.CountryRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RuleResolver {

    private final CountryRuleRepository countryRules;
    private final CityRuleOverrideRepository cityOverrides;

    public RuleResolver(CountryRuleRepository countryRules,
                         CityRuleOverrideRepository cityOverrides) {
        this.countryRules = countryRules;
        this.cityOverrides = cityOverrides;
    }

    @Transactional(readOnly = true)
    public EffectiveRule resolve(String countryCode, String cityName) {
        if (!StringUtils.hasText(countryCode)) {
            throw new MissingCountryRuleException("(vacío)");
        }
        CountryRule country = countryRules.findByCountryCodeIgnoreCase(countryCode.trim())
                .orElseThrow(() -> new MissingCountryRuleException(countryCode));

        CityRuleOverride city = null;
        if (StringUtils.hasText(cityName)) {
            city = cityOverrides.findByCountryRuleIdAndCityNameIgnoreCase(country.getId(), cityName.trim())
                    .orElse(null);
        }

        String currency = (city != null && city.getCurrencyCode() != null)
                ? city.getCurrencyCode()
                : country.getCurrencyCode();

        java.math.BigDecimal minFare = (city != null && city.getMinFare() != null)
                ? city.getMinFare()
                : country.getMinFare();

        boolean personal = (city != null && city.getRequiresPersonalIdentity() != null)
                ? city.getRequiresPersonalIdentity()
                : country.isRequiresPersonalIdentity();

        boolean vehicle = (city != null && city.getRequiresVehicle() != null)
                ? city.getRequiresVehicle()
                : country.isRequiresVehicle();

        return new EffectiveRule(country.getCountryCode().toUpperCase(), cityName, currency, minFare, personal, vehicle);
    }

    /** Un conductor solo queda habilitable si existe regla del país applicable. */
    public boolean countryRuleExists(String countryCode) {
        return StringUtils.hasText(countryCode)
                && countryRules.existsByCountryCodeIgnoreCase(countryCode.trim());
    }
}