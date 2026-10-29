
package com.openlab.mx.movo.chat;

import com.openlab.mx.movo.chat.controller.ChatController;
import com.openlab.mx.movo.chat.dto.ChatMessageRequest;
import com.openlab.mx.movo.experience.dto.ExperienceFormRequest;
import com.openlab.mx.movo.experience.dto.ScheduleEntryRequest;
import com.openlab.mx.movo.experience.entity.Experience;
import com.openlab.mx.movo.experience.entity.ExperienceCategory;
import com.openlab.mx.movo.experience.entity.RecurrenceType;
import com.openlab.mx.movo.experience.repository.ExperienceInstanceRepository;
import com.openlab.mx.movo.experience.service.ExperienceService;
import com.openlab.mx.movo.reservation.repository.ReservationRepository;
import com.openlab.mx.movo.reservation.service.CancellationService;
import com.openlab.mx.movo.reservation.service.ReservationService;
import com.openlab.mx.movo.user.entity.User;
import com.openlab.mx.movo.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(com.openlab.mx.movo.shared.TestIdentityGateConfig.class)
class ChatStompTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ChatController chatController;
    @Autowired
    private ExperienceService experienceService;
    @Autowired
    private ExperienceInstanceRepository instanceRepository;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private CancellationService cancellationService;
    @Autowired
    private UserRepository userRepository;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new SimpleMessageConverter());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    private User usuario(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    /** Login real por formulario para obtener la cookie de sesión del WS. */
    private String login(String username, String password) throws Exception {
        java.net.CookieManager jar = new java.net.CookieManager();
        HttpClient http = HttpClient.newBuilder().cookieHandler(jar).build();
        HttpResponse<String> page = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String body = page.body();
        String token = body.replaceAll("(?s).*name=\"_csrf\"[^>]*value=\"([^\"]+)\".*", "$1");
        if (token.length() > 200 || token.contains("<")) {
            int i = body.indexOf("_csrf");
            String contexto = i >= 0 ? body.substring(Math.max(0, i - 120), Math.min(body.length(), i + 120))
                    : "sin _csrf en el HTML";
            throw new IllegalStateException("no pude extraer el CSRF: " + contexto);
        }
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "username=" + username + "&password=" + password + "&_csrf=" + token))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (login.statusCode() != 302) {
            throw new IllegalStateException("login falló: status=" + login.statusCode());
        }
        return jar.getCookieStore().getCookies().stream()
                .filter(c -> "JSESSIONID".equals(c.getName()))
                .findFirst()
                .map(c -> "JSESSIONID=" + c.getValue())
                .orElseThrow(() -> new IllegalStateException("no JSESSIONID"));
    }

    private Long fechaConfirmada() {
        var image = new org.springframework.mock.web.MockMultipartFile(
                "images", "f.png", "image/png", "abc".getBytes());
        Experience experience = experienceService.create(new ExperienceFormRequest(
                "Chat Exp " + UUID.randomUUID(), "Desc", 120, 1, 4, BigDecimal.valueOf(100),
                ExperienceCategory.CULTURAL, RecurrenceType.UNIQUE,
                19.4326, -99.1332,
                List.of(new ScheduleEntryRequest(LocalDate.now().plusDays(20), null,
                        LocalTime.of(10, 0), LocalTime.of(12, 0), null, null)),
                List.of(image), null, null), usuario("demo_creator"));
        Long instanceId = instanceRepository.findByExperienceIdOrderByDateAsc(experience.getId())
                .get(0).getId();
        var reserva = reservationService.reserve(instanceId, 1, usuario("demo_user"));
        // La confirmación la hace el pago (Task 1); sembramos el estado directo.
        reserva.setStatus(com.openlab.mx.movo.reservation.entity.ReservationStatus.CONFIRMADA);
        reservationRepository.save(reserva);
        return instanceId;
    }

    @Test
    @WithMockUser("demo_user")
    void stompRecibeElMensajePublicadoEnMenosDe2Segundos() throws Exception {
        Long instanceId = fechaConfirmada();

        var headers = new org.springframework.web.socket.WebSocketHttpHeaders();
        headers.add("Cookie", login("demo_user", "Clave1234!"));
        StringBuilder errores = new StringBuilder();
        StompSession session = stompClient
                .connectAsync("http://localhost:" + port + "/ws", headers, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession session, StompCommand command,
                                                StompHeaders headers, byte[] payload, Throwable exception) {
                        errores.append("exception: ").append(exception.getMessage()).append("; ");
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        errores.append("transport: ").append(exception.getMessage()).append("; ");
                    }
                })
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<String> recibido = new CompletableFuture<>();
        session.subscribe("/topic/instances/" + instanceId, new StompFrameHandler() {
            @Override
            public Class<?> getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                byte[] bytes = (byte[]) payload;
                recibido.complete(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        // el SUBSCRIBE viaja asíncrono: damos tiempo a que quede registrado
        Thread.sleep(500);

        // Llamada directa al controller: BindingResult vacío simula un binding válido
        var mensajeReq = new ChatMessageRequest("hola desde el test");
        chatController.send(instanceId, mensajeReq,
                new BeanPropertyBindingResult(mensajeReq, "mensajeReq"));

        String mensaje;
        try {
            mensaje = recibido.get(2, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("no llegó el mensaje; errores STOMP: " + errores, e);
        }
        assertNotNull(mensaje);
        assertTrue(mensaje.contains("hola desde el test"), "payload: " + mensaje);
        session.disconnect();
    }

    @Test
    @WithMockUser("demo_user")
    void instanciaArchivadaNoAdmiteMensajes() {
        Long instanceId = fechaConfirmada();
        cancellationService.cancelByCreator(instanceId, usuario("demo_creator"));

        assertThrows(AccessDeniedException.class,
                () -> {
                    var rechazado = new ChatMessageRequest("no debería pasar");
                    chatController.send(instanceId, rechazado,
                            new BeanPropertyBindingResult(rechazado, "rechazado"));
                });
    }
}