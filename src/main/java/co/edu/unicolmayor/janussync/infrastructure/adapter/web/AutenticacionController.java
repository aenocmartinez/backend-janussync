package co.edu.unicolmayor.janussync.infrastructure.adapter.web;

import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.UsuarioId;
import co.edu.unicolmayor.janussync.infrastructure.config.PropiedadesAutenticacion;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto.UsuarioDTO;
import jakarta.servlet.http.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("jwt")
@RequestMapping("/api/v1/auth")
public class AutenticacionController {
    private static final String COOKIE = "JANUS-REFRESH";
    private final AutenticacionUseCase auth;
    private final ObtenerUsuarioUseCase usuarios;
    private final AccesoRepository repositorio;
    private final ProtectorCredenciales protector;
    private final PropiedadesAutenticacion config;
    private final Clock reloj;

    public record LoginDTO(String correo, String contrasena, String captchaToken) {
        @Override
        public String toString() {
            return "LoginDTO[protegido]";
        }
    }

    public AutenticacionController(AutenticacionUseCase auth, ObtenerUsuarioUseCase usuarios,
            AccesoRepository repositorio, ProtectorCredenciales protector, PropiedadesAutenticacion config,
            Clock reloj) {
        this.auth = auth;
        this.usuarios = usuarios;
        this.repositorio = repositorio;
        this.protector = protector;
        this.config = config;
        this.reloj = reloj;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("token", token.getToken());
    }

    @PostMapping("/login")
    ResponseEntity<?> ingresar(@RequestBody LoginDTO datos, HttpServletRequest req) {
        if (!permitir("login-ip:" + req.getRemoteAddr(), 50, 900)
                || !permitir("login-cuenta:" + correoNormalizado(datos.correo()), 10, 900))
            return limite(900);
        // Hasta integrar un proveedor, ningún token de demostración habilita acceso
        // real.
        if (!config.permitirSinCaptchaLocal())
            return ResponseEntity.status(503)
                    .body(Map.of("title", "El acceso requiere configurar un proveedor de CAPTCHA."));
        if (datos.captchaToken() != null && !datos.captchaToken().isBlank())
            return ResponseEntity.status(401).body(Map.of("title", "No fue posible validar el acceso."));
        var resultado = auth.ingresar(datos.correo(), datos.contrasena());
        auth.salir(cookie(req));
        return respuesta(resultado);
    }

    @PostMapping("/refresh")
    ResponseEntity<?> renovar(HttpServletRequest req) {
        if (!permitir("refresh:" + req.getRemoteAddr(), 60, 60))
            return limite(60);
        return respuesta(auth.renovar(cookie(req)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> salir(HttpServletRequest req, Authentication actual) {
        if (actual instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
            auth.salir(UUID.fromString(jwt.getToken().getClaimAsString("sid")), UUID.fromString(jwt.getName()));
        }
        auth.salir(cookie(req));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookieSalida("", Duration.ZERO))
                .header("Cache-Control", "no-store").build();
    }

    @GetMapping("/me")
    Map<String, Object> actual(Authentication autenticacion, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("usuario", UsuarioDTO.desde(usuarios.ejecutar(UsuarioId.desde(autenticacion.getName()))),
                "permisos", autenticacion.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }

    private ResponseEntity<?> respuesta(AutenticacionUseCase.Resultado resultado) {
        var permisos = repositorio
                .permisosActuales(resultado.sesion().id(), resultado.sesion().usuarioId(), reloj.instant())
                .orElse(Set.of());
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header(HttpHeaders.SET_COOKIE,
                        cookieSalida(resultado.refreshToken(),
                                Duration.between(reloj.instant(), resultado.sesion().venceEn())))
                .body(Map.of("accessToken", resultado.accessToken(), "venceEn", resultado.accessVenceEn(),
                        "usuario", UsuarioDTO.desde(usuarios.ejecutar(new UsuarioId(resultado.sesion().usuarioId()))),
                        "permisos", permisos));
    }

    private String cookieSalida(String token, Duration duracion) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(config.cookieSegura()).sameSite("Strict")
                .path("/api/v1/auth").maxAge(duracion.isNegative() ? Duration.ZERO : duracion).build().toString();
    }

    private String cookie(HttpServletRequest req) {
        if (req.getCookies() == null)
            return null;
        return Arrays.stream(req.getCookies()).filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst()
                .orElse(null);
    }

    private String correoNormalizado(String correo) {
        try {
            return new co.edu.unicolmayor.janussync.domain.valueobject.CorreoElectronico(correo).valor();
        } catch (co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException error) {
            return "entrada-invalida";
        }
    }

    private boolean permitir(String clave, int limite, long segundos) {
        return repositorio.consumirIntento(protector.hash(clave), reloj.instant(), limite, segundos);
    }

    private ResponseEntity<?> limite(int segundos) {
        return ResponseEntity.status(429).header("Retry-After", Integer.toString(segundos))
                .body(Map.of("title", "No fue posible validar el acceso. Intenta más tarde."));
    }
}
