package co.edu.unicolmayor.janussync.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Acceso técnico provisional para probar consultas locales. No es el login del producto. */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@EnableWebSecurity
public class SeguridadLocalConfiguration {
    @Bean
    UserDetailsService usuarioTecnico(@Value("${janussync.local.usuario}") String usuario,
            @Value("${janussync.local.contrasena}") String contrasena) {
        if (usuario.isBlank() || contrasena.codePointCount(0, contrasena.length()) < 15
                || contrasena.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Configure JANUS_LOCAL_USER y una JANUS_LOCAL_PASSWORD de al menos 15 caracteres y hasta 72 bytes.");
        }
        var encoder = new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(User.withUsername(usuario).password("{bcrypt}" + encoder.encode(contrasena))
                .authorities("USUARIOS_CONSULTAR", "ROLES_CONSULTAR").build());
    }

    @Bean
    SecurityFilterChain seguridadLocal(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios", "/api/v1/usuarios/*").hasAuthority("USUARIOS_CONSULTAR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles").hasAuthority("ROLES_CONSULTAR")
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic.authenticationEntryPoint((peticion, respuesta, error) -> {
                    respuesta.setStatus(401);
                    respuesta.setHeader("WWW-Authenticate", "Basic realm=\"JanusSync local\"");
                    respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    respuesta.getWriter().write("{\"status\":401,\"title\":\"Acceso no autorizado\"}");
                }))
                .exceptionHandling(errores -> errores.accessDeniedHandler((peticion, respuesta, error) -> {
                    respuesta.setStatus(403);
                    respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    respuesta.getWriter().write("{\"status\":403,\"title\":\"No tienes permiso para esta operación\"}");
                }))
                .build();
    }
}
