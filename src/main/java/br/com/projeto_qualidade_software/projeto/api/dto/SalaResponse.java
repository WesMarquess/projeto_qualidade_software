package br.com.projeto_qualidade_software.projeto.api.dto;

import br.com.projeto_qualidade_software.projeto.entities.Sala;
import br.com.projeto_qualidade_software.projeto.enums.StatusSala;

public record SalaResponse(
        Long id,
        Integer capacidade,
        StatusSala statusSala
) {

    public static SalaResponse de(Sala sala) {
        return new SalaResponse(
                sala.getId(),
                sala.getCapacidade(),
                sala.getStatusSala()
        );
    }
}
