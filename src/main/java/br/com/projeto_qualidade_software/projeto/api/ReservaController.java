package br.com.projeto_qualidade_software.projeto.api;

import br.com.projeto_qualidade_software.projeto.api.dto.AcaoReservaRequest;
import br.com.projeto_qualidade_software.projeto.api.dto.AlterarHorarioReservaRequest;
import br.com.projeto_qualidade_software.projeto.api.dto.DecisaoReservaRequest;
import br.com.projeto_qualidade_software.projeto.api.dto.ReservaResponse;
import br.com.projeto_qualidade_software.projeto.api.dto.SolicitarReservaRequest;
import br.com.projeto_qualidade_software.projeto.services.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> solicitar(
            @Valid @RequestBody SolicitarReservaRequest request
    ) {
        ReservaResponse reserva = ReservaResponse.de(reservaService.solicitarReserva(
                request.salaId(),
                request.solicitanteId(),
                request.tipoSolicitante(),
                request.inicio(),
                request.fim()
        ));
        URI localizacao = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reserva.id())
                .toUri();
        return ResponseEntity.created(localizacao).body(reserva);
    }

    @GetMapping("/{reservaId}")
    public ReservaResponse buscar(@PathVariable Long reservaId) {
        return ReservaResponse.de(reservaService.buscarReserva(reservaId));
    }

    @GetMapping("/solicitante/{solicitanteId}")
    public List<ReservaResponse> listarDoSolicitante(@PathVariable Long solicitanteId) {
        return reservaService.listarReservasDoSolicitante(solicitanteId)
                .stream()
                .map(ReservaResponse::de)
                .toList();
    }

    @GetMapping("/pendentes")
    public List<ReservaResponse> listarPendentes() {
        return reservaService.listarReservasPendentes()
                .stream()
                .map(ReservaResponse::de)
                .toList();
    }

    @PutMapping("/{reservaId}/horario")
    public ReservaResponse alterarHorario(
            @PathVariable Long reservaId,
            @Valid @RequestBody AlterarHorarioReservaRequest request
    ) {
        return ReservaResponse.de(reservaService.alterarHorarioReserva(
                reservaId,
                request.solicitanteId(),
                request.inicio(),
                request.fim()
        ));
    }

    @PostMapping("/{reservaId}/cancelamento")
    public ReservaResponse cancelar(
            @PathVariable Long reservaId,
            @Valid @RequestBody AcaoReservaRequest request
    ) {
        return ReservaResponse.de(reservaService.cancelarReserva(
                reservaId,
                request.usuarioId()
        ));
    }

    @PostMapping("/{reservaId}/aprovacao")
    public ReservaResponse aprovar(
            @PathVariable Long reservaId,
            @Valid @RequestBody DecisaoReservaRequest request
    ) {
        return ReservaResponse.de(reservaService.aprovarReserva(
                reservaId,
                request.usuarioId(),
                request.tipoAprovador()
        ));
    }

    @PostMapping("/{reservaId}/rejeicao")
    public ReservaResponse rejeitar(
            @PathVariable Long reservaId,
            @Valid @RequestBody DecisaoReservaRequest request
    ) {
        return ReservaResponse.de(reservaService.rejeitarReserva(
                reservaId,
                request.usuarioId(),
                request.tipoAprovador()
        ));
    }
}