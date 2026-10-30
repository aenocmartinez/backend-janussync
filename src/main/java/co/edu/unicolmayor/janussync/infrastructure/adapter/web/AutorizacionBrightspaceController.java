package co.edu.unicolmayor.janussync.infrastructure.adapter.web;
import co.edu.unicolmayor.janussync.application.usecase.AutorizarConexionUseCase;
import co.edu.unicolmayor.janussync.domain.exception.AutorizacionExternaException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@Profile("jwt")
@RestController
public class AutorizacionBrightspaceController {
    private static final String COOKIE="__Secure-JANUS-OAUTH";
    private final AutorizarConexionUseCase caso;
    public AutorizacionBrightspaceController(AutorizarConexionUseCase caso) { this.caso=caso; }
    public record EstadoDTO(String estado, co.edu.unicolmayor.janussync.domain.model.DiagnosticoConexion diagnostico,
        co.edu.unicolmayor.janussync.domain.model.DiagnosticoConexion ultimoIntento) {}
    @GetMapping("/api/v1/integraciones/BRIGHTSPACE/autorizacion")
    public ResponseEntity<EstadoDTO> estado(JwtAuthenticationToken a) {
        var estado=caso.estado();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new EstadoDTO(estado.estado(),estado.diagnostico(),caso.ultimoResultado(UUID.fromString(a.getName()))));
    }
    public record InicioDTO(String url) {}
    @PostMapping("/api/v1/integraciones/BRIGHTSPACE/autorizacion")
    public ResponseEntity<?> iniciar(JwtAuthenticationToken a) {
        try {
            var inicio=caso.iniciar(UUID.fromString(a.getName()),UUID.fromString(a.getToken().getClaimAsString("sid")));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,cookie(inicio.navegador(),300))
                .body(new InicioDTO(inicio.url()));
        } catch (AutorizacionExternaException e) {
            return ResponseEntity.status(e.codigo()==co.edu.unicolmayor.janussync.domain.model.DiagnosticoConexion.PERMISOS_INSUFICIENTES?403:409)
                .cacheControl(CacheControl.noStore()).body(java.util.Map.of("title","No se pudo iniciar la autorización de Brightspace."));
        }
    }
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(HttpServletRequest req) {
        String navegador=null;
        if (req.getCookies()!=null) for(var c:req.getCookies()) if (c.getName().equals(COOKIE)) navegador=c.getValue();
        try {
            // Rechazar parámetros duplicados y mezclas error/código sin reflejarlos.
            var states=req.getParameterValues("state");var codes=req.getParameterValues("code");var errors=req.getParameterValues("error");
            String state=states!=null && states.length==1?states[0]:null;
            String code=codes!=null && codes.length==1?codes[0]:null;
            String error=errors==null?null:"rechazado";
            if (codes!=null && codes.length!=1) error="rechazado";
            caso.completar(state,navegador,code,error);
        } catch (RuntimeException ignored) {
            // Nunca registrar parámetros, excepciones del transporte ni tokens.
            // Estado consultable tras regresar; conexión anterior permanece intacta.
        }
        return ResponseEntity.status(303).header(HttpHeaders.LOCATION,"/inicio?plataforma=brightspace")
            .header(HttpHeaders.SET_COOKIE,cookie("",0)).header("Referrer-Policy","no-referrer")
            .header("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'")
            .cacheControl(CacheControl.noStore()).build();
    }
    private static String cookie(String valor,long segundos) {
        return ResponseCookie.from(COOKIE,valor).httpOnly(true).secure(true).sameSite("Lax")
            .path("/callback").maxAge(segundos).build().toString();
    }
}
