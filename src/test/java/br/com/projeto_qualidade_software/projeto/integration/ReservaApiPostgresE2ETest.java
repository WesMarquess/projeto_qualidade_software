package br.com.projeto_qualidade_software.projeto.integration;

import br.com.projeto_qualidade_software.projeto.api.ApiExceptionHandler;
import br.com.projeto_qualidade_software.projeto.api.dto.ReservaResponse;
import br.com.projeto_qualidade_software.projeto.api.dto.SolicitarReservaRequest;
import br.com.projeto_qualidade_software.projeto.entities.Sala;
import br.com.projeto_qualidade_software.projeto.entities.Solicitante;
import br.com.projeto_qualidade_software.projeto.enums.StatusReserva;
import br.com.projeto_qualidade_software.projeto.enums.StatusSala;
import br.com.projeto_qualidade_software.projeto.enums.TipoSolicitante;
import br.com.projeto_qualidade_software.projeto.repositories.ReservaRepository;
import br.com.projeto_qualidade_software.projeto.repositories.SalaRepository;
import br.com.projeto_qualidade_software.projeto.repositories.SolicitanteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.seed.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@Testcontainers
class ReservaApiPostgresE2ETest {

    private static final LocalDateTime INICIO =
            LocalDateTime.of(2030, 10, 26, 10, 0);
    private static final LocalDateTime FIM =
            LocalDateTime.of(2030, 10, 26, 11, 0);
    private static final Set<StatusReserva> STATUS_QUE_OCUPAM_SALA = Set.of(
            StatusReserva.SOLICITADA,
            StatusReserva.APROVADA,
            StatusReserva.EM_USO
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("reservas_e2e_test")
                    .withUsername("test")
                    .withPassword("test")
                    .waitingFor(Wait.forSuccessfulCommand(
                            "pg_isready -U test -d reservas_e2e_test"
                    ).withStartupTimeout(Duration.ofSeconds(60)));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SalaRepository salaRepository;

    @Autowired
    private SolicitanteRepository solicitanteRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long salaId;
    private Long solicitanteId;

    @DynamicPropertySource
    static void configurarDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void prepararDados() {
        Sala sala = salaRepository.saveAndFlush(
                new Sala(null, 30, StatusSala.DISPONIVEL)
        );
        salaId = sala.getId();

        String identificador = UUID.randomUUID().toString();
        Solicitante solicitante = solicitanteRepository.saveAndFlush(new Solicitante(
                null,
                "Solicitante E2E",
                identificador + "@teste.local",
                "senha-de-teste",
                TipoSolicitante.PROFESSOR,
                LocalDate.now(),
                LocalDate.now()
        ));
        solicitanteId = solicitante.getId();
    }

    @AfterEach
    void limparDados() {
        if (salaId != null) {
            jdbcTemplate.update("delete from reservas where sala_id = ?", salaId);
        }
        if (solicitanteId != null) {
            jdbcTemplate.update("delete from solicitantes where id = ?", solicitanteId);
        }
        if (salaId != null) {
            jdbcTemplate.update("delete from salas where id = ?", salaId);
        }
    }

    @Test
    void deveCriarReservaPorHttpConsultarEConfirmarPersistencia() {
        ResponseEntity<ReservaResponse> criada = restTemplate.postForEntity(
                "/api/reservas",
                solicitarReserva(INICIO, FIM, TipoSolicitante.PROFESSOR),
                ReservaResponse.class
        );

        assertEquals(HttpStatus.CREATED, criada.getStatusCode());
        assertNotNull(criada.getHeaders().getLocation());
        ReservaResponse corpoCriacao = criada.getBody();
        assertNotNull(corpoCriacao);
        assertNotNull(corpoCriacao.id());
        assertEquals(salaId, corpoCriacao.salaId());
        assertEquals(solicitanteId, corpoCriacao.solicitanteId());
        assertEquals(INICIO, corpoCriacao.inicio());
        assertEquals(FIM, corpoCriacao.fim());
        assertEquals(StatusReserva.SOLICITADA, corpoCriacao.status());
        assertNotNull(corpoCriacao.solicitadoEm());

        URI localizacao = criada.getHeaders().getLocation();
        assertTrue(localizacao.getPath().endsWith(
                "/api/reservas/" + corpoCriacao.id()
        ));
        ResponseEntity<ReservaResponse> recuperada =
                restTemplate.getForEntity(localizacao, ReservaResponse.class);

        assertEquals(HttpStatus.OK, recuperada.getStatusCode());
        ReservaResponse corpoRecuperado = recuperada.getBody();
        assertNotNull(corpoRecuperado);
        assertEquals(corpoCriacao.id(), corpoRecuperado.id());
        assertEquals(corpoCriacao.salaId(), corpoRecuperado.salaId());
        assertEquals(corpoCriacao.solicitanteId(), corpoRecuperado.solicitanteId());
        assertEquals(corpoCriacao.inicio(), corpoRecuperado.inicio());
        assertEquals(corpoCriacao.fim(), corpoRecuperado.fim());
        assertEquals(corpoCriacao.status(), corpoRecuperado.status());
        assertNotNull(corpoRecuperado.solicitadoEm());
        assertTrue(reservaRepository.findById(corpoCriacao.id()).isPresent());
    }

    @Test
    void deveRetornarBadRequestQuandoCampoObrigatorioDoDtoEstiverAusente() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> requisicaoInvalida = Map.of(
                "salaId", salaId,
                "solicitanteId", solicitanteId,
                "tipoSolicitante", TipoSolicitante.PROFESSOR,
                "inicio", INICIO
        );

        ResponseEntity<ApiExceptionHandler.ErroResponse> resposta =
                restTemplate.postForEntity(
                        "/api/reservas",
                        new HttpEntity<>(requisicaoInvalida, headers),
                        ApiExceptionHandler.ErroResponse.class
                );

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertTrue(resposta.getBody().mensagem().contains("fim"));
        assertEquals(0L, contarReservasConflitantes());
    }

    @Test
    void deveRetornarConflictSemPersistirSegundaReservaConflitante() {
        ResponseEntity<ReservaResponse> primeira = restTemplate.postForEntity(
                "/api/reservas",
                solicitarReserva(INICIO, FIM, TipoSolicitante.PROFESSOR),
                ReservaResponse.class
        );
        assertEquals(HttpStatus.CREATED, primeira.getStatusCode());

        ResponseEntity<ApiExceptionHandler.ErroResponse> segunda =
                restTemplate.postForEntity(
                        "/api/reservas",
                        solicitarReserva(
                                INICIO.plusMinutes(30),
                                FIM.plusMinutes(30),
                                TipoSolicitante.PROFESSOR
                        ),
                        ApiExceptionHandler.ErroResponse.class
                );

        assertEquals(HttpStatus.CONFLICT, segunda.getStatusCode());
        assertNotNull(segunda.getBody());
        assertTrue(segunda.getBody().mensagem()
                .contains("Já existe uma reserva para esta sala"));
        assertEquals(
                1L,
                contarReservasConflitantes()
        );
    }

    @Test
    void deveCancelarReservaPorHttpEConsultarEstadoPersistido() {
        ResponseEntity<ReservaResponse> criada = restTemplate.postForEntity(
                "/api/reservas",
                solicitarReserva(INICIO, FIM, TipoSolicitante.PROFESSOR),
                ReservaResponse.class
        );
        assertEquals(HttpStatus.CREATED, criada.getStatusCode());
        ReservaResponse reserva = criada.getBody();
        assertNotNull(reserva);

        ResponseEntity<ReservaResponse> cancelada = restTemplate.postForEntity(
                "/api/reservas/{reservaId}/cancelamento",
                Map.of("usuarioId", solicitanteId),
                ReservaResponse.class,
                reserva.id()
        );

        assertEquals(HttpStatus.OK, cancelada.getStatusCode());
        assertNotNull(cancelada.getBody());
        assertEquals(StatusReserva.CANCELADA, cancelada.getBody().status());

        URI localizacao = criada.getHeaders().getLocation();
        assertNotNull(localizacao);
        ResponseEntity<ReservaResponse> recuperada =
                restTemplate.getForEntity(localizacao, ReservaResponse.class);
        assertEquals(HttpStatus.OK, recuperada.getStatusCode());
        assertNotNull(recuperada.getBody());
        assertEquals(StatusReserva.CANCELADA, recuperada.getBody().status());
    }

    @Test
    void deveRetornarForbiddenQuandoTipoInformadoDivergirDoCadastro() {
        ResponseEntity<ApiExceptionHandler.ErroResponse> resposta =
                restTemplate.postForEntity(
                        "/api/reservas",
                        solicitarReserva(INICIO, FIM, TipoSolicitante.COORDENADOR),
                        ApiExceptionHandler.ErroResponse.class
                );

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertTrue(resposta.getBody().mensagem()
                .contains("não corresponde ao perfil cadastrado"));
        assertEquals(0L, contarReservasConflitantes());
    }

    private long contarReservasConflitantes() {
        return reservaRepository.contarConflitos(
                salaId,
                STATUS_QUE_OCUPAM_SALA,
                INICIO,
                FIM,
                null
        );
    }

    private SolicitarReservaRequest solicitarReserva(
            LocalDateTime inicio,
            LocalDateTime fim,
            TipoSolicitante tipoSolicitante
    ) {
        return new SolicitarReservaRequest(
                salaId,
                solicitanteId,
                tipoSolicitante,
                inicio,
                fim
        );
    }
}
