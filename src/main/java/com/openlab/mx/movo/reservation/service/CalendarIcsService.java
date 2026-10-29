package com.openlab.mx.movo.reservation.service;

import com.openlab.mx.movo.reservation.entity.Reservation;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Genera el archivo .ics de una reserva ("añadir a mi calendario").
 *
 * Horas flotantes (sin Z ni TZID): DTSTART/DTEND salen con la fecha y el
 * horario tal como los guarda la instancia y el cliente los interpreta
 * como hora local, así no hay que convertir zonas.
 */
@Service
public class CalendarIcsService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public String build(Reservation reserva) {
        var fecha = reserva.getInstance();
        var experiencia = fecha.getExperience();
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n")
          .append("VERSION:2.0\r\n")
          .append("PRODID:-//Shared//Experiencias locales//ES\r\n")
          .append("CALSCALE:GREGORIAN\r\n")
          .append("BEGIN:VEVENT\r\n")
          .append("UID:reserva-").append(reserva.getId()).append("@shared\r\n")
          .append("DTSTAMP:").append(ZonedDateTime.now(ZoneOffset.UTC).format(UTC)).append("\r\n")
          .append("DTSTART:").append(fecha.getDate().format(DIA)).append('T')
          .append(fecha.getStartTime().format(HORA)).append("\r\n")
          .append("DTEND:").append(fecha.getDate().format(DIA)).append('T')
          .append(fecha.getEndTime().format(HORA)).append("\r\n")
          .append("SUMMARY:").append(escapar(experiencia.getName())).append(" (Shared)\r\n");
        if (experiencia.getMeetingLat() != null && experiencia.getMeetingLng() != null) {
            sb.append("LOCATION:")
              .append(escapar(experiencia.getMeetingLat() + "," + experiencia.getMeetingLng()))
              .append("\r\n");
        }
        sb.append("END:VEVENT\r\n")
          .append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    /** RFC 5545 §3.3.11: en TEXT escapan barra invertida, ; , y saltos de línea. */
    private String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }
}
