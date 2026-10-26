package br.com.projeto_qualidade_software.projeto.api;

import br.com.projeto_qualidade_software.projeto.api.dto.AcaoReservaRequest;
import br.com.projeto_qualidade_software.projeto.api.dto.AlterarHorarioReservaRequest;
import br.com.projeto_qualidade_software.projeto.api.dto.DecisaoReservaRequest;
import br.com.projeto_qualidade_software.projeto.api.dto.ReservaResponse;
import br.com.projeto_qualidade_software.projeto.api.dto.SolicitarReservaRequest;
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
import br.com.projeto_qualidade_software.projeto.services.ReservaService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservaController.class)
@Import(ApiExceptionHandler.class)
class ReservaControllerTest {

    private static final Long SALA_ID = 1L;
    private static final Long SOLICITANTE_ID = 2L;
    private static final Long RESPONSAVEL_ID = 3L;
    private static final Long ADMINISTRADOR_ID = 4L;
    private static final Long RESERVA_ID = 5L;
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 27, 10, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 10, 27, 11, 0);
    private static final LocalDateTime SOLICITADO_EM =
            LocalDateTime.of(2026, 10, 26, 9, 0);
    private static final LocalDateTime APROVADO_EM =
            LocalDateTime.of(2026, 10, 26, 9, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    @Test
    void solicitarReservaRetornaCreatedLocationEReserva() throws Exception {
        Reserva reserva = reserva(StatusReserva.SOLICITADA);
        when(reservaService.solicitarReserva(
                SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, FIM
        )).thenReturn(reserva);

        mockMvc.perform(post("/api/reservas")
                        .contentType("application/json")
                        .content("""
                                {
                                  "salaId": 1,
                                  "solicitanteId": 2,
                                  "tipoSolicitante": "PROFESSOR",
                                  "inicio": "2026-10-27T10:00:00",
                                  "fim": "2026-10-27T11:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/api/reservas/5")))
                .andExpect(jsonPath("$.id").value(RESERVA_ID))
                .andExpect(jsonPath("$.salaId").value(SALA_ID))
                .andExpect(jsonPath("$.solicitanteId").value(SOLICITANTE_ID))
                .andExpect(jsonPath("$.status").value("SOLICITADA"));

        verify(reservaService).solicitarReserva(
                SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, FIM
        );
    }

    @Test
    void buscarReservaRetornaReservaEncontrada() throws Exception {
        when(reservaService.buscarReserva(RESERVA_ID))
                .thenReturn(reserva(StatusReserva.APROVADA));

        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESERVA_ID))
                .andExpect(jsonPath("$.salaId").value(SALA_ID))
                .andExpect(jsonPath("$.solicitanteId").value(SOLICITANTE_ID))
                .andExpect(jsonPath("$.responsavelId").value(RESPONSAVEL_ID))
                .andExpect(jsonPath("$.administradorId").doesNotExist())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.inicio").value("2026-10-27T10:00:00"));

        verify(reservaService).buscarReserva(RESERVA_ID);
    }

    @Test
    void listarReservasDoSolicitanteRetornaListaDeReservas() throws Exception {
        when(reservaService.listarReservasDoSolicitante(SOLICITANTE_ID))
                .thenReturn(List.of(reserva(StatusReserva.SOLICITADA)));

        mockMvc.perform(get("/api/reservas/solicitante/{solicitanteId}", SOLICITANTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(RESERVA_ID))
                .andExpect(jsonPath("$[0].salaId").value(SALA_ID))
                .andExpect(jsonPath("$[0].solicitanteId").value(SOLICITANTE_ID))
                .andExpect(jsonPath("$[0].status").value("SOLICITADA"));

        verify(reservaService).listarReservasDoSolicitante(SOLICITANTE_ID);
    }

    @Test
    void alterarHorarioRetornaReservaComHorarioAtualizado() throws Exception {
        when(reservaService.alterarHorarioReserva(RESERVA_ID, SOLICITANTE_ID, INICIO, FIM))
                .thenReturn(reserva(StatusReserva.SOLICITADA));

        mockMvc.perform(put("/api/reservas/{reservaId}/horario", RESERVA_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "solicitanteId": 2,
                                  "inicio": "2026-10-27T10:00:00",
                                  "fim": "2026-10-27T11:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESERVA_ID))
                .andExpect(jsonPath("$.inicio").value("2026-10-27T10:00:00"))
                .andExpect(jsonPath("$.fim").value("2026-10-27T11:00:00"))
                .andExpect(jsonPath("$.status").value("SOLICITADA"));

        verify(reservaService).alterarHorarioReserva(RESERVA_ID, SOLICITANTE_ID, INICIO, FIM);
    }

    @Test
    void cancelarReservaRetornaReservaCancelada() throws Exception {
        when(reservaService.cancelarReserva(RESERVA_ID, SOLICITANTE_ID))
                .thenReturn(reserva(StatusReserva.CANCELADA));

        mockMvc.perform(post("/api/reservas/{reservaId}/cancelamento", RESERVA_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "usuarioId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESERVA_ID))
                .andExpect(jsonPath("$.solicitanteId").value(SOLICITANTE_ID))
                .andExpect(jsonPath("$.status").value("CANCELADA"));

        verify(reservaService).cancelarReserva(RESERVA_ID, SOLICITANTE_ID);
    }

    @Test
    void aprovarReservaRetornaReservaAprovada() throws Exception {
        when(reservaService.aprovarReserva(
                RESERVA_ID, RESPONSAVEL_ID, TipoAprovadorReserva.RESPONSAVEL
        )).thenReturn(reserva(StatusReserva.APROVADA));

        mockMvc.perform(post("/api/reservas/{reservaId}/aprovacao", RESERVA_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "usuarioId": 3,
                                  "tipoAprovador": "RESPONSAVEL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESERVA_ID))
                .andExpect(jsonPath("$.responsavelId").value(RESPONSAVEL_ID))
                .andExpect(jsonPath("$.administradorId").doesNotExist())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.aprovadoEm").value("2026-10-26T09:30:00"));

        verify(reservaService).aprovarReserva(
                RESERVA_ID, RESPONSAVEL_ID, TipoAprovadorReserva.RESPONSAVEL
        );
    }

    @Test
    void rejeitarReservaRetornaReservaRejeitada() throws Exception {
        when(reservaService.rejeitarReserva(
                RESERVA_ID, ADMINISTRADOR_ID, TipoAprovadorReserva.ADMINISTRADOR
        )).thenReturn(reserva(StatusReserva.REJEITADA));

        mockMvc.perform(post("/api/reservas/{reservaId}/rejeicao", RESERVA_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "usuarioId": 4,
                                  "tipoAprovador": "ADMINISTRADOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESERVA_ID))
                .andExpect(jsonPath("$.administradorId").value(ADMINISTRADOR_ID))
                .andExpect(jsonPath("$.responsavelId").doesNotExist())
                .andExpect(jsonPath("$.status").value("REJEITADA"))
                .andExpect(jsonPath("$.aprovadoEm").value("2026-10-26T09:30:00"));

        verify(reservaService).rejeitarReserva(
                RESERVA_ID, ADMINISTRADOR_ID, TipoAprovadorReserva.ADMINISTRADOR
        );
    }

    @Test
    void buscarReservaInexistenteRetornaNotFound() throws Exception {
        when(reservaService.buscarReserva(RESERVA_ID))
                .thenThrow(new EntityNotFoundException("Reserva não encontrada: " + RESERVA_ID));

        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem")
                        .value("Reserva não encontrada: " + RESERVA_ID));

        verify(reservaService).buscarReserva(RESERVA_ID);
    }

    @Test
    void solicitarReservaComTipoNaoAutorizadoRetornaForbidden() throws Exception {
        when(reservaService.solicitarReserva(
                SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, FIM
        )).thenThrow(new AcessoNegadoException(
                "O cargo informado não corresponde ao perfil cadastrado do solicitante."
        ));

        mockMvc.perform(post("/api/reservas")
                        .contentType("application/json")
                        .content("""
                                {
                                  "salaId": 1,
                                  "solicitanteId": 2,
                                  "tipoSolicitante": "PROFESSOR",
                                  "inicio": "2026-10-27T10:00:00",
                                  "fim": "2026-10-27T11:00:00"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value(
                        "O cargo informado não corresponde ao perfil cadastrado do solicitante."
                ));

        verify(reservaService).solicitarReserva(
                SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, FIM
        );
    }

    @Test
    void solicitarReservaComIntervaloInvalidoRetornaBadRequest() throws Exception {
        when(reservaService.solicitarReserva(
                SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, INICIO
        )).thenThrow(new IllegalArgumentException("O término deve ser posterior ao início."));

        mockMvc.perform(post("/api/reservas")
                        .contentType("application/json")
                        .content("""
                                {
                                  "salaId": 1,
                                  "solicitanteId": 2,
                                  "tipoSolicitante": "PROFESSOR",
                                  "inicio": "2026-10-27T10:00:00",
                                  "fim": "2026-10-27T10:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem")
                        .value("O término deve ser posterior ao início."));

        verify(reservaService).solicitarReserva(
                SALA_ID, SOLICITANTE_ID, TipoSolicitante.PROFESSOR, INICIO, INICIO
        );
    }

    @Test
    void cancelarReservaEmEstadoNaoPermitidoRetornaConflict() throws Exception {
        when(reservaService.cancelarReserva(RESERVA_ID, SOLICITANTE_ID))
                .thenThrow(new IllegalStateException(
                        "Somente reservas solicitadas ou aprovadas podem ser canceladas."
                ));

        mockMvc.perform(post("/api/reservas/{reservaId}/cancelamento", RESERVA_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "usuarioId": 2
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(
                        "Somente reservas solicitadas ou aprovadas podem ser canceladas."
                ));

        verify(reservaService).cancelarReserva(RESERVA_ID, SOLICITANTE_ID);
    }

    @ParameterizedTest(name = "solicitação rejeita campo obrigatório ausente: {1}")
    @MethodSource("solicitarReservaComCampoObrigatorioAusente")
    void solicitarReservaComCampoObrigatorioAusenteRetornaBadRequest(
            String body,
            String field
    ) throws Exception {
        assertValidationError(
                post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(body),
                field
        );
    }

    static Stream<Arguments> solicitarReservaComCampoObrigatorioAusente() {
        return Stream.of(
                Arguments.of("""
                        {
                          "solicitanteId": 2,
                          "tipoSolicitante": "PROFESSOR",
                          "inicio": "2026-10-27T10:00:00",
                          "fim": "2026-10-27T11:00:00"
                        }
                        """, "salaId"),
                Arguments.of("""
                        {
                          "salaId": 1,
                          "tipoSolicitante": "PROFESSOR",
                          "inicio": "2026-10-27T10:00:00",
                          "fim": "2026-10-27T11:00:00"
                        }
                        """, "solicitanteId"),
                Arguments.of("""
                        {
                          "salaId": 1,
                          "solicitanteId": 2,
                          "inicio": "2026-10-27T10:00:00",
                          "fim": "2026-10-27T11:00:00"
                        }
                        """, "tipoSolicitante"),
                Arguments.of("""
                        {
                          "salaId": 1,
                          "solicitanteId": 2,
                          "tipoSolicitante": "PROFESSOR",
                          "fim": "2026-10-27T11:00:00"
                        }
                        """, "inicio"),
                Arguments.of("""
                        {
                          "salaId": 1,
                          "solicitanteId": 2,
                          "tipoSolicitante": "PROFESSOR",
                          "inicio": "2026-10-27T10:00:00"
                        }
                        """, "fim")
        );
    }

    @ParameterizedTest(name = "solicitação rejeita {0}={1}")
    @CsvSource({
            "salaId, null",
            "salaId, 0",
            "salaId, -1",
            "solicitanteId, null",
            "solicitanteId, 0",
            "solicitanteId, -1"
    })
    void solicitarReservaComIdNaoPositivoRetornaBadRequest(
            String field,
            String value
    ) throws Exception {
        String salaId = field.equals("salaId") ? value : "1";
        String solicitanteId = field.equals("solicitanteId") ? value : "2";
        String body = """
                {
                  "salaId": %s,
                  "solicitanteId": %s,
                  "tipoSolicitante": "PROFESSOR",
                  "inicio": "2026-10-27T10:00:00",
                  "fim": "2026-10-27T11:00:00"
                }
                """.formatted(salaId, solicitanteId);

        assertValidationError(
                post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(body),
                field
        );
    }

    @ParameterizedTest(name = "alteração rejeita campo obrigatório ausente: {1}")
    @MethodSource("alterarHorarioComCampoObrigatorioAusente")
    void alterarHorarioComCampoObrigatorioAusenteRetornaBadRequest(
            String body,
            String field
    ) throws Exception {
        assertValidationError(
                put("/api/reservas/{reservaId}/horario", RESERVA_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                field
        );
    }

    static Stream<Arguments> alterarHorarioComCampoObrigatorioAusente() {
        return Stream.of(
                Arguments.of("""
                        {
                          "inicio": "2026-10-27T10:00:00",
                          "fim": "2026-10-27T11:00:00"
                        }
                        """, "solicitanteId"),
                Arguments.of("""
                        {
                          "solicitanteId": 2,
                          "fim": "2026-10-27T11:00:00"
                        }
                        """, "inicio"),
                Arguments.of("""
                        {
                          "solicitanteId": 2,
                          "inicio": "2026-10-27T10:00:00"
                        }
                        """, "fim")
        );
    }

    @ParameterizedTest(name = "alteração rejeita solicitanteId={0}")
    @CsvSource({"null", "0", "-1"})
    void alterarHorarioComSolicitanteIdNaoPositivoRetornaBadRequest(String value)
            throws Exception {
        String body = """
                {
                  "solicitanteId": %s,
                  "inicio": "2026-10-27T10:00:00",
                  "fim": "2026-10-27T11:00:00"
                }
                """.formatted(value);

        assertValidationError(
                put("/api/reservas/{reservaId}/horario", RESERVA_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                "solicitanteId"
        );
    }

    @ParameterizedTest(name = "cancelamento rejeita usuarioId={0}")
    @MethodSource("valoresUsuarioIdAusentesOuNaoPositivos")
    void cancelarReservaComUsuarioIdAusenteOuNaoPositivoRetornaBadRequest(String value)
            throws Exception {
        String body = "{\"usuarioId\": %s}".formatted(value);

        assertValidationError(
                post("/api/reservas/{reservaId}/cancelamento", RESERVA_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                "usuarioId"
        );
    }

    static Stream<String> valoresUsuarioIdAusentesOuNaoPositivos() {
        return Stream.of("null", "0", "-1");
    }

    @ParameterizedTest(name = "decisão rejeita usuarioId={0}")
    @MethodSource("valoresUsuarioIdAusentesOuNaoPositivos")
    void decisaoComUsuarioIdAusenteOuNaoPositivoRetornaBadRequest(String value)
            throws Exception {
        String body = "{\"usuarioId\": %s, \"tipoAprovador\": \"RESPONSAVEL\"}"
                .formatted(value);

        assertValidationError(
                post("/api/reservas/{reservaId}/aprovacao", RESERVA_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                "usuarioId"
        );
    }

    @ParameterizedTest(name = "decisão rejeita tipoAprovador {0}")
    @MethodSource("decisaoSemTipoAprovador")
    void decisaoComTipoAprovadorAusenteOuNuloRetornaBadRequest(
            String scenario,
            String body
    ) throws Exception {
        assertValidationError(
                post("/api/reservas/{reservaId}/aprovacao", RESERVA_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                "tipoAprovador"
        );
    }

    static Stream<Arguments> decisaoSemTipoAprovador() {
        return Stream.of(
                Arguments.of("ausente", "{\"usuarioId\": 2}"),
                Arguments.of("nulo", "{\"usuarioId\": 2, \"tipoAprovador\": null}")
        );
    }

    @Test
    void corpoJsonAusenteRetornaBadRequest() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "O corpo da requisição está ausente ou malformado."
                ));

        verifyNoInteractions(reservaService);
    }

    @Test
    void corpoJsonMalformadoRetornaBadRequest() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "O corpo da requisição está ausente ou malformado."
                ));

        verifyNoInteractions(reservaService);
    }

    private void assertValidationError(
            MockHttpServletRequestBuilder request,
            String field
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", startsWith(field + ":")));

        verifyNoInteractions(reservaService);
    }

    private Reserva reserva(StatusReserva status) {
        Sala sala = new Sala(SALA_ID, 30, StatusSala.DISPONIVEL);
        Solicitante solicitante = new Solicitante();
        solicitante.setId(SOLICITANTE_ID);
        solicitante.setTipoSolicitante(TipoSolicitante.PROFESSOR);

        Reserva reserva = new Reserva();
        reserva.setId(RESERVA_ID);
        reserva.setSala(sala);
        reserva.setSolicitante(solicitante);
        reserva.setInicio(INICIO);
        reserva.setFim(FIM);
        reserva.setStatus(status);
        reserva.setSolicitadoEm(SOLICITADO_EM);

        if (status == StatusReserva.APROVADA) {
            Responsavel responsavel = new Responsavel();
            responsavel.setId(RESPONSAVEL_ID);
            reserva.setResponsavel(responsavel);
            reserva.setAprovadoEm(APROVADO_EM);
        } else if (status == StatusReserva.REJEITADA) {
            Administrador administrador = new Administrador();
            administrador.setId(ADMINISTRADOR_ID);
            reserva.setAdministrador(administrador);
            reserva.setAprovadoEm(APROVADO_EM);
        }

        return reserva;
    }
}
