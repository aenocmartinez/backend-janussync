package co.edu.unicolmayor.janussync.infrastructure.config;

import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean ListarUsuariosUseCase listarUsuarios(UsuarioRepository usuarios) { return new ListarUsuariosUseCase(usuarios); }
    @Bean ObtenerUsuarioUseCase obtenerUsuario(UsuarioRepository usuarios) { return new ObtenerUsuarioUseCase(usuarios); }
    @Bean co.edu.unicolmayor.janussync.application.usecase.GestionarRolesUseCase gestionarRoles(co.edu.unicolmayor.janussync.domain.port.GestionRolesRepository gestion,RolRepository roles,AuditoriaPort audit) { return new co.edu.unicolmayor.janussync.application.usecase.GestionarRolesUseCase(gestion,roles,audit); }
    @Bean ListarRolesUseCase listarRoles(RolRepository roles) { return new ListarRolesUseCase(roles); }

    @Bean @org.springframework.context.annotation.Profile("jwt") GestionarUsuariosUseCase gestionarUsuarios(GestionUsuariosRepository g,UsuarioRepository u,GestionRolesRepository a,RolRepository r,AuditoriaPort audit,java.time.Clock reloj){return new GestionarUsuariosUseCase(g,u,a,r,audit,reloj);}
    @Bean @org.springframework.context.annotation.Profile("jwt") ContrasenasUseCase contrasenas(GestionUsuariosRepository g,GestionarUsuariosUseCase u,AccesoRepository a,ProtectorCredenciales p,CorreoPort c,AuditoriaPort audit,java.time.Clock reloj,org.springframework.core.env.Environment e){
        String url=e.getProperty("janussync.correo.url-publica","http://localhost:3000");
        java.net.URI uri=java.net.URI.create(url);
        if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getScheme().equals("https") || e.matchesProfiles("local")&&uri.getScheme().equals("http")))throw new IllegalStateException("Configura una URL pública válida para el frontend.");
        var invitacion=java.time.Duration.parse(e.getProperty("janussync.correo.invitacion-ttl","P7D"));var reset=java.time.Duration.parse(e.getProperty("janussync.correo.recuperacion-ttl","PT60M"));
        if(invitacion.isNegative()||invitacion.isZero()||reset.isNegative()||reset.isZero())throw new IllegalStateException("Configura vencimientos positivos de enlaces.");
        long pausa=java.time.Duration.parse(e.getProperty("janussync.correo.pausa-envios","PT1M")).getSeconds();
        if(pausa<1)throw new IllegalStateException("Configura una pausa positiva entre envíos.");
        return new ContrasenasUseCase(g,u,a,p,c,audit,reloj,url.replaceAll("/+$",""),invitacion,reset,pausa);
    }
}
