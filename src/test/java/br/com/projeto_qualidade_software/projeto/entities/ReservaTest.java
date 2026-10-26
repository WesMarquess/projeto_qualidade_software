package br.com.projeto_qualidade_software.projeto.entities;

import br.com.projeto_qualidade_software.projeto.enums.StatusReserva;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservaTest {

    @Test
    void intervaloComInicioAnteriorAoFimEValido() {
        Reserva reserva = new Reserva();
        reserva.setInicio(LocalDateTime.of(2026, 10, 26, 10, 0));
        reserva.setFim(LocalDateTime.of(2026, 10, 26, 11, 0));

        assertTrue(reserva.isIntervaloValido());
    }

    @Test
    void intervaloComInicioEFimIguaisEInvalido() {
        LocalDateTime inicioEFim = LocalDateTime.of(2026, 10, 26, 10, 0);
        Reserva reserva = new Reserva();
        reserva.setInicio(inicioEFim);
        reserva.setFim(inicioEFim);

        assertFalse(reserva.isIntervaloValido());
    }

    @Test
    void intervaloComFimAnteriorAoInicioEInvalido() {
        Reserva reserva = new Reserva();
        reserva.setInicio(LocalDateTime.of(2026, 10, 26, 11, 0));
        reserva.setFim(LocalDateTime.of(2026, 10, 26, 10, 0));

        assertFalse(reserva.isIntervaloValido());
    }

    @Test
    void intervaloComInicioNuloEValidoSegundoComportamentoAtual() {
        Reserva reserva = new Reserva();
        reserva.setFim(LocalDateTime.of(2026, 10, 26, 11, 0));

        assertTrue(reserva.isIntervaloValido());
    }

    @Test
    void intervaloComFimNuloEValidoSegundoComportamentoAtual() {
        Reserva reserva = new Reserva();
        reserva.setInicio(LocalDateTime.of(2026, 10, 26, 10, 0));

        assertTrue(reserva.isIntervaloValido());
    }

    @Test
    void aprovarPorResponsavelRegistraAprovacaoEAssociacaoCorreta() {
        Reserva reserva = new Reserva();
        Responsavel responsavel = new Responsavel();
        reserva.setAdministrador(new Administrador());

        reserva.aprovar(responsavel);

        assertEquals(StatusReserva.APROVADA, reserva.getStatus());
        assertSame(responsavel, reserva.getResponsavel());
        assertNull(reserva.getAdministrador());
        assertNotNull(reserva.getAprovadoEm());
    }

    @Test
    void aprovarPorAdministradorRegistraAprovacaoEAssociacaoCorreta() {
        Reserva reserva = new Reserva();
        Administrador administrador = new Administrador();
        reserva.setResponsavel(new Responsavel());

        reserva.aprovar(administrador);

        assertEquals(StatusReserva.APROVADA, reserva.getStatus());
        assertNull(reserva.getResponsavel());
        assertSame(administrador, reserva.getAdministrador());
        assertNotNull(reserva.getAprovadoEm());
    }

    @Test
    void rejeitarPorResponsavelRegistraRejeicaoEAssociacaoCorreta() {
        Reserva reserva = new Reserva();
        Responsavel responsavel = new Responsavel();
        reserva.setAdministrador(new Administrador());

        reserva.rejeitar(responsavel);

        assertEquals(StatusReserva.REJEITADA, reserva.getStatus());
        assertSame(responsavel, reserva.getResponsavel());
        assertNull(reserva.getAdministrador());
        assertNull(reserva.getAprovadoEm());
    }

    @Test
    void rejeitarPorAdministradorRegistraRejeicaoEAssociacaoCorreta() {
        Reserva reserva = new Reserva();
        Administrador administrador = new Administrador();
        reserva.setResponsavel(new Responsavel());

        reserva.rejeitar(administrador);

        assertEquals(StatusReserva.REJEITADA, reserva.getStatus());
        assertNull(reserva.getResponsavel());
        assertSame(administrador, reserva.getAdministrador());
        assertNull(reserva.getAprovadoEm());
    }

    @Test
    void aprovarReservaForaDoEstadoSolicitadoLancaExcecaoEPreservaEstado() {
        Reserva reserva = new Reserva();
        Responsavel responsavelAnterior = new Responsavel();
        Administrador administradorAnterior = new Administrador();
        LocalDateTime aprovadoEmAnterior = LocalDateTime.of(2026, 10, 25, 9, 0);
        reserva.setStatus(StatusReserva.APROVADA);
        reserva.setResponsavel(responsavelAnterior);
        reserva.setAdministrador(administradorAnterior);
        reserva.setAprovadoEm(aprovadoEmAnterior);

        assertThrows(IllegalStateException.class, () -> reserva.aprovar(new Responsavel()));

        assertEquals(StatusReserva.APROVADA, reserva.getStatus());
        assertSame(responsavelAnterior, reserva.getResponsavel());
        assertSame(administradorAnterior, reserva.getAdministrador());
        assertEquals(aprovadoEmAnterior, reserva.getAprovadoEm());
    }

    @Test
    void rejeitarReservaForaDoEstadoSolicitadoLancaExcecaoEPreservaEstado() {
        Reserva reserva = new Reserva();
        Responsavel responsavelAnterior = new Responsavel();
        Administrador administradorAnterior = new Administrador();
        LocalDateTime aprovadoEmAnterior = LocalDateTime.of(2026, 10, 25, 9, 0);
        reserva.setStatus(StatusReserva.APROVADA);
        reserva.setResponsavel(responsavelAnterior);
        reserva.setAdministrador(administradorAnterior);
        reserva.setAprovadoEm(aprovadoEmAnterior);

        assertThrows(IllegalStateException.class, () -> reserva.rejeitar(new Administrador()));

        assertEquals(StatusReserva.APROVADA, reserva.getStatus());
        assertSame(responsavelAnterior, reserva.getResponsavel());
        assertSame(administradorAnterior, reserva.getAdministrador());
        assertEquals(aprovadoEmAnterior, reserva.getAprovadoEm());
    }
}
