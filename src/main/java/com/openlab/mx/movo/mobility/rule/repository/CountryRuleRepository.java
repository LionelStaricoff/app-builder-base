package com.openlab.mx.movo.mobility.rule.repository;

import com.openlab.mx.movo.mobility.rule.entity.CountryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CountryRuleRepository extends JpaRepository<CountryRule, Long> {
    Optional<CountryRule> findByCountryCodeIgnoreCase(String countryCode);
    boolean existsByCountryCodeIgnoreCase(String countryCode);
}