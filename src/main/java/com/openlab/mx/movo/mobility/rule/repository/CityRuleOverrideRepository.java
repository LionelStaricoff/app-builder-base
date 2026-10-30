package com.openlab.mx.movo.mobility.rule.repository;

import com.openlab.mx.movo.mobility.rule.entity.CityRuleOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CityRuleOverrideRepository extends JpaRepository<CityRuleOverride, Long> {
    Optional<CityRuleOverride> findByCountryRuleIdAndCityNameIgnoreCase(Long countryRuleId, String cityName);
}