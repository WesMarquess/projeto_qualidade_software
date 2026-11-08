package br.com.projeto_qualidade_software.projeto.config;

import br.com.projeto_qualidade_software.projeto.entities.Administrador;
import br.com.projeto_qualidade_software.projeto.entities.Responsavel;
import br.com.projeto_qualidade_software.projeto.entities.Sala;
import br.com.projeto_qualidade_software.projeto.entities.Solicitante;
import br.com.projeto_qualidade_software.projeto.enums.StatusSala;
import br.com.projeto_qualidade_software.projeto.enums.TipoSolicitante;
import br.com.projeto_qualidade_software.projeto.repositories.AdministradorRepository;
import br.com.projeto_qualidade_software.projeto.repositories.ResponsavelRepository;
import br.com.projeto_qualidade_software.projeto.repositories.SalaRepository;
import br.com.projeto_qualidade_software.projeto.repositories.SolicitanteRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Cria dados sinteticos para teste manual. So roda quando app.seed.enabled=true
 * e apenas se ainda nao existir nenhuma sala.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DevDataInitializer implements ApplicationRunner {

    private final SalaRepository salaRepository;
    private final SolicitanteRepository solicitanteRepository;
    private final ResponsavelRepository responsavelRepository;
    private final AdministradorRepository administradorRepository;

    public DevDataInitializer(
            SalaRepository salaRepository,
            SolicitanteRepository solicitanteRepository,
            ResponsavelRepository responsavelRepository,
            AdministradorRepository administradorRepository
    ) {
        this.salaRepository = salaRepository;
        this.solicitanteRepository = solicitanteRepository;
        this.responsavelRepository = responsavelRepository;
        this.administradorRepository = administradorRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (salaRepository.count() > 0) {
            return;
        }

        LocalDate hoje = LocalDate.now();

        salaRepository.save(new Sala(null, 30, StatusSala.DISPONIVEL));

        solicitanteRepository.save(new Solicitante(
                null, "Professor Teste", "professor@teste.local", "senha-ficticia",
                TipoSolicitante.PROFESSOR, hoje, hoje));

        responsavelRepository.save(new Responsavel(
                null, "Responsavel Teste", "responsavel@teste.local", "senha-ficticia",
                hoje, hoje));

        administradorRepository.save(new Administrador(
                null, "Administrador Teste", "admin@teste.local", "senha-ficticia",
                hoje, hoje));
    }
}