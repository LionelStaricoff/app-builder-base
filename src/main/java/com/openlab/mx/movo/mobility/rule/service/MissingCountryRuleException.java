package com.openlab.mx.movo.mobility.rule.service;

public class MissingCountryRuleException extends RuntimeException {
    public MissingCountryRuleException(String countryCode) {
        super("No existe regla de movilidad para el país " + countryCode
                + ": no se puede habilitar la operación ni inventar requisitos");
    }
}