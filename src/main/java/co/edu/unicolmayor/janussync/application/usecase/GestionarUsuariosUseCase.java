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
 private final GestionRolesRepository administracion;private final RolRepository roles;private final AuditoriaPort auditoria;private final Clock reloj;private final CatalogoProgramasRepository programasAcademicos;private final java.time.Duration vigenciaCatalogo;
 public GestionarUsuariosUseCase(GestionUsuariosRepository g,UsuarioRepository u,GestionRolesRepository a,RolRepository r,AuditoriaPort audit,Clock reloj,CatalogoProgramasRepository programasAcademicos,java.time.Duration vigenciaCatalogo){this.vigenciaCatalogo=vigenciaCatalogo;this.programasAcademicos=programasAcademicos;gestion=g;usuarios=u;administracion=a;roles=r;auditoria=audit;this.reloj=reloj;}
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
 if(nuevos.stream().anyMatch(Set.of("COORDINADOR","COLABORADOR")::contains) && (programas==null || programas.isEmpty()))
 throw new ReglaRolException("VALIDATION","programIds","Los coordinadores y colaboradores deben estar vinculados al menos a un programa.");
 return Set.copyOf(nuevos);
 }
 private Set<String> validarProgramas(Set<String> solicitados,Set<String> anteriores){
 if(solicitados==null || solicitados.size()>100)throw new ReglaRolException("VALIDATION","programIds","Selecciona hasta 100 programas válidos.");
 Set<ProgramaId> nuevos=new HashSet<>();
 for(String id:solicitados){
  ProgramaId programa;try{programa=new ProgramaId(id);}catch(DatoInvalidoException e){throw new ReglaRolException("VALIDATION","programIds",e.getMessage());}
  if(!anteriores.contains(id))nuevos.add(programa);
 }
 if(!nuevos.isEmpty()){
  if(!programasAcademicos.estado().utilizable(reloj.instant(),vigenciaCatalogo))
   throw new ReglaRolException("CATALOG_UNAVAILABLE","programIds","El catálogo de programas no está disponible o superó su vigencia. Actualízalo antes de agregar nuevas vinculaciones.");
  var encontrados=programasAcademicos.resolver(nuevos);
  if(nuevos.stream().anyMatch(id->encontrados.stream().noneMatch(p->p.id().equals(id)&&p.elegible())))
   throw new ReglaRolException("VALIDATION","programIds","Uno de los programas no existe, está inactivo o está ausente de Academusoft.");
 }
 return Set.copyOf(solicitados);
 }
 private void comprobarVersion(Usuario u,Long version){
 if(version==null)throw new ReglaRolException("VALIDATION",null,"La versión de la asignación es obligatoria.");
 if(u.versionAsignacion()!=version)throw new ReglaRolException("CONFLICT",null,"La asignación cambió mientras editabas. Conserva tus selecciones y vuelve a abrir el usuario para revisar los cambios.");
 }
 public Usuario crear(String actor,String nombres,String apellidos,String correo,String cargo,EstadoRegistro estado,Set<String> nuevos,Set<String> programas){return crear(actor,nombres,apellidos,correo,cargo,estado,nuevos,programas,false);}
 public Usuario crear(String actor,String nombres,String apellidos,String correo,String cargo,EstadoRegistro estado,Set<String> nuevos,Set<String> programas,boolean enviarInvitacion){
 autorizar(actor,"USUARIOS_CREAR");autorizar(actor,"ROLES_ASIGNAR");
 var validados=validarProgramas(programas,Set.of());
 return gestion.enTransaccion(()->{
 autorizar(actor,"USUARIOS_CREAR");autorizar(actor,"ROLES_ASIGNAR");if(enviarInvitacion && estado==EstadoRegistro.ACTIVO)autorizar(actor,"USUARIOS_ACCESO");
 if(estado==null)throw new ReglaRolException("VALIDATION",null,"Selecciona un estado válido.");
 String id=UUID.randomUUID().toString();var d=datos(nombres,apellidos,correo,cargo,id);
 validarProgramas(validados,Set.of());
 gestion.crear(id,d,estado,asignaciones(nuevos,Set.of(),validados));gestion.guardarProgramas(id,validados);proteger();
 auditoria.registrar(actor,"CREACION","USUARIO",id,d.nombres().valor()+" "+d.apellidos().valor(),"Cuenta creada; estado: "+estado+"; roles: "+nuevos.stream().sorted().toList()+"; programas: "+validados.stream().sorted().toList());return obtener(id);
 });}
 public Usuario editar(String actor,String id,String nombres,String apellidos,String correo,String cargo){return gestion.enTransaccion(()->{
 autorizar(actor,"USUARIOS_EDITAR");var anterior=obtener(id);var d=datos(nombres,apellidos,correo,cargo,id);
 gestion.editar(id,d);if(!anterior.correo().equals(d.correo()))gestion.invalidarEnlaces(id,reloj.instant());
 auditoria.registrar(actor,"EDICION","USUARIO",id,d.nombres().valor()+" "+d.apellidos().valor(),"Datos: "+anterior.nombres().valor()+" "+anterior.apellidos().valor()+" → "+d.nombres().valor()+" "+d.apellidos().valor()+"; correo: "+anterior.correo().valor()+" → "+d.correo().valor()+"; cargo: "+anterior.cargo()+" → "+d.cargo().valor());return obtener(id);
 });}
 public Usuario asignar(String actor,String id,Set<String> nuevos,Set<String> programas){
 return asignar(actor,id,nuevos,programas,obtener(id).versionAsignacion());
 }
 public Usuario asignar(String actor,String id,Set<String> nuevos,Set<String> programas,Long version){
 autorizar(actor,"ROLES_ASIGNAR");var inicial=obtener(id);comprobarVersion(inicial,version);
 // Solo catálogo MySQL: la elegibilidad y vigencia se vuelven a comprobar bajo el bloqueo administrativo.
 var validados=validarProgramas(programas,inicial.programas());
 return gestion.enTransaccion(()->{
  autorizar(actor,"ROLES_ASIGNAR");var anterior=obtener(id);comprobarVersion(anterior,version);
  validarProgramas(validados,anterior.programas());
  gestion.asignar(id,asignaciones(nuevos,anterior.roles(),validados));gestion.guardarProgramas(id,validados);proteger();
  auditoria.registrar(actor,"CAMBIO_ROLES","USUARIO",id,anterior.nombres().valor()+" "+anterior.apellidos().valor(),
    "Roles: "+anterior.roles().stream().sorted().toList()+" → "+nuevos.stream().sorted().toList()+
    "; programas: "+anterior.programas().stream().sorted().toList()+" → "+validados.stream().sorted().toList());return obtener(id);
 });
 }
 public Usuario estado(String actor,String id,EstadoRegistro estado){return gestion.enTransaccion(()->{
 autorizar(actor,"USUARIOS_ESTADO");var u=obtener(id);if(estado==null)throw new ReglaRolException("VALIDATION",null,"Selecciona un estado válido.");gestion.estado(id,estado);
 if(estado==EstadoRegistro.INACTIVO){gestion.revocarSesiones(id,reloj.instant());gestion.invalidarEnlaces(id,reloj.instant());}proteger();
 auditoria.registrar(actor,estado==EstadoRegistro.ACTIVO?"ACTIVACION":"DESACTIVACION","USUARIO",id,u.nombres().valor()+" "+u.apellidos().valor(),"Estado: "+u.estado()+" → "+estado);return obtener(id);
 });}
}
