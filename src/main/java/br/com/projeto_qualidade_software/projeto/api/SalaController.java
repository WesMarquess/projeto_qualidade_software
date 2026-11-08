package br.com.projeto_qualidade_software.projeto.api;

import br.com.projeto_qualidade_software.projeto.api.dto.SalaResponse;
import br.com.projeto_qualidade_software.projeto.services.SalaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/salas")
public class SalaController {

    private final SalaService salaService;

    public SalaController(SalaService salaService) {
        this.salaService = salaService;
    }

    @GetMapping
    public List<SalaResponse> listar() {
        return salaService.listarSalas()
                .stream()
                .map(SalaResponse::de)
                .toList();
    }
}
