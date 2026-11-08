package br.com.projeto_qualidade_software.projeto.services;

import br.com.projeto_qualidade_software.projeto.entities.Sala;
import br.com.projeto_qualidade_software.projeto.enums.StatusSala;
import br.com.projeto_qualidade_software.projeto.repositories.SalaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalaServiceTest {

    private SalaRepository salaRepository;
    private SalaService salaService;

    @BeforeEach
    void setUp() {
        salaRepository = mock(SalaRepository.class);
        salaService = new SalaService(salaRepository);
    }

    @Test
    @DisplayName("CB-59 / RF05 - Consulta salas usando findAll do repository")
    void listarSalasConsultaTodasAsSalasDoRepository() {
        List<Sala> salas = List.of(
                new Sala(101L, 30, StatusSala.DISPONIVEL),
                new Sala(204L, 24, StatusSala.EM_MANUTENCAO)
        );
        when(salaRepository.findAll()).thenReturn(salas);

        assertEquals(salas, salaService.listarSalas());

        verify(salaRepository).findAll();
    }
}
