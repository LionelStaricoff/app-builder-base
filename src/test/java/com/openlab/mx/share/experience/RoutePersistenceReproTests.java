package com.openlab.mx.share.experience;

import com.openlab.mx.share.experience.dto.ExperienceFormRequest;
import com.openlab.mx.share.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.share.experience.entity.Experience;
import com.openlab.mx.share.experience.entity.ExperienceCategory;
import com.openlab.mx.share.experience.entity.RecurrenceType;
import com.openlab.mx.share.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.share.experience.repository.ExperienceStopRepository;
import com.openlab.mx.share.experience.service.ExperienceService;
import com.openlab.mx.share.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reproducción del reporte: paradas/fechas que no se guardan ni se muestran, edición que pierde datos. */
@SpringBootTest
@AutoConfigureMockMvc
class RoutePersistenceReproTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExperienceStopRepository stopRepository;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;
    @Autowired
    private com.openlab.mx.share.experience.service.FeedService feedService;
    @Autowired
    private com.openlab.mx.share.experience.repository.ExperienceImageRepository imageRepository;
    @Autowired
    private org.thymeleaf.spring6.SpringTemplateEngine templateEngine;

    private String fechaFutura() {
        return LocalDate.now().plusDays(30).toString();
    }

    @Test
    @WithMockUser("demo_creator")
    void crearConParadasPersisteParadasYFechas() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        MvcResult result = mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "Repro ruta " + UUID.randomUUID())
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "150")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "250.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", fechaFutura())
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .param("stopPlace", "Mirador del Cerro")
                        .param("stopPlace", "Playa Escondida")
                        .param("stopLat", "19.4400")
                        .param("stopLat", "19.4500")
                        .param("stopLng", "-99.1400")
                        .param("stopLng", "-99.1500")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/experiences/*"))
                .andReturn();

        Long id = Long.valueOf(result.getResponse().getRedirectedUrl().replace("/experiences/", ""));

        assertEquals(2, stopRepository.findByExperienceIdOrderByPositionAscIdAsc(id).size(),
                "las 2 paradas del POST tienen que estar en la base");
        assertFalse(instanceRepository.findByExperienceIdOrderByDateAsc(id).isEmpty(),
                "tiene que existir al menos una instancia futura");

        mockMvc.perform(get("/experiences/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Recorrido")))
                .andExpect(content().string(containsString("Mirador del Cerro")))
                .andExpect(content().string(containsString("Playa Escondida")));

        // El feed (página server + API del scroll infinito) muestra recorrido y fecha.
        var dto = feedService.dtoFor(
                experienceService.get(id),
                userRepository.findByUsername("demo_creator").orElseThrow().getId());
        assertEquals(2, dto.stops().size(), "el DTO del feed lleva las 2 paradas");
        org.junit.jupiter.api.Assertions.assertNotNull(dto.nextDate(), "el DTO del feed lleva próxima fecha");
        org.junit.jupiter.api.Assertions.assertTrue(dto.stopsJson() != null && dto.stopsJson().contains("Mirador"),
                "el DTO del feed lleva el JSON del recorrido");

        // La API del scroll infinito expone paradas y fecha en JSON.
        String nombre = dto.name();
        boolean encontrado = false;
        for (int page = 0; page < 10 && !encontrado; page++) {
            MvcResult api = mockMvc.perform(get("/feed/api").param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andReturn();
            String json = api.getResponse().getContentAsString();
            if (json.contains(nombre)) {
                encontrado = true;
                org.junit.jupiter.api.Assertions.assertTrue(json.contains("Mirador del Cerro"),
                        "el JSON del feed lleva las paradas");
                org.junit.jupiter.api.Assertions.assertTrue(json.contains(dto.nextDate().toString()),
                        "el JSON del feed lleva la próxima fecha");
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(encontrado, "la experiencia aparece en el feed");
    }

    @Test
    @WithMockUser("demo_creator")
    void editarMuestraDatosCargados() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        String nombre = "Repro edit " + UUID.randomUUID();
        Experience experience = experienceService.create(new ExperienceFormRequest(
                        nombre, "Desc", 120, 2, 8, BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                        19.4326, -99.1332,
                        List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(30), null,
                                LocalTime.of(14, 30), LocalTime.of(16, 45), null, null)),
                        List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());

        mockMvc.perform(get("/experiences/" + experience.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nombre)))
                .andExpect(content().string(containsString("19.4326")))
                .andExpect(content().string(containsString("14:30")))
                .andExpect(content().string(containsString("16:45")));
    }

    @Test
    @WithMockUser("demo_creator")
    void semanalSabadoDomingoGeneraInstancias() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        // Próximo sábado y domingo dentro del rango.
        java.time.LocalDate sabado = LocalDate.now().plusDays(1);
        while (sabado.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) sabado = sabado.plusDays(1);
        java.time.LocalDate domingo = sabado.plusDays(1);

        MvcResult result = mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "Repro semanal " + UUID.randomUUID())
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "600")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "10")
                        .param("price", "300.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "WEEKLY")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].dayOfWeek", "SATURDAY")
                        .param("schedules[0].startTime", "08:00")
                        .param("schedules[0].endTime", "18:00")
                        .param("schedules[0].rangeStart", sabado.toString())
                        .param("schedules[0].rangeEnd", domingo.plusWeeks(2).toString())
                        .param("schedules[1].dayOfWeek", "SUNDAY")
                        .param("schedules[1].startTime", "08:00")
                        .param("schedules[1].endTime", "18:00")
                        .param("schedules[1].rangeStart", sabado.toString())
                        .param("schedules[1].rangeEnd", domingo.plusWeeks(2).toString())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/experiences/*"))
                .andReturn();

        Long id = Long.valueOf(result.getResponse().getRedirectedUrl().replace("/experiences/", ""));
        var instancias = instanceRepository.findByExperienceIdOrderByDateAsc(id);
        assertFalse(instancias.isEmpty(), "el semanal sáb+dom tiene que generar instancias futuras");
        org.junit.jupiter.api.Assertions.assertTrue(
                instancias.stream().anyMatch(i -> i.getDate().getDayOfWeek() == java.time.DayOfWeek.SATURDAY),
                "tiene que haber instancia un sábado");

        mockMvc.perform(get("/experiences/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Próximas fechas")));
    }

    @Test
    @WithMockUser("demo_creator")
    void editarSinImagenesNuevasConservaLasViejas() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                        "Repro img " + UUID.randomUUID(), "Desc", 120, 2, 8, BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                        19.4326, -99.1332,
                        List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(30), null,
                                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                        List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());
        Long id = experience.getId();
        int antes = imageRepository.findByExperienceIdOrderByIdAsc(id).size();

        mockMvc.perform(multipart("/experiences/" + id)
                        .param("name", experience.getName())
                        .param("description", "Desc editada")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", LocalDate.now().plusDays(30).toString())
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertEquals(antes, imageRepository.findByExperienceIdOrderByIdAsc(id).size(),
                "editar sin subir imágenes nuevas conserva las existentes");
    }

    @Test
    @WithMockUser("demo_creator")
    void editarPuedeEliminarUnaImagenExistente() throws Exception {
        var img1 = new MockMultipartFile("images", "a.png", "image/png", "aaa".getBytes());
        var img2 = new MockMultipartFile("images", "b.png", "image/png", "bbb".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                        "Repro ximg " + UUID.randomUUID(), "Desc", 120, 2, 8, BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                        19.4326, -99.1332,
                        List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(30), null,
                                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                        List.of(img1, img2)),
                userRepository.findByUsername("demo_creator").orElseThrow());
        Long id = experience.getId();
        var guardadas = imageRepository.findByExperienceIdOrderByIdAsc(id);
        assertEquals(2, guardadas.size());

        // El form de edición muestra las existentes con botón de borrado.
        mockMvc.perform(get("/experiences/" + id + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("existing-images")))
                .andExpect(content().string(containsString("data-image-id=\"" + guardadas.get(0).getId() + "\"")));

        // Guardar marcando la primera para borrar, sin subir nuevas.
        mockMvc.perform(multipart("/experiences/" + id)
                        .param("name", experience.getName())
                        .param("description", "Desc")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", LocalDate.now().plusDays(30).toString())
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .param("removeImageIds", String.valueOf(guardadas.get(0).getId()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        var restantes = imageRepository.findByExperienceIdOrderByIdAsc(id);
        assertEquals(1, restantes.size(), "solo se borra la marcada");
        assertEquals(guardadas.get(1).getId(), restantes.get(0).getId());
    }

    @Test
    void editarConInstanciaReservadaGuardaTodoSinRomper() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "vieja".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                        "Repro reserva " + UUID.randomUUID(), "Desc", 120, 2, 8, BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                        19.4326, -99.1332,
                        List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(30), null,
                                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                        List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());
        Long expId = experience.getId();
        Long instanceId = instanceRepository.findByExperienceIdOrderByDateAsc(expId).get(0).getId();

        // demo_user reserva la fecha (queda vigente, no cancelada).
        mockMvc.perform(post("/reservations/" + instanceId)
                        .with(user("demo_user")).with(csrf())
                        .param("attendees", "1"))
                .andExpect(status().is3xxRedirection());

        // demo_creator edita todo (descripción + imagen nueva) con la misma agenda.
        var nueva = new MockMultipartFile("images", "n.png", "image/png", "nueva".getBytes());
        mockMvc.perform(multipart("/experiences/" + expId)
                        .file(nueva)
                        .with(user("demo_creator")).with(csrf())
                        .param("name", experience.getName())
                        .param("description", "Desc editada con reserva vigente")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", LocalDate.now().plusDays(30).toString())
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00"))
                .andExpect(status().is3xxRedirection());

        var instancias = instanceRepository.findByExperienceIdOrderByDateAsc(expId);
        assertEquals(1, instancias.size(), "no duplica el slot reservado");
        assertEquals(instanceId, instancias.get(0).getId(), "la instancia reservada sigue intacta");
        var imagenes = imageRepository.findByExperienceIdOrderByIdAsc(expId);
        assertEquals(1, imagenes.size());
        assertEquals(java.util.Base64.getEncoder().encodeToString("nueva".getBytes()),
                imagenes.get(0).getData(), "la imagen nueva sí se guarda");
        assertEquals("Desc editada con reserva vigente",
                experienceService.get(expId).getDescription());
    }

    @Test
    @WithMockUser("demo_creator")
    void crearSinFechaRechazaConError() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "Sin fecha " + UUID.randomUUID())
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", "")
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("obligatori")));
    }

    @Test
    @WithMockUser("demo_creator")
    void crearSemanalSinRangoRechazaConError() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "Sin rango " + UUID.randomUUID())
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "WEEKLY")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].dayOfWeek", "SATURDAY")
                        .param("schedules[0].startTime", "08:00")
                        .param("schedules[0].endTime", "18:00")
                        .param("schedules[0].rangeStart", "")
                        .param("schedules[0].rangeEnd", "")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("obligatori")));
    }

    @Test
    @WithMockUser("demo_creator")
    void editarBorrandoLaUnicaImagenRechazaConError() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                        "Repro una img " + UUID.randomUUID(), "Desc", 120, 2, 8, BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                        19.4326, -99.1332,
                        List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(30), null,
                                LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                        List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());
        Long id = experience.getId();
        Long unica = imageRepository.findByExperienceIdOrderByIdAsc(id).get(0).getId();

        mockMvc.perform(multipart("/experiences/" + id)
                        .param("name", experience.getName())
                        .param("description", "Desc")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("currencyCode", "MXN")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", LocalDate.now().plusDays(30).toString())
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .param("removeImageIds", String.valueOf(unica))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("imagen")));

        assertEquals(1, imageRepository.findByExperienceIdOrderByIdAsc(id).size(),
                "la única imagen sigue guardada");
    }

    @Test
    void feedConParadasRenderizaRecorrido() {
        var paradas = List.of(
                new com.openlab.mx.share.experience.dto.ExperienceStopDTO(0, "Mirador del Cerro", "—", "—", 19.44, -99.14),
                new com.openlab.mx.share.experience.dto.ExperienceStopDTO(1, "Playa Escondida", "—", "—", 19.45, -99.15));
        var dto = new com.openlab.mx.share.experience.dto.ExperienceFeedDTO(
                999L, "Ruta test", "Desc", "QUJD", List.of("QUJD"), "Mirador del Cerro · —",
                150, LocalDate.now().plusDays(7), BigDecimal.valueOf(250), 4.5, false,
                "MXN", paradas, "[{\"placeName\":\"Mirador del Cerro\"}]");
        var variables = new java.util.HashMap<String, Object>();
        variables.put("feedPage",
                new org.springframework.data.domain.PageImpl<>(List.of(dto)));
        variables.put("categories", ExperienceCategory.values());
        variables.put("selectedCategory", null);
        variables.put("searchedCity", null);
        variables.put("savedCity", "CDMX");
        variables.put("_csrf",
                new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "tok"));
        var webApp = org.thymeleaf.web.servlet.JakartaServletWebApplication
                .buildApplication(new org.springframework.mock.web.MockServletContext());
        var exchange = webApp.buildExchange(
                new org.springframework.mock.web.MockHttpServletRequest(),
                new org.springframework.mock.web.MockHttpServletResponse());
        var ctx = new org.thymeleaf.context.WebContext(
                exchange, java.util.Locale.forLanguageTag("es"), variables);

        String html = templateEngine.process("feed", ctx);

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("feed-route-map"),
                "la tarjeta del feed muestra el mapa del recorrido");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Playa Escondida"),
                "la tarjeta del feed nombra inicio y fin");
    }

    @Test
    @WithMockUser("demo_creator")
    void errorDeValidacionConservaParadasEnElForm() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());

        mockMvc.perform(multipart("/experiences")
                        .file(image)
                        .param("name", "")
                        .param("description", "Descripción de prueba")
                        .param("durationMinutes", "120")
                        .param("minCapacity", "2")
                        .param("maxCapacity", "8")
                        .param("price", "190.00")
                        .param("category", "CULTURAL")
                        .param("recurrenceType", "UNIQUE")
                        .param("meetingLat", "19.4326")
                        .param("meetingLng", "-99.1332")
                        .param("schedules[0].date", fechaFutura())
                        .param("schedules[0].startTime", "10:00")
                        .param("schedules[0].endTime", "12:00")
                        .param("stopPlace", "Mirador del Cerro")
                        .param("stopLat", "19.4400")
                        .param("stopLng", "-99.1400")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stops-prefill")))
                .andExpect(content().string(containsString("Mirador del Cerro")));
    }

    @Test
    @WithMockUser("demo_creator")
    void editarSemanalMuestraTodosLosDias() throws Exception {
        var image = new MockMultipartFile("images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                        "Repro semanal " + UUID.randomUUID(), "Desc", 120, 2, 8,
                        BigDecimal.valueOf(190),
                        ExperienceCategory.CULTURAL, RecurrenceType.WEEKLY,
                        19.4326, -99.1332,
                        List.of(
                                new ScheduleEntryRequest(null, java.time.DayOfWeek.MONDAY,
                                        LocalTime.of(18, 0), LocalTime.of(20, 0),
                                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(60)),
                                new ScheduleEntryRequest(null, java.time.DayOfWeek.FRIDAY,
                                        LocalTime.of(19, 0), LocalTime.of(21, 0),
                                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(60))),
                        List.of(image)),
                userRepository.findByUsername("demo_creator").orElseThrow());

        mockMvc.perform(get("/experiences/" + experience.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("18:00")))
                .andExpect(content().string(containsString("19:00")));
    }
}
