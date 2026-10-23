package co.edu.unicolmayor.janussync.infrastructure.adapter.web;
import co.edu.unicolmayor.janussync.application.usecase.ContrasenasUseCase;
import co.edu.unicolmayor.janussync.domain.port.*;
import java.time.Clock;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@org.springframework.context.annotation.Profile("jwt")
@RestController
@RequestMapping("/api/v1/auth")
public class ContrasenasController {
 private final ContrasenasUseCase casos;private final AccesoRepository limites;private final ProtectorCredenciales protector;private final Clock reloj;
 public ContrasenasController(ContrasenasUseCase c,AccesoRepository l,ProtectorCredenciales p,Clock r){casos=c;limites=l;protector=p;reloj=r;}
 public record Solicitud(String correo){}
 public record Enlace(String token,String proposito,String nueva,String confirmacion){@Override public String toString(){return "Enlace[protegido]";}}
 public record Cambio(String actual,String nueva,String confirmacion){@Override public String toString(){return "Cambio[protegido]";}}
 private void limitar(HttpServletRequest r){if(!limites.consumirIntento(protector.hash("enlaces-ip:"+r.getRemoteAddr()),reloj.instant(),60,60))throw new co.edu.unicolmayor.janussync.domain.exception.ReglaRolException("CONFLICT",null,"Demasiados intentos. Intenta más tarde.");}
 @PostMapping("/recuperacion") @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
 public java.util.Map<String,String> recuperar(@RequestBody Solicitud d,HttpServletRequest r){casos.recuperar(d.correo(),r.getRemoteAddr());return java.util.Map.of("mensaje","Si la cuenta está habilitada para recuperación, recibirá un enlace.");}
 @PostMapping("/enlaces/consulta") public ContrasenasUseCase.Info consultar(@RequestBody Enlace d,HttpServletRequest r,HttpServletResponse response){limitar(r);response.setHeader("Cache-Control","no-store");return casos.inspeccionar(d.token(),d.proposito());}
 @PostMapping("/enlaces/consumo") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void consumir(@RequestBody Enlace d,HttpServletRequest r,HttpServletResponse response){limitar(r);response.setHeader("Cache-Control","no-store");casos.completar(d.token(),d.proposito(),d.nueva(),d.confirmacion());}
 @PostMapping("/contrasena") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 public void cambiar(Authentication a,@RequestBody Cambio d){casos.cambiar(a.getName(),d.actual(),d.nueva(),d.confirmacion());}
}
