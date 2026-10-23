package co.edu.unicolmayor.janussync.infrastructure.adapter.web;
import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.domain.model.EstadoRegistro;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto.UsuarioDTO;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
@org.springframework.context.annotation.Profile("jwt")
@RestController
@RequestMapping("/api/v1/usuarios")
public class GestionUsuariosController {
 private final GestionarUsuariosUseCase usuarios;private final ContrasenasUseCase contrasenas;
 public GestionUsuariosController(GestionarUsuariosUseCase u,ContrasenasUseCase c){usuarios=u;contrasenas=c;}
 public record Crear(String nombres,String apellidos,String correo,String cargo,EstadoRegistro estado,Set<String> roles,Set<String> programas,boolean enviarInvitacion){}
 public record Datos(String nombres,String apellidos,String correo,String cargo){}
 public record Asignacion(Set<String> roles,Set<String> programas){}
 public record Estado(EstadoRegistro estado){}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public UsuarioDTO crear(Authentication a,@RequestBody Crear d){var u=usuarios.crear(a.getName(),d.nombres(),d.apellidos(),d.correo(),d.cargo(),d.estado(),d.roles(),d.programas(),d.enviarInvitacion());if(d.enviarInvitacion()&&u.estado()==EstadoRegistro.ACTIVO){try{u=contrasenas.enviarAdministrativo(a.getName(),u.id().valor().toString(),"INVITACION");}catch(co.edu.unicolmayor.janussync.domain.exception.ReglaRolException e){ /* Cuenta creada. El detalle permite resolver el acceso con autorización. */ }}return UsuarioDTO.desde(u);}
 @PutMapping("/{id}") public UsuarioDTO editar(Authentication a,@PathVariable String id,@RequestBody Datos d){return UsuarioDTO.desde(usuarios.editar(a.getName(),id,d.nombres(),d.apellidos(),d.correo(),d.cargo()));}
 @PutMapping("/{id}/roles") public UsuarioDTO asignar(Authentication a,@PathVariable String id,@RequestBody Asignacion d){return UsuarioDTO.desde(usuarios.asignar(a.getName(),id,d.roles(),d.programas()));}
 @PutMapping("/{id}/estado") public UsuarioDTO estado(Authentication a,@PathVariable String id,@RequestBody Estado d){return UsuarioDTO.desde(usuarios.estado(a.getName(),id,d.estado()));}
 @PostMapping("/{id}/invitacion") public UsuarioDTO invitar(Authentication a,@PathVariable String id){return UsuarioDTO.desde(contrasenas.enviarAdministrativo(a.getName(),id,"INVITACION"));}
 @PostMapping("/{id}/restablecimiento") public UsuarioDTO restablecer(Authentication a,@PathVariable String id){return UsuarioDTO.desde(contrasenas.enviarAdministrativo(a.getName(),id,"RESTABLECIMIENTO"));}
}
