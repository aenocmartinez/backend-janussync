package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.domain.port.AccesoRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;

@Configuration(proxyBeanMethods=false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@Profile("jwt")
public class SeguridadJwtConfiguration {
    @Bean public SecurityFilterChain seguridadJwt(HttpSecurity http,AccesoRepository repositorio,Clock reloj,
            PropiedadesAutenticacion p,Environment ambiente) throws Exception {
        boolean local=ambiente.matchesProfiles("local");
        if ((!p.cookieSegura() || p.permitirSinCaptchaLocal()) && !local)
            throw new IllegalStateException("Las excepciones de cookie HTTP y CAPTCHA solo están permitidas con el perfil local.");
        var csrf=new CookieCsrfTokenRepository();
        csrf.setCookieName("JANUS-CSRF");csrf.setCookiePath("/");csrf.setHeaderName("X-CSRF-TOKEN");
        csrf.setCookieCustomizer(c->c.httpOnly(true).secure(p.cookieSegura()).sameSite("Strict"));
        return http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(r->r
                .requestMatchers(HttpMethod.GET,"/actuator/health","/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/usuarios","/api/v1/usuarios/*").hasAuthority("USUARIOS_CONSULTAR")
                .requestMatchers(HttpMethod.GET,"/api/v1/roles","/api/v1/roles/permisos").hasAuthority("ROLES_CONSULTAR")
                .requestMatchers(HttpMethod.POST,"/api/v1/roles").hasAuthority("ROLES_CREAR")
                .requestMatchers(HttpMethod.PATCH,"/api/v1/roles/*").hasAnyAuthority("ROLES_EDITAR","ROLES_PERMISOS")
                .requestMatchers(HttpMethod.PUT,"/api/v1/roles/*/estado").hasAuthority("ROLES_ESTADO")

                .requestMatchers(HttpMethod.POST,"/api/v1/auth/recuperacion","/api/v1/auth/enlaces/consulta","/api/v1/auth/enlaces/consumo").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/contrasena").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/usuarios").hasAuthority("USUARIOS_CREAR")
                .requestMatchers(HttpMethod.PUT,"/api/v1/usuarios/*").hasAuthority("USUARIOS_EDITAR")
                .requestMatchers(HttpMethod.PUT,"/api/v1/usuarios/*/roles").hasAuthority("ROLES_ASIGNAR")
                .requestMatchers(HttpMethod.PUT,"/api/v1/usuarios/*/estado").hasAuthority("USUARIOS_ESTADO")
                .requestMatchers(HttpMethod.POST,"/api/v1/usuarios/*/invitacion","/api/v1/usuarios/*/restablecimiento").hasAuthority("USUARIOS_ACCESO")
                .requestMatchers(HttpMethod.GET,"/api/v1/auditoria","/api/v1/auditoria/*").hasAuthority("HISTORIAL_CONSULTAR")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(jwt->{
                try {
                    var permisos=repositorio.permisosActuales(UUID.fromString(jwt.getClaimAsString("sid")),UUID.fromString(jwt.getSubject()),reloj.instant())
                        .orElseThrow(()->new OAuth2AuthenticationException("invalid_token"));
                    return new JwtAuthenticationToken(jwt,permisos.stream().map(SimpleGrantedAuthority::new).toList());
                } catch (IllegalArgumentException e) { throw new OAuth2AuthenticationException("invalid_token"); }
            })))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/problem+json");res.getWriter().write("{\"status\":401,\"title\":\"Acceso no autorizado\"}");})
                .accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/problem+json");res.getWriter().write("{\"status\":403,\"title\":\"Operación no autorizada\"}");}))
            .build();
    }
}
