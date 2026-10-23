package co.edu.unicolmayor.janussync.application.usecase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.exception.*;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
public final class GestionarUsuariosUseCase {
 private final GestionUsuariosRepository gestion;private final UsuarioRepository usuarios;
 private final GestionRolesRepository administracion;private final RolRepository roles;private final AuditoriaPort auditoria;private final Clock reloj;
 public GestionarUsuariosUseCase(GestionUsuariosRepository g,UsuarioRepository u,GestionRolesRepository a,RolRepository r,AuditoriaPort audit,Clock reloj){gestion=g;usuarios=u;administracion=a;roles=r;auditoria=audit;this.reloj=reloj;}
 public void autorizar(String actor,String permiso){if(!administracion.permisosDeUsuariosActivos().getOrDefault(actor,Set.of()).contains(permiso))throw new ReglaRolException("FORBIDDEN",null,"No tienes permiso para esta operación.");}
 private void proteger(){var conAcceso=administracion.usuariosConCredenciales();if(administracion.permisosDeUsuariosActivos().entrySet().stream().filter(e->conAcceso.contains(e.getKey())).noneMatch(e->e.getValue().containsAll(GestionarRolesUseCase.ESENCIALES)))throw new ReglaRolException("ADMIN_REQUIRED",null,"Debe conservarse una persona activa con contraseña establecida y los permisos esenciales de administración.");}
 public Usuario obtener(String id){return usuarios.obtener(UsuarioId.desde(id)).orElseThrow(()->new ReglaRolException("NOT_FOUND",null,"No se encontró el usuario."));}
 private <T>T campo(String valor,String campo,Function<String,T> constructor){try{return constructor.apply(valor);}catch(DatoInvalidoException e){throw new ReglaRolException("VALIDATION",campo,e.getMessage());}}
 private DatosUsuario datos(String nombres,String apellidos,String correo,String cargo,String id){
 var d=new DatosUsuario(campo(nombres,"firstName",NombrePersona::new),campo(apellidos,"lastName",NombrePersona::new),campo(correo,"email",CorreoElectronico::new),campo(cargo,"position",Cargo::new));
 if(gestion.existeCorreo(d.correo().valor(),id))throw new ReglaRolException("DUPLICATE_EMAIL","email","Ya existe una cuenta con este correo.");return d;
 }
 private Set<String> asignaciones(Set<String> nuevos,Set<String> anteriores,Set<String> programas){
 if(nuevos==null||nuevos.isEmpty()||nuevos.stream().anyMatch(Objects::isNull))throw new ReglaRolException("VALIDATION","roles","Selecciona al menos un rol válido.");
 var catalogo=roles.listar();
 for(String id:nuevos){var rol=catalogo.stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->new ReglaRolException("VALIDATION","roles","Uno de los roles no existe."));
 if(rol.estado()==EstadoRegistro.INACTIVO && !anteriores.contains(id))throw new ReglaRolException("VALIDATION","roles","Solo se pueden asignar nuevos roles activos.");}
 // No hay puerto/adaptador real de Academusoft: nunca se validan programas inventados.
 if(programas!=null&&!programas.isEmpty() || nuevos.stream().anyMatch(id->Set.of("COORDINADOR","COLABORADOR").contains(id)&&!anteriores.contains(id)))
 throw new ReglaRolException("VALIDATION","programIds","No es posible validar programas: la consulta a Academusoft aún no está disponible.");
 return Set.copyOf(nuevos);
 }
 public Usuario crear(String actor,String nombres,String apellidos,String correo,String cargo,EstadoRegistro estado,Set<String> nuevos,Set<String> programas){return crear(actor,nombres,apellidos,correo,cargo,estado,nuevos,programas,false);}
 public Usuario crear(String actor,String nombres,String apellidos,String correo,String cargo,EstadoRegistro estado,Set<String> nuevos,Set<String> programas,boolean enviarInvitacion){return gestion.enTransaccion(()->{
 autorizar(actor,"USUARIOS_CREAR");autorizar(actor,"ROLES_ASIGNAR");if(enviarInvitacion && estado==EstadoRegistro.ACTIVO)autorizar(actor,"USUARIOS_ACCESO");
 if(estado==null)throw new ReglaRolException("VALIDATION",null,"Selecciona un estado válido.");
 String id=UUID.randomUUID().toString();var d=datos(nombres,apellidos,correo,cargo,id);
 gestion.crear(id,d,estado,asignaciones(nuevos,Set.of(),programas));proteger();
 auditoria.registrar(actor,"CREACION","USUARIO",id,d.nombres().valor()+" "+d.apellidos().valor(),"Cuenta creada; estado: "+estado+"; roles: "+nuevos.stream().sorted().toList());return obtener(id);
 });}
 public Usuario editar(String actor,String id,String nombres,String apellidos,String correo,String cargo){return gestion.enTransaccion(()->{
 autorizar(actor,"USUARIOS_EDITAR");var anterior=obtener(id);var d=datos(nombres,apellidos,correo,cargo,id);
 gestion.editar(id,d);if(!anterior.correo().equals(d.correo()))gestion.invalidarEnlaces(id,reloj.instant());
 auditoria.registrar(actor,"EDICION","USUARIO",id,d.nombres().valor()+" "+d.apellidos().valor(),"Datos: "+anterior.nombres().valor()+" "+anterior.apellidos().valor()+" → "+d.nombres().valor()+" "+d.apellidos().valor()+"; correo: "+anterior.correo().valor()+" → "+d.correo().valor()+"; cargo: "+anterior.cargo()+" → "+d.cargo().valor());return obtener(id);
 });}
 public Usuario asignar(String actor,String id,Set<String> nuevos,Set<String> programas){return gestion.enTransaccion(()->{
 autorizar(actor,"ROLES_ASIGNAR");var anterior=obtener(id);gestion.asignar(id,asignaciones(nuevos,anterior.roles(),programas));proteger();
 auditoria.registrar(actor,"CAMBIO_ROLES","USUARIO",id,anterior.nombres().valor()+" "+anterior.apellidos().valor(),"Roles: "+anterior.roles().stream().sorted().toList()+" → "+nuevos.stream().sorted().toList());return obtener(id);
 });}
 public Usuario estado(String actor,String id,EstadoRegistro estado){return gestion.enTransaccion(()->{
 autorizar(actor,"USUARIOS_ESTADO");var u=obtener(id);if(estado==null)throw new ReglaRolException("VALIDATION",null,"Selecciona un estado válido.");gestion.estado(id,estado);
 if(estado==EstadoRegistro.INACTIVO){gestion.revocarSesiones(id,reloj.instant());gestion.invalidarEnlaces(id,reloj.instant());}proteger();
 auditoria.registrar(actor,estado==EstadoRegistro.ACTIVO?"ACTIVACION":"DESACTIVACION","USUARIO",id,u.nombres().valor()+" "+u.apellidos().valor(),"Estado: "+u.estado()+" → "+estado);return obtener(id);
 });}
}
