package co.edu.unicolmayor.janussync.infrastructure.config;

import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean ListarUsuariosUseCase listarUsuarios(UsuarioRepository usuarios) { return new ListarUsuariosUseCase(usuarios); }
    @Bean ObtenerUsuarioUseCase obtenerUsuario(UsuarioRepository usuarios) { return new ObtenerUsuarioUseCase(usuarios); }
    @Bean ListarRolesUseCase listarRoles(RolRepository roles) { return new ListarRolesUseCase(roles); }
}
