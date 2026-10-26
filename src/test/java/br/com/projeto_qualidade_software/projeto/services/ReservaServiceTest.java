package br.com.projeto_qualidade_software.projeto.services;

import br.com.projeto_qualidade_software.projeto.entities.Administrador;
import br.com.projeto_qualidade_software.projeto.entities.Reserva;
import br.com.projeto_qualidade_software.projeto.entities.Responsavel;
import br.com.projeto_qualidade_software.projeto.entities.Sala;
import br.com.projeto_qualidade_software.projeto.entities.Solicitante;
import br.com.projeto_qualidade_software.projeto.enums.StatusReserva;
import br.com.projeto_qualidade_software.projeto.enums.StatusSala;
import br.com.projeto_qualidade_software.projeto.enums.TipoAprovadorReserva;
import br.com.projeto_qualidade_software.projeto.enums.TipoSolicitante;
import br.com.projeto_qualidade_software.projeto.exceptions.AcessoNegadoException;
import br.com.projeto_qualidade_software.projeto.repositories.AdministradorRepository;
import br.com.projeto_qualidade_software.projeto.repositories.ReservaRepository;
import br.com.projeto_qualidade_software.projeto.repositories.ResponsavelRepository;
import br.com.projeto_qualidade_software.projeto.repositories.SalaRepository;
import br.com.projeto_qualidade_software.projeto.repositories.SolicitanteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReservaServiceTest {

    private static final Long SALA_ID = 1L;
    private static final Long SOLICITANTE_ID = 2L;
    private static final Long RESERVA_ID = 3L;
    private static final Long APROVADOR_ID = 4L;
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 26, 10, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 10, 26, 11, 0);

    private ReservaRepository reservaRepository;
    private SalaRepository salaRepository;
    private SolicitanteRepository solicitanteRepository;
    private ResponsavelRepository responsavelRepository;
    private AdministradorRepository administradorRepository;
    private ReservaService reservaService;

    @BeforeEach
    void setUp() {
        reservaRepository = mock(ReservaRepository.class);
        salaRepository = mock(SalaRepository.class);
        solicitanteRepository = mock(SolicitanteRepository.class);
        responsavelRepository = mock(ResponsavelRepository.class);
        administradorRepository = mock(AdministradorRepository.class);
        reservaService = new ReservaService(
                reservaRepository,
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void solicitarReservaComInicioEFimIguaisLancaIllegalArgumentException() {
        LocalDateTime inicioEFim = LocalDateTime.of(2026, 10, 26, 10, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> reservaService.solicitarReserva(
                        1L,
                        1L,
                        TipoSolicitante.PROFESSOR,
                        inicioEFim,
                        inicioEFim
                )
        );

        verificarNenhumRepositorioConsultado();
    }

    @Test
    void solicitarReservaComInicioNuloLancaIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reservaService.solicitarReserva(
                        SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, null, FIM
                )
        );

        verificarNenhumRepositorioConsultado();
    }

    @Test
    void solicitarReservaComFimNuloLancaIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reservaService.solicitarReserva(
                        SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, null
                )
        );

        verificarNenhumRepositorioConsultado();
    }

    @Test
    void solicitarReservaComFimAnteriorAoInicioLancaIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reservaService.solicitarReserva(
                        SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, FIM, INICIO
                )
        );

        verificarNenhumRepositorioConsultado();
    }

    @Test
    void solicitarReservaComIntervaloValidoSalvaReservaSolicitada() {
        Sala sala = salaDisponivel();
        Solicitante solicitante = solicitanteProfessor();
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID)).thenReturn(Optional.of(sala));
        when(solicitanteRepository.findById(SOLICITANTE_ID))
                .thenReturn(Optional.of(solicitante));
        when(reservaRepository.contarConflitos(
                eq(SALA_ID),
                eq(EnumSet.of(
                        StatusReserva.SOLICITADA,
                        StatusReserva.APROVADA,
                        StatusReserva.EM_USO
                )),
                eq(INICIO),
                eq(FIM),
                isNull()
        )).thenReturn(0L);
        doAnswer(invocation -> invocation.getArgument(0, Reserva.class))
                .when(reservaRepository).save(any(Reserva.class));

        Reserva resultado = reservaService.solicitarReserva(
                SALA_ID,
                SOLICITANTE_ID,
                TipoSolicitante.PROFESSOR,
                INICIO,
                FIM
        );

        assertNotNull(resultado);
        assertSame(sala, resultado.getSala());
        assertSame(solicitante, resultado.getSolicitante());
        assertEquals(INICIO, resultado.getInicio());
        assertEquals(FIM, resultado.getFim());
        assertEquals(StatusReserva.SOLICITADA, resultado.getStatus());
        verify(reservaRepository).save(resultado);
    }

    @Test
    void solicitarReservaComSalaInexistenteLancaEntityNotFoundException() {
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.solicitarReserva(
                        SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, FIM
                )
        );

        verify(salaRepository).buscarPorIdComBloqueio(SALA_ID);
        verifyNoInteractions(
                reservaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void solicitarReservaComSalaIndisponivelLancaIllegalStateException() {
        Sala sala = new Sala(SALA_ID, 30, StatusSala.EM_MANUTENCAO);
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID)).thenReturn(Optional.of(sala));

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.solicitarReserva(
                        SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, FIM
                )
        );

        verify(salaRepository).buscarPorIdComBloqueio(SALA_ID);
        verifyNoInteractions(
                reservaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void solicitarReservaComSolicitanteInexistenteLancaEntityNotFoundException() {
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID))
                .thenReturn(Optional.of(salaDisponivel()));
        when(solicitanteRepository.findById(SOLICITANTE_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.solicitarReserva(
                        SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, FIM
                )
        );

        verify(salaRepository).buscarPorIdComBloqueio(SALA_ID);
        verify(solicitanteRepository).findById(SOLICITANTE_ID);
        verifyNoInteractions(reservaRepository, responsavelRepository, administradorRepository);
    }

    @Test
    void solicitarReservaComTipoDiferenteDoCadastroLancaAcessoNegadoException() {
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID))
                .thenReturn(Optional.of(salaDisponivel()));
        when(solicitanteRepository.findById(SOLICITANTE_ID))
                .thenReturn(Optional.of(solicitanteProfessor()));

        assertThrows(
                AcessoNegadoException.class,
                () -> reservaService.solicitarReserva(
                        SALA_ID, SOLICITANTE_ID, TipoSolicitante.COORDENADOR, INICIO, FIM
                )
        );

        verify(salaRepository).buscarPorIdComBloqueio(SALA_ID);
        verify(solicitanteRepository).findById(SOLICITANTE_ID);
        verifyNoInteractions(reservaRepository, responsavelRepository, administradorRepository);
    }

    @Test
    void solicitarReservaComConflitoLancaIllegalStateException() {
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID))
                .thenReturn(Optional.of(salaDisponivel()));
        when(solicitanteRepository.findById(SOLICITANTE_ID))
                .thenReturn(Optional.of(solicitanteProfessor()));
        when(reservaRepository.contarConflitos(
                eq(SALA_ID),
                eq(EnumSet.of(
                        StatusReserva.SOLICITADA,
                        StatusReserva.APROVADA,
                        StatusReserva.EM_USO
                )),
                eq(INICIO),
                eq(FIM),
                isNull()
        )).thenReturn(1L);

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.solicitarReserva(
                        SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, FIM
                )
        );

        verify(reservaRepository).contarConflitos(
                eq(SALA_ID),
                eq(EnumSet.of(
                        StatusReserva.SOLICITADA,
                        StatusReserva.APROVADA,
                        StatusReserva.EM_USO
                )),
                eq(INICIO),
                eq(FIM),
                isNull()
        );
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    void alterarHorarioReservaComIntervaloValidoAtualizaEDevolveReservaSalva() {
        LocalDateTime novoInicio = LocalDateTime.of(2026, 10, 26, 12, 0);
        LocalDateTime novoFim = LocalDateTime.of(2026, 10, 26, 13, 0);
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);

        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID))
                .thenReturn(Optional.of(salaDisponivel()));
        when(reservaRepository.contarConflitos(
                eq(SALA_ID),
                eq(EnumSet.of(
                        StatusReserva.SOLICITADA,
                        StatusReserva.APROVADA,
                        StatusReserva.EM_USO
                )),
                eq(novoInicio),
                eq(novoFim),
                eq(RESERVA_ID)
        )).thenReturn(0L);
        doAnswer(invocation -> invocation.getArgument(0, Reserva.class))
                .when(reservaRepository).save(any(Reserva.class));

        Reserva resultado = reservaService.alterarHorarioReserva(
                RESERVA_ID, SOLICITANTE_ID, novoInicio, novoFim
        );

        assertSame(reserva, resultado);
        assertEquals(novoInicio, resultado.getInicio());
        assertEquals(novoFim, resultado.getFim());
        assertSame(StatusReserva.SOLICITADA, resultado.getStatus());
        verify(reservaRepository).save(reserva);
    }

    @Test
    void alterarHorarioReservaComInicioNuloLancaIllegalArgumentExceptionSemConsultarRepositorios() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, null, FIM
                )
        );

        verificarNenhumRepositorioConsultado();
    }

    @Test
    void alterarHorarioReservaComFimNuloLancaIllegalArgumentExceptionSemConsultarRepositorios() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, INICIO, null
                )
        );

        verificarNenhumRepositorioConsultado();
    }

    @Test
    void alterarHorarioReservaComInicioIgualAoFimLancaIllegalArgumentExceptionSemConsultarRepositorios() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, INICIO, INICIO
                )
        );

        verificarNenhumRepositorioConsultado();
    }

    @Test
    void alterarHorarioReservaComFimAnteriorAoInicioLancaIllegalArgumentExceptionSemConsultarRepositorios() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, FIM, INICIO
                )
        );

        verificarNenhumRepositorioConsultado();
    }

    @Test
    void alterarHorarioReservaComReservaInexistenteLancaEntityNotFoundException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, INICIO, FIM
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).contarConflitos(
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void alterarHorarioReservaPorSolicitanteDiferenteDoDonoLancaAcessoNegadoException() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        Solicitante outroSolicitante = new Solicitante();
        outroSolicitante.setId(99L);
        reserva.setSolicitante(outroSolicitante);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));

        assertThrows(
                AcessoNegadoException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, INICIO, FIM
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).contarConflitos(
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void alterarHorarioReservaComEstadoDiferenteDeSolicitadaLancaIllegalStateException() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.APROVADA);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, INICIO, FIM
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).contarConflitos(
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void alterarHorarioReservaComSalaInexistenteLancaEntityNotFoundException() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, INICIO, FIM
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(salaRepository).buscarPorIdComBloqueio(SALA_ID);
        verify(reservaRepository, never()).contarConflitos(
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(solicitanteRepository, responsavelRepository, administradorRepository);
    }

    @Test
    void alterarHorarioReservaComSalaIndisponivelLancaIllegalStateException() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        Sala salaIndisponivel = new Sala(SALA_ID, 30, StatusSala.RESERVADA);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID))
                .thenReturn(Optional.of(salaIndisponivel));

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, INICIO, FIM
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(salaRepository).buscarPorIdComBloqueio(SALA_ID);
        verify(reservaRepository, never()).contarConflitos(
                any(),
                any(),
                any(),
                any(),
                any()
        );
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(solicitanteRepository, responsavelRepository, administradorRepository);
    }

    @Test
    void alterarHorarioReservaComConflitoLancaIllegalStateExceptionESemAlterarReserva() {
        LocalDateTime novoInicio = LocalDateTime.of(2026, 10, 26, 12, 0);
        LocalDateTime novoFim = LocalDateTime.of(2026, 10, 26, 13, 0);
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);

        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(salaRepository.buscarPorIdComBloqueio(SALA_ID))
                .thenReturn(Optional.of(salaDisponivel()));
        when(reservaRepository.contarConflitos(
                eq(SALA_ID),
                eq(EnumSet.of(
                        StatusReserva.SOLICITADA,
                        StatusReserva.APROVADA,
                        StatusReserva.EM_USO
                )),
                eq(novoInicio),
                eq(novoFim),
                eq(RESERVA_ID)
        )).thenReturn(1L);

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.alterarHorarioReserva(
                        RESERVA_ID, SOLICITANTE_ID, novoInicio, novoFim
                )
        );

        assertEquals(INICIO, reserva.getInicio());
        assertEquals(FIM, reserva.getFim());
        verify(reservaRepository).contarConflitos(
                eq(SALA_ID),
                eq(EnumSet.of(
                        StatusReserva.SOLICITADA,
                        StatusReserva.APROVADA,
                        StatusReserva.EM_USO
                )),
                eq(novoInicio),
                eq(novoFim),
                eq(RESERVA_ID)
        );
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(solicitanteRepository, responsavelRepository, administradorRepository);
    }

    @Test
    void cancelarReservaSolicitadaAlteraEstadoESalvaReserva() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        Reserva resultado = reservaService.cancelarReserva(RESERVA_ID, SOLICITANTE_ID);

        assertSame(reserva, resultado);
        assertEquals(StatusReserva.CANCELADA, resultado.getStatus());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository).save(reserva);
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void cancelarReservaAprovadaAlteraEstadoESalvaReserva() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.APROVADA);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        Reserva resultado = reservaService.cancelarReserva(RESERVA_ID, SOLICITANTE_ID);

        assertSame(reserva, resultado);
        assertEquals(StatusReserva.CANCELADA, resultado.getStatus());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository).save(reserva);
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void cancelarReservaInexistenteLancaEntityNotFoundException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.cancelarReserva(RESERVA_ID, SOLICITANTE_ID)
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void cancelarReservaDeOutroSolicitanteLancaAcessoNegadoException() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        Solicitante outroSolicitante = new Solicitante();
        outroSolicitante.setId(99L);
        reserva.setSolicitante(outroSolicitante);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));

        assertThrows(
                AcessoNegadoException.class,
                () -> reservaService.cancelarReserva(RESERVA_ID, SOLICITANTE_ID)
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = StatusReserva.class,
            names = {"EM_USO", "CONCLUIDA", "REJEITADA", "CANCELADA", "NAO_COMPARECEU"}
    )
    void cancelarReservaEmEstadoNaoPermitidoLancaIllegalStateException(
            StatusReserva status
    ) {
        Reserva reserva = reservaParaAlteracao(status);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.cancelarReserva(RESERVA_ID, SOLICITANTE_ID)
        );

        assertEquals(status, reserva.getStatus());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void aprovarReservaPorResponsavelAtualizaReservaESalva() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        Responsavel responsavel = responsavelDeTeste();
        reserva.setAdministrador(administradorDeTeste());
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(responsavelRepository.findById(APROVADOR_ID))
                .thenReturn(Optional.of(responsavel));
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        Reserva resultado = reservaService.aprovarReserva(
                RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.RESPONSAVEL
        );

        assertSame(reserva, resultado);
        assertEquals(StatusReserva.APROVADA, resultado.getStatus());
        assertSame(responsavel, resultado.getResponsavel());
        assertNull(resultado.getAdministrador());
        assertNotNull(resultado.getAprovadoEm());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(responsavelRepository).findById(APROVADOR_ID);
        verify(reservaRepository).save(reserva);
        verifyNoInteractions(salaRepository, solicitanteRepository, administradorRepository);
    }

    @Test
    void aprovarReservaPorAdministradorAtualizaReservaESalva() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        Administrador administrador = administradorDeTeste();
        reserva.setResponsavel(responsavelDeTeste());
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(administradorRepository.findById(APROVADOR_ID))
                .thenReturn(Optional.of(administrador));
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        Reserva resultado = reservaService.aprovarReserva(
                RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.ADMINISTRADOR
        );

        assertSame(reserva, resultado);
        assertEquals(StatusReserva.APROVADA, resultado.getStatus());
        assertSame(administrador, resultado.getAdministrador());
        assertNull(resultado.getResponsavel());
        assertNotNull(resultado.getAprovadoEm());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(administradorRepository).findById(APROVADOR_ID);
        verify(reservaRepository).save(reserva);
        verifyNoInteractions(salaRepository, solicitanteRepository, responsavelRepository);
    }

    @Test
    void rejeitarReservaPorResponsavelAtualizaReservaESalva() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        Responsavel responsavel = responsavelDeTeste();
        reserva.setAdministrador(administradorDeTeste());
        reserva.setAprovadoEm(INICIO);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(responsavelRepository.findById(APROVADOR_ID))
                .thenReturn(Optional.of(responsavel));
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        Reserva resultado = reservaService.rejeitarReserva(
                RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.RESPONSAVEL
        );

        assertSame(reserva, resultado);
        assertEquals(StatusReserva.REJEITADA, resultado.getStatus());
        assertSame(responsavel, resultado.getResponsavel());
        assertNull(resultado.getAdministrador());
        assertEquals(INICIO, resultado.getAprovadoEm());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(responsavelRepository).findById(APROVADOR_ID);
        verify(reservaRepository).save(reserva);
        verifyNoInteractions(salaRepository, solicitanteRepository, administradorRepository);
    }

    @Test
    void rejeitarReservaPorAdministradorAtualizaReservaESalva() {
        Reserva reserva = reservaParaAlteracao(StatusReserva.SOLICITADA);
        Administrador administrador = administradorDeTeste();
        reserva.setResponsavel(responsavelDeTeste());
        reserva.setAprovadoEm(INICIO);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(administradorRepository.findById(APROVADOR_ID))
                .thenReturn(Optional.of(administrador));
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        Reserva resultado = reservaService.rejeitarReserva(
                RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.ADMINISTRADOR
        );

        assertSame(reserva, resultado);
        assertEquals(StatusReserva.REJEITADA, resultado.getStatus());
        assertSame(administrador, resultado.getAdministrador());
        assertNull(resultado.getResponsavel());
        assertEquals(INICIO, resultado.getAprovadoEm());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(administradorRepository).findById(APROVADOR_ID);
        verify(reservaRepository).save(reserva);
        verifyNoInteractions(salaRepository, solicitanteRepository, responsavelRepository);
    }

    @Test
    void aprovarReservaInexistenteLancaEntityNotFoundException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.aprovarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.RESPONSAVEL
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void rejeitarReservaInexistenteLancaEntityNotFoundException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.rejeitarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.RESPONSAVEL
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void aprovarReservaComResponsavelInexistenteLancaEntityNotFoundException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reservaParaAlteracao(StatusReserva.SOLICITADA)));
        when(responsavelRepository.findById(APROVADOR_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.aprovarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.RESPONSAVEL
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(responsavelRepository).findById(APROVADOR_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(salaRepository, solicitanteRepository, administradorRepository);
    }

    @Test
    void rejeitarReservaComResponsavelInexistenteLancaEntityNotFoundException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reservaParaAlteracao(StatusReserva.SOLICITADA)));
        when(responsavelRepository.findById(APROVADOR_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.rejeitarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.RESPONSAVEL
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(responsavelRepository).findById(APROVADOR_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(salaRepository, solicitanteRepository, administradorRepository);
    }

    @Test
    void aprovarReservaComAdministradorInexistenteLancaEntityNotFoundException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reservaParaAlteracao(StatusReserva.SOLICITADA)));
        when(administradorRepository.findById(APROVADOR_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.aprovarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.ADMINISTRADOR
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(administradorRepository).findById(APROVADOR_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(salaRepository, solicitanteRepository, responsavelRepository);
    }

    @Test
    void rejeitarReservaComAdministradorInexistenteLancaEntityNotFoundException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reservaParaAlteracao(StatusReserva.SOLICITADA)));
        when(administradorRepository.findById(APROVADOR_ID)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> reservaService.rejeitarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.ADMINISTRADOR
                )
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(administradorRepository).findById(APROVADOR_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(salaRepository, solicitanteRepository, responsavelRepository);
    }

    @Test
    void aprovarReservaComTipoNuloLancaAcessoNegadoException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reservaParaAlteracao(StatusReserva.SOLICITADA)));

        assertThrows(
                AcessoNegadoException.class,
                () -> reservaService.aprovarReserva(RESERVA_ID, APROVADOR_ID, null)
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @Test
    void rejeitarReservaComTipoNuloLancaAcessoNegadoException() {
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reservaParaAlteracao(StatusReserva.SOLICITADA)));

        assertThrows(
                AcessoNegadoException.class,
                () -> reservaService.rejeitarReserva(RESERVA_ID, APROVADOR_ID, null)
        );

        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    @ParameterizedTest(name = "aprovação por responsável com estado {0}")
    @EnumSource(value = StatusReserva.class, mode = EnumSource.Mode.EXCLUDE, names = "SOLICITADA")
    void aprovarReservaPorResponsavelComEstadoIncompativelPreservaReserva(
            StatusReserva status
    ) {
        Reserva reserva = reservaParaAlteracao(status);
        Responsavel responsavelInicial = responsavelDeTeste();
        Administrador administradorInicial = administradorDeTeste();
        LocalDateTime aprovadoEmInicial = INICIO.minusDays(1);
        reserva.setResponsavel(responsavelInicial);
        reserva.setAdministrador(administradorInicial);
        reserva.setAprovadoEm(aprovadoEmInicial);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(responsavelRepository.findById(APROVADOR_ID))
                .thenReturn(Optional.of(responsavelDeTeste()));

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.aprovarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.RESPONSAVEL
                )
        );

        assertEquals(status, reserva.getStatus());
        assertSame(responsavelInicial, reserva.getResponsavel());
        assertSame(administradorInicial, reserva.getAdministrador());
        assertEquals(aprovadoEmInicial, reserva.getAprovadoEm());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(responsavelRepository).findById(APROVADOR_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(salaRepository, solicitanteRepository, administradorRepository);
    }

    @ParameterizedTest(name = "aprovação por administrador com estado {0}")
    @EnumSource(value = StatusReserva.class, mode = EnumSource.Mode.EXCLUDE, names = "SOLICITADA")
    void aprovarReservaPorAdministradorComEstadoIncompativelPreservaReserva(
            StatusReserva status
    ) {
        Reserva reserva = reservaParaAlteracao(status);
        Responsavel responsavelInicial = responsavelDeTeste();
        Administrador administradorInicial = administradorDeTeste();
        LocalDateTime aprovadoEmInicial = INICIO.minusDays(1);
        reserva.setResponsavel(responsavelInicial);
        reserva.setAdministrador(administradorInicial);
        reserva.setAprovadoEm(aprovadoEmInicial);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(administradorRepository.findById(APROVADOR_ID))
                .thenReturn(Optional.of(administradorDeTeste()));

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.aprovarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.ADMINISTRADOR
                )
        );

        assertEquals(status, reserva.getStatus());
        assertSame(responsavelInicial, reserva.getResponsavel());
        assertSame(administradorInicial, reserva.getAdministrador());
        assertEquals(aprovadoEmInicial, reserva.getAprovadoEm());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(administradorRepository).findById(APROVADOR_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(salaRepository, solicitanteRepository, responsavelRepository);
    }

    @ParameterizedTest(name = "rejeição por responsável com estado {0}")
    @EnumSource(value = StatusReserva.class, mode = EnumSource.Mode.EXCLUDE, names = "SOLICITADA")
    void rejeitarReservaPorResponsavelComEstadoIncompativelPreservaReserva(
            StatusReserva status
    ) {
        Reserva reserva = reservaParaAlteracao(status);
        Responsavel responsavelInicial = responsavelDeTeste();
        Administrador administradorInicial = administradorDeTeste();
        LocalDateTime aprovadoEmInicial = INICIO.minusDays(1);
        reserva.setResponsavel(responsavelInicial);
        reserva.setAdministrador(administradorInicial);
        reserva.setAprovadoEm(aprovadoEmInicial);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(responsavelRepository.findById(APROVADOR_ID))
                .thenReturn(Optional.of(responsavelDeTeste()));

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.rejeitarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.RESPONSAVEL
                )
        );

        assertEquals(status, reserva.getStatus());
        assertSame(responsavelInicial, reserva.getResponsavel());
        assertSame(administradorInicial, reserva.getAdministrador());
        assertEquals(aprovadoEmInicial, reserva.getAprovadoEm());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(responsavelRepository).findById(APROVADOR_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(salaRepository, solicitanteRepository, administradorRepository);
    }

    @ParameterizedTest(name = "rejeição por administrador com estado {0}")
    @EnumSource(value = StatusReserva.class, mode = EnumSource.Mode.EXCLUDE, names = "SOLICITADA")
    void rejeitarReservaPorAdministradorComEstadoIncompativelPreservaReserva(
            StatusReserva status
    ) {
        Reserva reserva = reservaParaAlteracao(status);
        Responsavel responsavelInicial = responsavelDeTeste();
        Administrador administradorInicial = administradorDeTeste();
        LocalDateTime aprovadoEmInicial = INICIO.minusDays(1);
        reserva.setResponsavel(responsavelInicial);
        reserva.setAdministrador(administradorInicial);
        reserva.setAprovadoEm(aprovadoEmInicial);
        when(reservaRepository.buscarPorIdComBloqueio(RESERVA_ID))
                .thenReturn(Optional.of(reserva));
        when(administradorRepository.findById(APROVADOR_ID))
                .thenReturn(Optional.of(administradorDeTeste()));

        assertThrows(
                IllegalStateException.class,
                () -> reservaService.rejeitarReserva(
                        RESERVA_ID, APROVADOR_ID, TipoAprovadorReserva.ADMINISTRADOR
                )
        );

        assertEquals(status, reserva.getStatus());
        assertSame(responsavelInicial, reserva.getResponsavel());
        assertSame(administradorInicial, reserva.getAdministrador());
        assertEquals(aprovadoEmInicial, reserva.getAprovadoEm());
        verify(reservaRepository).buscarPorIdComBloqueio(RESERVA_ID);
        verify(administradorRepository).findById(APROVADOR_ID);
        verify(reservaRepository, never()).save(any(Reserva.class));
        verifyNoInteractions(salaRepository, solicitanteRepository, responsavelRepository);
    }

    private void verificarNenhumRepositorioConsultado() {
        verifyNoInteractions(
                reservaRepository,
                salaRepository,
                solicitanteRepository,
                responsavelRepository,
                administradorRepository
        );
    }

    private Sala salaDisponivel() {
        return new Sala(SALA_ID, 30, StatusSala.DISPONIVEL);
    }

    private Solicitante solicitanteProfessor() {
        Solicitante solicitante = new Solicitante();
        solicitante.setId(SOLICITANTE_ID);
        solicitante.setTipoSolicitante(TipoSolicitante.PROFESSOR);
        return solicitante;
    }

    private Reserva reservaParaAlteracao(StatusReserva status) {
        Reserva reserva = new Reserva();
        reserva.setId(RESERVA_ID);
        reserva.setSala(salaDisponivel());
        reserva.setSolicitante(solicitanteProfessor());
        reserva.setInicio(INICIO);
        reserva.setFim(FIM);
        reserva.setStatus(status);
        return reserva;
    }

    private Responsavel responsavelDeTeste() {
        Responsavel responsavel = new Responsavel();
        responsavel.setId(APROVADOR_ID);
        return responsavel;
    }

    private Administrador administradorDeTeste() {
        Administrador administrador = new Administrador();
        administrador.setId(APROVADOR_ID);
        return administrador;
    }
}
