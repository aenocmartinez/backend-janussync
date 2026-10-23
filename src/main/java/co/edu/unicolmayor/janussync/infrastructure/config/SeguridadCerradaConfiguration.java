package co.edu.unicolmayor.janussync.infrastructure.config;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/** Sin autenticación institucional implementada, ningún endpoint administrativo queda público. */
@Configuration(proxyBeanMethods = false)
@Profile("!local")
@EnableWebSecurity
public class SeguridadCerradaConfiguration {
    @Bean
    UserDetailsService sinUsuariosAutomaticos() {
        return nombre -> { throw new UsernameNotFoundException("Acceso no disponible."); };
    }

    @Bean
    SecurityFilterChain seguridadCerrada(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(rutas -> rutas
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .anyRequest().denyAll()).build();
    }
}
