package co.edu.unicolmayor.janussync.application.usecase.solicitudes;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.model.solicitudes.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import co.edu.unicolmayor.janussync.domain.exception.*;
import java.util.*;import java.time.*;import java.security.*;import java.nio.charset.StandardCharsets;

public final class GestionarSolicitudesUseCase {
 private final SolicitudesRepository solicitudes;private final GestionRolesRepository administracion;private final UsuarioRepository usuarios;
 private final CatalogoProgramasRepository catalogo;private final AcademicosSolicitudPort academicos;private final AuditoriaPort auditoria;private final CorreoPort correo;private final Clock reloj;private final String url;
 public GestionarSolicitudesUseCase(SolicitudesRepository s,GestionRolesRepository a,UsuarioRepository u,CatalogoProgramasRepository c,AcademicosSolicitudPort f,AuditoriaPort audit,CorreoPort correo,Clock reloj,String url){solicitudes=s;administracion=a;usuarios=u;catalogo=c;academicos=f;auditoria=audit;this.correo=correo;this.reloj=reloj;this.url=url;}
 private Usuario usuario(String id){return usuarios.obtener(UsuarioId.desde(id)).orElseThrow(()->error("FORBIDDEN",null,"No tienes acceso a solicitudes."));}
 public AccesoSolicitudes acceso(String actor){
  var permisos=administracion.permisosDeUsuariosActivos().getOrDefault(actor,Set.of());
  var a=new AccesoSolicitudes(actor,permisos,usuario(actor).programas());
  if(!a.prepara()&&!a.envia()&&!a.atiende())throw error("FORBIDDEN",null,"No tienes acceso a solicitudes.");return a;
 }
 private void preparar(AccesoSolicitudes a,String programa){if(!a.prepara()||!a.programas().contains(programa))throw error("FORBIDDEN",null,"No puedes preparar solicitudes de este programa.");}
 private Solicitud leer(AccesoSolicitudes a,String id){var s=solicitudes.obtener(id).orElseThrow(()->error("NOT_FOUND",null,"No se encontró la solicitud."));if(!a.ve(s))throw error("NOT_FOUND",null,"No se encontró la solicitud.");return s;}
 public Solicitud obtener(String actor,String id){return leer(acceso(actor),id);}
 public String nombreResponsable(String actor,String id){var u=usuario(obtener(actor,id).responsableId());return u.nombres().valor()+" "+u.apellidos().valor();}
 public List<SeguimientoSolicitud> seguimiento(String actor,String id){obtener(actor,id);return solicitudes.seguimiento(id);}
 public List<Map<String,String>> avisos(String actor,String id){obtener(actor,id);return solicitudes.avisos(id);}
 public Pagina<Solicitud> listar(String actor,String q,String estado,int pagina){
  if(q==null||q.length()>100||pagina<1||pagina>100000)throw error("VALIDATION",null,"Consulta de solicitudes no válida.");
  if(!estado.isEmpty())try{EstadoSolicitud.valueOf(estado);}catch(IllegalArgumentException e){throw error("VALIDATION","estado","Estado no válido.");}
  return solicitudes.listar(acceso(actor),q.strip(),estado,pagina);
 }
 public long pendientes(String actor){return solicitudes.pendientes(acceso(actor));}
 public List<OpcionAcademica> programas(String actor){var a=acceso(actor);if(!a.prepara()&&!a.envia())throw error("FORBIDDEN",null,"No puedes consultar programas académicos.");return catalogo.resolver(a.programas().stream().map(ProgramaId::new).collect(java.util.stream.Collectors.toSet())).stream().filter(ProgramaAcademico::elegible).map(p->new OpcionAcademica(p.id().valor(),p.nombre())).toList();}
 private void consultar(String actor,String programa){new ProgramaId(programa);if(!acceso(actor).academico(programa))throw error("FORBIDDEN",null,"No puedes consultar la información académica de este programa.");}
 public List<OpcionAcademica> periodos(String actor,String programa){consultar(actor,programa);return academicos.periodos(programa);}
 public Pagina<GrupoAcademico> grupos(String actor,String programa,String periodo,String q,int pagina){consultar(actor,programa);new ReferenciaAcademicaId(periodo);if(q==null||q.length()>100||pagina<1||pagina>100000)throw error("VALIDATION",null,"Búsqueda no válida.");return academicos.grupos(programa,periodo,q,pagina);}
 public GrupoAcademico integrantes(String actor,String solicitud,String grupo){var s=obtener(actor,solicitud);if(s.contenido().grupos().stream().noneMatch(g->g.id().equals(grupo)))throw error("NOT_FOUND",null,"El grupo no pertenece a la solicitud.");return academicos.grupo(s.contenido().programaId(),s.contenido().periodoId(),grupo,true);}
 public GrupoAcademico integrantesPreparacion(String actor,String programa,String periodo,String grupo){consultar(actor,programa);return academicos.grupo(programa,periodo,grupo,true);}
 private boolean elegible(String id,String programa){return administracion.permisosDeUsuariosActivos().getOrDefault(id,Set.of()).contains("SOLICITUDES_ENVIAR")&&usuario(id).programas().contains(programa);}
 public List<OpcionAcademica> responsables(String actor,String programa){consultar(actor,programa);return administracion.permisosDeUsuariosActivos().entrySet().stream().filter(e->e.getValue().contains("SOLICITUDES_ENVIAR")).map(e->usuario(e.getKey())).filter(u->u.programas().contains(programa)).map(u->new OpcionAcademica(u.id().valor().toString(),u.nombres().valor()+" "+u.apellidos().valor())).sorted(Comparator.comparing(OpcionAcademica::nombre)).toList();}
 private ContenidoSolicitud contenido(CambioSolicitud c,boolean enviar){
  var p=catalogo.resolver(Set.of(new ProgramaId(c.programaId()))).stream().filter(ProgramaAcademico::elegible).findFirst().orElseThrow(()->error("VALIDATION","programaId","El programa no está disponible para nuevas solicitudes."));
  var periodo=academicos.periodos(c.programaId()).stream().filter(x->x.id().equals(c.periodoId())).findFirst().orElseThrow(()->error("VALIDATION","periodoId","El período no corresponde al programa."));
  if(enviar&&c.grupos().isEmpty())throw error("VALIDATION","grupos","Selecciona al menos un grupo válido antes de enviar.");
  var grupos=c.grupos().stream().map(id->academicos.grupo(c.programaId(),c.periodoId(),id,enviar)).toList();
  if(grupos.stream().map(GrupoAcademico::id).distinct().count()!=c.grupos().size()||grupos.stream().anyMatch(g->!c.grupos().contains(g.id())||!g.programaId().equals(c.programaId())||!g.periodoId().equals(c.periodoId())||g.asignatura()==null||g.asignatura().isBlank()||g.nombre()==null||g.nombre().isBlank()))throw error("VALIDATION","grupos","Los grupos no pertenecen al programa y período seleccionados o están incompletos.");
  return new ContenidoSolicitud(c.programaId(),p.nombre(),c.periodoId(),periodo.nombre(),grupos,c.nota(),"ACADEMUSOFT",reloj.instant());
 }
 private static ReglaRolException error(String codigo,String campo,String texto){return new ReglaRolException(codigo,campo,texto);}
 private String huella(String texto){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private void version(Solicitud s,Long v){if(v==null||s.version()!=v)throw error("CONFLICT",null,"La solicitud cambió. Conserva el formulario y revisa la versión actual.");}
 private void responsable(String id,String programa){if(!elegible(id,programa))throw error("VALIDATION","responsableId","Selecciona un coordinador activo con permiso de envío y asignación vigente al programa.");}
 private String repetida(String op,String actor,String accion,String h){UsuarioId.desde(op);return solicitudes.repeticion(op,actor,accion,h).orElse(null);}
 public Solicitud guardar(String actor,String id,CambioSolicitud c){
  preparar(acceso(actor),c.programaId());String accion=id==null?"CREAR":"EDITAR",h=huella(id+"|"+c);
  String repetida=repetida(c.operacion(),actor,accion,h);if(repetida!=null)return obtener(actor,repetida);
  if(id!=null){var s=obtener(actor,id);version(s,c.version());if(s.estado()==EstadoSolicitud.ENVIADA)throw error("CONFLICT",null,"La solicitud enviada no puede editarse hasta ser devuelta.");if(!s.contenido().programaId().equals(c.programaId())||!s.contenido().periodoId().equals(c.periodoId()))throw error("VALIDATION",null,"Una solicitud conserva su programa y período; crea otra para cambiarlos.");}
  var contenido=contenido(c,false); // Oracle siempre fuera de la transacción MySQL.
  return administracion.enTransaccion(()->{
   var a=acceso(actor);preparar(a,c.programaId());String previa=repetida(c.operacion(),actor,accion,h);if(previa!=null)return leer(a,previa);
   var anterior=id==null?null:leer(a,id);if(anterior!=null){version(anterior,c.version());if(anterior.estado()==EstadoSolicitud.ENVIADA)throw error("CONFLICT",null,"La solicitud ya fue enviada.");}
   String encargado=anterior==null&&a.envia()?actor:c.responsableId();responsable(encargado,c.programaId());
   if(anterior!=null&&!encargado.equals(anterior.responsableId()))throw error("VALIDATION","responsableId","El responsable se conserva; no se reasigna silenciosamente.");
   var ahora=reloj.instant();var s=new Solicitud(id==null?UUID.randomUUID().toString():id,anterior==null?0:anterior.numero(),anterior==null?actor:anterior.creadorId(),encargado,anterior==null?EstadoSolicitud.BORRADOR:anterior.estado(),anterior==null?0:anterior.version()+1,contenido,anterior==null?ahora:anterior.creadaEn(),ahora);
   if(anterior==null)solicitudes.crear(s);else solicitudes.guardar(s,anterior.version());
   var persistida=solicitudes.obtener(s.id()).orElseThrow();String evento=UUID.randomUUID().toString();solicitudes.evento(evento,persistida,actor,accion,null,true);solicitudes.comando(c.operacion(),actor,accion,h,s.id());auditar(actor,persistida,accion);return persistida;
  });
 }
 private void auditar(String actor,Solicitud s,String accion){auditoria.registrar(actor,"SOLICITUD","SOLICITUD",s.id(),"Solicitud #"+s.numero(),accion+"; versión "+s.version()+"; estado "+s.estado());}
 private record Resultado(Solicitud solicitud,String evento,Set<String> destinatarios,String accion){}
 public Solicitud transicion(String actor,String id,String operacion,Long version,boolean devolver,String comentario){
  UsuarioId.desde(operacion);var a=acceso(actor);var inicial=leer(a,id);String accion=devolver?"DEVOLVER":"ENVIAR";
  if(devolver?!a.atiende():!a.envia()||!a.academico(inicial.contenido().programaId())||!actor.equals(inicial.responsableId()))throw error("FORBIDDEN",null,"No puedes realizar esta acción sobre la solicitud.");
  String nota=devolver?new ObservacionSolicitud(comentario).valor():"";String h=huella(id+"|"+version+"|"+nota);
  if(repetida(operacion,actor,accion,h)!=null)return obtener(actor,id);
  version(inicial,version);
  if(devolver?inicial.estado()!=EstadoSolicitud.ENVIADA:inicial.estado()==EstadoSolicitud.ENVIADA)throw error("CONFLICT",null,"La acción no corresponde al estado actual.");
  var c=inicial.contenido();var confirmado=devolver?c:contenido(new CambioSolicitud(operacion,version,c.programaId(),c.periodoId(),inicial.responsableId(),c.grupos().stream().map(GrupoAcademico::id).toList(),c.nota()),true);
  Resultado resultado=administracion.enTransaccion(()->{
   var vigente=acceso(actor);var previa=leer(vigente,id);
   if(devolver?!vigente.atiende():!vigente.envia()||!vigente.academico(c.programaId())||!actor.equals(previa.responsableId()))throw error("FORBIDDEN",null,"Tu acceso cambió; vuelve a revisar la solicitud.");
   if(repetida(operacion,actor,accion,h)!=null)return new Resultado(previa,null,Set.of(),accion);
   version(previa,version);if(devolver?previa.estado()!=EstadoSolicitud.ENVIADA:previa.estado()==EstadoSolicitud.ENVIADA)throw error("CONFLICT",null,"El estado cambió.");
   if(!devolver)responsable(previa.responsableId(),c.programaId());
   var s=new Solicitud(id,previa.numero(),previa.creadorId(),previa.responsableId(),devolver?EstadoSolicitud.CON_OBSERVACIONES:EstadoSolicitud.ENVIADA,previa.version()+1,confirmado,previa.creadaEn(),reloj.instant());
   solicitudes.guardar(s,previa.version());String evento=UUID.randomUUID().toString();solicitudes.evento(evento,s,actor,accion,nota,true);solicitudes.comando(operacion,actor,accion,h,id);auditar(actor,s,accion);
   Set<String> destinos=new HashSet<>();
   if(devolver){destinos.add(s.responsableId());if(!s.creadorId().equals(s.responsableId()))destinos.add(s.creadorId());}
   else administracion.permisosDeUsuariosActivos().forEach((u,p)->{if(p.contains("SOLICITUDES_ATENDER"))destinos.add(u);});
   destinos.removeIf(u->!destinatarioVigente(u,s,devolver));
   if(destinos.isEmpty())solicitudes.prepararAviso(evento,id,"");else destinos.forEach(u->solicitudes.prepararAviso(evento,id,u));
   return new Resultado(s,evento,Set.copyOf(destinos),accion);
  });
  // La transición ya confirmó. Ningún fallo de SMTP puede revertirla ni iniciar un reintento.
  for(String destino:resultado.destinatarios()){
   if(!solicitudes.reclamarAviso(resultado.evento(),destino))continue;
   String estado="INCIERTO";
   try {
    if(!destinatarioVigente(destino,resultado.solicitud(),devolver))estado="OMITIDO_SIN_ACCESO";
    else {var u=usuario(destino);var r=correo.avisarSolicitud(u.correo().valor(),resultado.solicitud().numero(),c.programa(),accion,url+"/solicitudes/"+id);estado=r==CorreoPort.Resultado.ENVIADO?"ACEPTADO_SMTP":r==CorreoPort.Resultado.FALLIDO?"FALLIDO":"INCIERTO";}
   }catch(RuntimeException e){estado="INCIERTO";}
   solicitudes.resultadoAviso(resultado.evento(),destino,estado);
  }
  return obtener(actor,id);
 }
 private boolean destinatarioVigente(String id,Solicitud s,boolean devolucion){
  var p=administracion.permisosDeUsuariosActivos().getOrDefault(id,Set.of());
  return devolucion?(p.contains("SOLICITUDES_PREPARAR")||p.contains("SOLICITUDES_ENVIAR"))&&usuario(id).programas().contains(s.contenido().programaId()):p.contains("SOLICITUDES_ATENDER");
 }
}
