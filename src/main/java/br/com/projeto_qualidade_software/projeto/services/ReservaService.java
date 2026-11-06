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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ReservaService {

    private static final Set<StatusReserva> STATUS_QUE_OCUPAM_SALA = EnumSet.of(
            StatusReserva.SOLICITADA,
            StatusReserva.APROVADA,
            StatusReserva.EM_USO
    );

    private final ReservaRepository reservaRepository;
    private final SalaRepository salaRepository;
    private final SolicitanteRepository solicitanteRepository;
    private final ResponsavelRepository responsavelRepository;
    private final AdministradorRepository administradorRepository;

    public ReservaService(
            ReservaRepository reservaRepository,
            SalaRepository salaRepository,
            SolicitanteRepository solicitanteRepository,
            ResponsavelRepository responsavelRepository,
            AdministradorRepository administradorRepository
    ) {
        this.reservaRepository = reservaRepository;
        this.salaRepository = salaRepository;
        this.solicitanteRepository = solicitanteRepository;
        this.responsavelRepository = responsavelRepository;
        this.administradorRepository = administradorRepository;
    }

    @Transactional
    public Reserva solicitarReserva(
            Long salaId,
            Long solicitanteId,
            TipoSolicitante tipoSolicitante,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        validarIntervalo(inicio, fim);
        Sala sala = buscarSalaBloqueada(salaId);
        validarDisponibilidadeDaSala(sala);
        Solicitante solicitante = solicitanteRepository.findById(solicitanteId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Solicitante não encontrado: " + solicitanteId
                ));
        if (!Objects.equals(solicitante.getTipoSolicitante(), tipoSolicitante)) {
            throw new AcessoNegadoException(
                    "O cargo informado não corresponde ao perfil cadastrado do solicitante."
            );
        }
        validarAusenciaDeConflito(sala.getId(), inicio, fim, null);

        Reserva reserva = new Reserva();
        reserva.setSala(sala);
        reserva.setSolicitante(solicitante);
        reserva.setInicio(inicio);
        reserva.setFim(fim);
        reserva.setStatus(StatusReserva.SOLICITADA);
        return reservaRepository.save(reserva);
    }

    @Transactional
    public Reserva alterarHorarioReserva(
            Long reservaId,
            Long solicitanteId,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        validarIntervalo(inicio, fim);
        Reserva reserva = buscarReservaBloqueada(reservaId);
        validarSolicitante(reserva, solicitanteId);
        if (reserva.getStatus() != StatusReserva.SOLICITADA) {
            throw new IllegalStateException(
                    "Somente reservas solicitadas podem ter o horário alterado."
            );
        }

        Sala sala = buscarSalaBloqueada(reserva.getSala().getId());
        validarDisponibilidadeDaSala(sala);
        validarAusenciaDeConflito(sala.getId(), inicio, fim, reservaId);

        reserva.setInicio(inicio);
        reserva.setFim(fim);
        return reservaRepository.save(reserva);
    }

    @Transactional
    public Reserva cancelarReserva(Long reservaId, Long solicitanteId) {
        Reserva reserva = buscarReservaBloqueada(reservaId);
        validarSolicitante(reserva, solicitanteId);
        if (reserva.getStatus() != StatusReserva.SOLICITADA
                && reserva.getStatus() != StatusReserva.APROVADA) {
            throw new IllegalStateException(
                    "Somente reservas solicitadas ou aprovadas podem ser canceladas."
            );
        }

        reserva.setStatus(StatusReserva.CANCELADA);
        return reservaRepository.save(reserva);
    }

    @Transactional
    public Reserva aprovarReserva(
            Long reservaId,
            Long usuarioId,
            TipoAprovadorReserva tipoAprovador
    ) {
        Reserva reserva = buscarReservaBloqueada(reservaId);
        if (tipoAprovador == TipoAprovadorReserva.RESPONSAVEL) {
            reserva.aprovar(buscarResponsavel(usuarioId));
        } else if (tipoAprovador == TipoAprovadorReserva.ADMINISTRADOR) {
            reserva.aprovar(buscarAdministrador(usuarioId));
        } else {
            throw new AcessoNegadoException(
                    "Somente Responsável ou Administrador pode aprovar reservas."
            );
        }
        return reservaRepository.save(reserva);
    }

    @Transactional
    public Reserva rejeitarReserva(
            Long reservaId,
            Long usuarioId,
            TipoAprovadorReserva tipoAprovador
    ) {
        Reserva reserva = buscarReservaBloqueada(reservaId);
        if (tipoAprovador == TipoAprovadorReserva.RESPONSAVEL) {
            reserva.rejeitar(buscarResponsavel(usuarioId));
        } else if (tipoAprovador == TipoAprovadorReserva.ADMINISTRADOR) {
            reserva.rejeitar(buscarAdministrador(usuarioId));
        } else {
            throw new AcessoNegadoException(
                    "Somente Responsável ou Administrador pode decidir reservas."
            );
        }
        return reservaRepository.save(reserva);
    }

    @Transactional(readOnly = true)
    public Reserva buscarReserva(Long reservaId) {
        return reservaRepository.findById(reservaId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Reserva não encontrada: " + reservaId
                ));
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarReservasDoSolicitante(Long solicitanteId) {
        return reservaRepository.findBySolicitanteIdOrderByInicioDesc(solicitanteId);
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarReservasPendentes() {
        return reservaRepository.findByStatusOrderBySolicitadoEmAsc(
                StatusReserva.SOLICITADA
        );
    }

    private Reserva buscarReservaBloqueada(Long reservaId) {
        return reservaRepository.buscarPorIdComBloqueio(reservaId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Reserva não encontrada: " + reservaId
                ));
    }

    private Sala buscarSalaBloqueada(Long salaId) {
        return salaRepository.buscarPorIdComBloqueio(salaId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Sala não encontrada: " + salaId
                ));
    }

    private Responsavel buscarResponsavel(Long responsavelId) {
        return responsavelRepository.findById(responsavelId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Responsável não encontrado: " + responsavelId
                ));
    }

    private Administrador buscarAdministrador(Long administradorId) {
        return administradorRepository.findById(administradorId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Administrador não encontrado: " + administradorId
                ));
    }

    private void validarSolicitante(Reserva reserva, Long solicitanteId) {
        if (!Objects.equals(reserva.getSolicitante().getId(), solicitanteId)) {
            throw new AcessoNegadoException(
                    "O solicitante não tem permissão para alterar esta reserva."
            );
        }
    }

    private void validarDisponibilidadeDaSala(Sala sala) {
        if (sala.getStatusSala() != StatusSala.DISPONIVEL) {
            throw new IllegalStateException(
                    "A sala não está disponível para novas reservas."
            );
        }
    }

    private void validarAusenciaDeConflito(
            Long salaId,
            LocalDateTime inicio,
            LocalDateTime fim,
            Long reservaId
    ) {
        if (reservaRepository.contarConflitos(
                salaId,
                STATUS_QUE_OCUPAM_SALA,
                inicio,
                fim,
                reservaId
        ) > 0) {
            throw new IllegalStateException(
                    "Já existe uma reserva para esta sala no intervalo informado."
            );
        }
    }

    private void validarIntervalo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new IllegalArgumentException(
                    "O término deve ser posterior ao início."
            );
        }
    }
}