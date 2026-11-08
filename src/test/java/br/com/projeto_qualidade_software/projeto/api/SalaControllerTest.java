package br.com.projeto_qualidade_software.projeto.api;

import br.com.projeto_qualidade_software.projeto.entities.Sala;
import br.com.projeto_qualidade_software.projeto.enums.StatusSala;
import br.com.projeto_qualidade_software.projeto.services.SalaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalaController.class)
class SalaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SalaService salaService;

    @Test
    @DisplayName("CP-89 / RF05 - Lista salas com os campos do contrato e sem dados internos")
    void listarSalasRetornaCamposPublicosDeCadaSala() throws Exception {
        when(salaService.listarSalas()).thenReturn(List.of(
                new Sala(101L, 30, StatusSala.DISPONIVEL),
                new Sala(204L, 24, StatusSala.EM_MANUTENCAO),
                new Sala(305L, 40, StatusSala.RESERVADA)
        ));

        mockMvc.perform(get("/api/salas"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [
                          {"id": 101, "capacidade": 30, "statusSala": "DISPONIVEL"},
                          {"id": 204, "capacidade": 24, "statusSala": "EM_MANUTENCAO"},
                          {"id": 305, "capacidade": 40, "statusSala": "RESERVADA"}
                        ]
                        """, true));

        verify(salaService).listarSalas();
    }

    @Test
    @DisplayName("CP-90 / RF05 - Lista vazia quando não há salas cadastradas")
    void listarSalasRetornaListaVaziaQuandoNaoHaSalas() throws Exception {
        when(salaService.listarSalas()).thenReturn(List.of());

        mockMvc.perform(get("/api/salas"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));

        verify(salaService).listarSalas();
    }
}
