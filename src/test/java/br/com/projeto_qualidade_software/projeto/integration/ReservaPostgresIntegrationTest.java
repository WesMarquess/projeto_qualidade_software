package br.com.projeto_qualidade_software.projeto.integration;

import br.com.projeto_qualidade_software.projeto.entities.Reserva;
import br.com.projeto_qualidade_software.projeto.entities.Sala;
import br.com.projeto_qualidade_software.projeto.entities.Solicitante;
import br.com.projeto_qualidade_software.projeto.enums.StatusReserva;
import br.com.projeto_qualidade_software.projeto.enums.StatusSala;
import br.com.projeto_qualidade_software.projeto.enums.TipoSolicitante;
import br.com.projeto_qualidade_software.projeto.repositories.ReservaRepository;
import br.com.projeto_qualidade_software.projeto.repositories.SalaRepository;
import br.com.projeto_qualidade_software.projeto.repositories.SolicitanteRepository;
import br.com.projeto_qualidade_software.projeto.services.ReservaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
@Transactional
class ReservaPostgresIntegrationTest {

    private static final LocalDateTime INICIO =
            LocalDateTime.of(2026, 10, 26, 10, 0);
    private static final LocalDateTime FIM =
            LocalDateTime.of(2026, 10, 26, 11, 0);
    private static final Set<StatusReserva> STATUS_QUE_OCUPAM_SALA = Set.of(
            StatusReserva.SOLICITADA,
            StatusReserva.APROVADA,
            StatusReserva.EM_USO
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("reservas_test")
                    .withUsername("test")
                    .withPassword("test")
                    .waitingFor(Wait.forSuccessfulCommand(
                            "pg_isready -U test -d reservas_test"
                    ).withStartupTimeout(Duration.ofSeconds(60)));

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private SalaRepository salaRepository;

    @Autowired
    private SolicitanteRepository solicitanteRepository;

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configurarDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void devePersistirReservaERecuperarSuasAssociacoes() {
        // Rastreabilidade: RF07, INV-01 e CB-47 (reserva válida persistida no PostgreSQL).
        Sala sala = salvarSala();
        Solicitante solicitante = salvarSolicitante();
        Reserva reserva = new Reserva(
                null, sala, solicitante, null, INICIO, FIM, null, null, null
        );

        Reserva salva = reservaRepository.saveAndFlush(reserva);
        entityManager.clear();

        Reserva recuperada = reservaRepository.findById(salva.getId()).orElseThrow();

        assertEquals(salva.getId(), recuperada.getId());
        assertEquals(StatusReserva.SOLICITADA, recuperada.getStatus());
        assertEquals(INICIO, recuperada.getInicio());
        assertEquals(FIM, recuperada.getFim());
        assertEquals(sala.getId(), recuperada.getSala().getId());
        assertEquals(solicitante.getId(), recuperada.getSolicitante().getId());
        assertNotNull(recuperada.getSolicitadoEm());
    }

    @Test
    void deveListarReservasDoSolicitantePorInicioDecrescente() {
        // Rastreabilidade: RF06, INV-12 e CB-58.
        Sala sala = salvarSala();
        Solicitante solicitante = salvarSolicitante();
        Solicitante outroSolicitante = salvarSolicitante();
        Reserva maisAntiga = salvarReserva(
                sala, solicitante, INICIO, FIM, StatusReserva.SOLICITADA
        );
        Reserva intermediaria = salvarReserva(
                sala, solicitante, INICIO.plusDays(1), FIM.plusDays(1),
                StatusReserva.SOLICITADA
        );
        Reserva maisRecente = salvarReserva(
                sala, solicitante, INICIO.plusDays(2), FIM.plusDays(2),
                StatusReserva.SOLICITADA
        );
        salvarReserva(
                sala, outroSolicitante, INICIO.plusDays(3), FIM.plusDays(3),
                StatusReserva.SOLICITADA
        );

        entityManager.clear();
        List<Reserva> reservas =
                reservaRepository.findBySolicitanteIdOrderByInicioDesc(solicitante.getId());

        assertEquals(
                List.of(maisRecente.getId(), intermediaria.getId(), maisAntiga.getId()),
                reservas.stream().map(Reserva::getId).toList()
        );
    }

    @Test
    void deveDetectarConflitoEntreIntervalosSobrepostos() {
        // Rastreabilidade: RF08, RN02, INV-02 e CB-54.
        Sala sala = salvarSala();
        Solicitante solicitante = salvarSolicitante();
        salvarReserva(sala, solicitante, INICIO, FIM, StatusReserva.SOLICITADA);

        long conflitos = reservaRepository.contarConflitos(
                sala.getId(),
                STATUS_QUE_OCUPAM_SALA,
                INICIO.plusMinutes(30),
                FIM.plusMinutes(30),
                null
        );

        assertEquals(1L, conflitos);
    }

    @ParameterizedTest(name = "intervalo adjacente {0} não gera conflito")
    @MethodSource("intervalosAdjacentes")
    void naoDeveDetectarConflitoEntreIntervalosAdjacentes(
            LocalDateTime inicioNovo,
            LocalDateTime fimNovo
    ) {
        // Rastreabilidade: RF08, RN02, INV-02, CB-52 e CB-53.
        Sala sala = salvarSala();
        Solicitante solicitante = salvarSolicitante();
        salvarReserva(sala, solicitante, INICIO, FIM, StatusReserva.APROVADA);

        long conflitos = reservaRepository.contarConflitos(
                sala.getId(), STATUS_QUE_OCUPAM_SALA, inicioNovo, fimNovo, null
        );

        assertEquals(0L, conflitos);
    }

    private static Stream<Arguments> intervalosAdjacentes() {
        return Stream.of(
                Arguments.of(INICIO.minusHours(1), INICIO),
                Arguments.of(FIM, FIM.plusHours(1))
        );
    }

    @Test
    void deveExcluirAPropriaReservaDaContagemDeConflitos() {
        // Rastreabilidade: RF06, RN02, INV-02 e CB-55.
        Sala sala = salvarSala();
        Solicitante solicitante = salvarSolicitante();
        Reserva reserva = salvarReserva(
                sala, solicitante, INICIO, FIM, StatusReserva.SOLICITADA
        );

        long conflitos = reservaRepository.contarConflitos(
                sala.getId(),
                STATUS_QUE_OCUPAM_SALA,
                INICIO,
                FIM,
                reserva.getId()
        );

        assertEquals(0L, conflitos);
    }

    @ParameterizedTest(name = "status existente {0}: bloqueia solicitação = {1}")
    @MethodSource("statusDeOcupacao")
    void deveBloquearSomenteEstadosQueOcupamASala(
            StatusReserva statusExistente,
            boolean deveBloquear
    ) {
        // Rastreabilidade: RF08, RN02, INV-02 e CB-49 a CB-51.
        Sala sala = salvarSala();
        Solicitante solicitante = salvarSolicitante();
        salvarReserva(sala, solicitante, INICIO, FIM, statusExistente);

        if (deveBloquear) {
            assertThrows(
                    IllegalStateException.class,
                    () -> reservaService.solicitarReserva(
                            sala.getId(),
                            solicitante.getId(),
                            TipoSolicitante.PROFESSOR,
                            INICIO,
                            FIM
                    )
            );
            assertEquals(1L, reservaRepository.count());
        } else {
            Reserva criada = assertDoesNotThrow(() -> reservaService.solicitarReserva(
                    sala.getId(),
                    solicitante.getId(),
                    TipoSolicitante.PROFESSOR,
                    INICIO,
                    FIM
            ));
            assertNotNull(criada.getId());
            assertEquals(2L, reservaRepository.count());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deveAceitarSomenteUmaReservaEmSolicitacoesConcorrentesComConflito()
            throws Exception {
        // Rastreabilidade: RF09, RN03 e INV-03.
        FixtureConcorrencia fixture = criarFixtureConcorrencia();
        try {
            List<ResultadoConcorrencia> resultados = solicitarSimultaneamente(
                    fixture,
                    new Intervalo(INICIO, FIM),
                    new Intervalo(INICIO, FIM)
            );

            List<ResultadoConcorrencia> aceitas = resultados.stream()
                    .filter(resultado -> resultado.reserva() != null)
                    .toList();
            List<ResultadoConcorrencia> rejeitadas = resultados.stream()
                    .filter(resultado -> resultado.conflito() != null)
                    .toList();

            assertEquals(1, aceitas.size());
            assertEquals(1, rejeitadas.size());
            assertTrue(rejeitadas.getFirst().conflito().getMessage()
                    .contains("Já existe uma reserva para esta sala"));
            assertEquals(
                    1L,
                    contarReservasNoIntervalo(fixture.salaId(), INICIO, FIM)
            );
        } finally {
            limparFixtureConcorrencia(fixture);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deveAceitarSolicitacoesConcorrentesParaIntervalosNaoSobrepostos()
            throws Exception {
        // Rastreabilidade: RF09, RN03 e INV-03 (intervalos não conflitantes).
        FixtureConcorrencia fixture = criarFixtureConcorrencia();
        LocalDateTime segundoInicio = FIM.plusHours(1);
        LocalDateTime segundoFim = segundoInicio.plusHours(1);
        try {
            List<ResultadoConcorrencia> resultados = solicitarSimultaneamente(
                    fixture,
                    new Intervalo(INICIO, FIM),
                    new Intervalo(segundoInicio, segundoFim)
            );

            assertEquals(2, resultados.size());
            assertTrue(resultados.stream()
                    .allMatch(resultado -> resultado.reserva() != null
                            && resultado.conflito() == null));
            assertEquals(
                    2L,
                    contarReservasNoIntervalo(fixture.salaId(), INICIO, segundoFim)
            );
        } finally {
            limparFixtureConcorrencia(fixture);
        }
    }

    private FixtureConcorrencia criarFixtureConcorrencia() {
        return novaTransacao().execute(status -> {
            Sala sala = salvarSala();
            Solicitante solicitante = salvarSolicitante();
            return new FixtureConcorrencia(sala.getId(), solicitante.getId());
        });
    }

    private void limparFixtureConcorrencia(FixtureConcorrencia fixture) {
        novaTransacao().executeWithoutResult(status -> {
            jdbcTemplate.update("delete from reservas where sala_id = ?", fixture.salaId());
            jdbcTemplate.update(
                    "delete from solicitantes where id = ?",
                    fixture.solicitanteId()
            );
            jdbcTemplate.update("delete from salas where id = ?", fixture.salaId());
        });
    }

    private List<ResultadoConcorrencia> solicitarSimultaneamente(
            FixtureConcorrencia fixture,
            Intervalo primeiro,
            Intervalo segundo
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        List<Future<ResultadoConcorrencia>> tarefas = new ArrayList<>(2);
        try {
            tarefas.add(executor.submit(() ->
                    solicitarAposBarreira(fixture, primeiro, prontas, iniciar)));
            tarefas.add(executor.submit(() ->
                    solicitarAposBarreira(fixture, segundo, prontas, iniciar)));

            assertTrue(prontas.await(10, TimeUnit.SECONDS),
                    "As duas threads devem estar prontas antes da liberação.");
            iniciar.countDown();

            return List.of(
                    obterResultado(tarefas.get(0)),
                    obterResultado(tarefas.get(1))
            );
        } finally {
            iniciar.countDown();
            executor.shutdown();
            boolean terminou;
            try {
                terminou = executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrompida) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw interrompida;
            }
            if (!terminou) {
                executor.shutdownNow();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "As threads concorrentes não terminaram antes da limpeza."
                    );
                }
            }
        }
    }

    private ResultadoConcorrencia solicitarAposBarreira(
            FixtureConcorrencia fixture,
            Intervalo intervalo,
            CountDownLatch prontas,
            CountDownLatch iniciar
    ) throws InterruptedException, TimeoutException {
        prontas.countDown();
        if (!iniciar.await(10, TimeUnit.SECONDS)) {
            throw new TimeoutException("A liberação das solicitações concorrentes expirou.");
        }

        try {
            Reserva reserva = reservaService.solicitarReserva(
                    fixture.salaId(),
                    fixture.solicitanteId(),
                    TipoSolicitante.PROFESSOR,
                    intervalo.inicio(),
                    intervalo.fim()
            );
            return new ResultadoConcorrencia(reserva, null);
        } catch (IllegalStateException conflito) {
            return new ResultadoConcorrencia(null, conflito);
        }
    }

    private ResultadoConcorrencia obterResultado(
            Future<ResultadoConcorrencia> tarefa
    ) throws InterruptedException, ExecutionException, TimeoutException {
        return tarefa.get(20, TimeUnit.SECONDS);
    }

    private long contarReservasNoIntervalo(
            Long salaId,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        return reservaRepository.contarConflitos(
                salaId,
                STATUS_QUE_OCUPAM_SALA,
                inicio,
                fim,
                null
        );
    }

    private TransactionTemplate novaTransacao() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    private record FixtureConcorrencia(Long salaId, Long solicitanteId) {
    }

    private record Intervalo(LocalDateTime inicio, LocalDateTime fim) {
    }

    private record ResultadoConcorrencia(
            Reserva reserva,
            IllegalStateException conflito
    ) {
    }

    private static Stream<Arguments> statusDeOcupacao() {
        return Stream.of(
                Arguments.of(StatusReserva.SOLICITADA, true),
                Arguments.of(StatusReserva.APROVADA, true),
                Arguments.of(StatusReserva.EM_USO, true),
                Arguments.of(StatusReserva.CONCLUIDA, false),
                Arguments.of(StatusReserva.REJEITADA, false),
                Arguments.of(StatusReserva.CANCELADA, false),
                Arguments.of(StatusReserva.NAO_COMPARECEU, false)
        );
    }

    private Sala salvarSala() {
        return salaRepository.saveAndFlush(
                new Sala(null, 30, StatusSala.DISPONIVEL)
        );
    }

    private Solicitante salvarSolicitante() {
        String identificador = UUID.randomUUID().toString();
        return solicitanteRepository.saveAndFlush(new Solicitante(
                null,
                "Solicitante de integração",
                identificador + "@teste.local",
                "senha-de-teste",
                TipoSolicitante.PROFESSOR,
                LocalDate.now(),
                LocalDate.now()
        ));
    }

    private Reserva salvarReserva(
            Sala sala,
            Solicitante solicitante,
            LocalDateTime inicio,
            LocalDateTime fim,
            StatusReserva status
    ) {
        return reservaRepository.saveAndFlush(new Reserva(
                null, sala, solicitante, null, inicio, fim, status, null, null
        ));
    }
}
