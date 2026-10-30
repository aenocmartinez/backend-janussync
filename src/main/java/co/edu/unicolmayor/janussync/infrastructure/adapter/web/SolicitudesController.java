package co.edu.unicolmayor.janussync.infrastructure.adapter.web;
import co.edu.unicolmayor.janussync.application.usecase.solicitudes.GestionarSolicitudesUseCase;
import co.edu.unicolmayor.janussync.domain.model.solicitudes.*;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto.PaginaDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;import java.time.Instant;
@RestController @Profile("jwt") @RequestMapping("/api/v1/solicitudes")
public class SolicitudesController {
 private final GestionarSolicitudesUseCase caso;
 public SolicitudesController(GestionarSolicitudesUseCase caso){this.caso=caso;}
 public record GrupoDTO(String id,String asignaturaId,String asignatura,String nombre,String sede,String programaId,String periodoId,Integer totalDocentes,Integer totalEstudiantes) {
  static GrupoDTO desde(GrupoAcademico g){return new GrupoDTO(g.id(),g.asignaturaId(),g.asignatura(),g.nombre(),g.sede(),g.programaId(),g.periodoId(),g.tieneCantidades()?g.docentes().size():null,g.tieneCantidades()?g.estudiantes().size():null);}
 }
 public record ContenidoDTO(String programaId,String programa,String periodoId,String periodo,List<GrupoDTO> grupos,String nota,String origen,Instant consultadaEn){static ContenidoDTO desde(ContenidoSolicitud c){return c==null?null:new ContenidoDTO(c.programaId(),c.programa(),c.periodoId(),c.periodo(),c.grupos().stream().map(GrupoDTO::desde).toList(),c.nota(),c.origen(),c.consultadaEn());}}
 public record SolicitudDTO(String id,long numero,String creadorId,String responsableId,EstadoSolicitud estado,long version,ContenidoDTO contenido,Instant creadaEn,Instant actualizadaEn){static SolicitudDTO desde(Solicitud s){return new SolicitudDTO(s.id(),s.numero(),s.creadorId(),s.responsableId(),s.estado(),s.version(),ContenidoDTO.desde(s.contenido()),s.creadaEn(),s.actualizadaEn());}}
 public record SeguimientoDTO(String id,String accion,String actor,String comentario,ContenidoDTO contenido,Instant instante){}
 public record DetalleDTO(SolicitudDTO solicitud,List<SeguimientoDTO> seguimiento,List<Map<String,String>> avisos,boolean puedeEditar,boolean puedeEnviar,boolean puedeDevolver,String responsable){}
 public record OperacionDTO(String operacion,Long version,String comentario){}
 @GetMapping public PaginaDTO<SolicitudDTO> listar(Authentication a,@RequestParam(defaultValue="")String busqueda,@RequestParam(defaultValue="")String estado,@RequestParam(defaultValue="1")int pagina){return PaginaDTO.desde(caso.listar(a.getName(),busqueda,estado,pagina),SolicitudDTO::desde);}
 @GetMapping("/pendientes") public Map<String,Long> pendientes(Authentication a){return Map.of("total",caso.pendientes(a.getName()));}
 @GetMapping("/academicos/programas") public List<OpcionAcademica> programas(Authentication a){return caso.programas(a.getName());}
 @GetMapping("/academicos/periodos") public List<OpcionAcademica> periodos(Authentication a,@RequestParam String programa){return caso.periodos(a.getName(),programa);}
 @GetMapping("/academicos/grupos") public PaginaDTO<GrupoDTO> grupos(Authentication a,@RequestParam String programa,@RequestParam String periodo,@RequestParam(defaultValue="")String busqueda,@RequestParam(defaultValue="1")int pagina){return PaginaDTO.desde(caso.grupos(a.getName(),programa,periodo,busqueda,pagina),GrupoDTO::desde);}
 @GetMapping("/academicos/integrantes") public GrupoAcademico integrantes(Authentication a,@RequestParam String programa,@RequestParam String periodo,@RequestParam String grupo){return caso.integrantesPreparacion(a.getName(),programa,periodo,grupo);}
 @GetMapping("/responsables") public List<OpcionAcademica> responsables(Authentication a,@RequestParam String programa){return caso.responsables(a.getName(),programa);}
 @GetMapping("/{id}") public DetalleDTO obtener(Authentication a,@PathVariable String id){
  var s=caso.obtener(a.getName(),id);var acceso=caso.acceso(a.getName());
  var eventos=caso.seguimiento(a.getName(),id).stream().map(e->new SeguimientoDTO(e.id(),e.accion(),e.actor(),e.comentario(),ContenidoDTO.desde(e.contenido()),e.instante())).toList();
  return new DetalleDTO(SolicitudDTO.desde(s),eventos,caso.avisos(a.getName(),id),acceso.prepara()&&acceso.academico(s.contenido().programaId())&&s.estado()!=EstadoSolicitud.ENVIADA,acceso.envia()&&acceso.academico(s.contenido().programaId())&&s.responsableId().equals(a.getName())&&s.estado()!=EstadoSolicitud.ENVIADA,acceso.atiende()&&s.estado()==EstadoSolicitud.ENVIADA,caso.nombreResponsable(a.getName(),id));
 }
 @GetMapping("/{id}/historial/{evento}/grupos/{grupo}") public GrupoAcademico registro(Authentication a,@PathVariable String id,@PathVariable String evento,@PathVariable String grupo){
  return caso.seguimiento(a.getName(),id).stream().filter(e->e.id().equals(evento)&&e.contenido()!=null).flatMap(e->e.contenido().grupos().stream()).filter(g->g.id().equals(grupo)).findFirst().orElseThrow(()->new co.edu.unicolmayor.janussync.domain.exception.ReglaRolException("NOT_FOUND",null,"No se encontró el grupo registrado."));
 }
 @GetMapping("/{id}/grupos/{grupo}/actual") public GrupoAcademico actual(Authentication a,@PathVariable String id,@PathVariable String grupo){return caso.integrantes(a.getName(),id,grupo);}
 @PostMapping public SolicitudDTO crear(Authentication a,@RequestBody CambioSolicitud c){return SolicitudDTO.desde(caso.guardar(a.getName(),null,c));}
 @PutMapping("/{id}") public SolicitudDTO editar(Authentication a,@PathVariable String id,@RequestBody CambioSolicitud c){return SolicitudDTO.desde(caso.guardar(a.getName(),id,c));}
 @PostMapping("/{id}/envio") public SolicitudDTO enviar(Authentication a,@PathVariable String id,@RequestBody OperacionDTO o){return SolicitudDTO.desde(caso.transicion(a.getName(),id,o.operacion(),o.version(),false,null));}
 @PostMapping("/{id}/devolucion") public SolicitudDTO devolver(Authentication a,@PathVariable String id,@RequestBody OperacionDTO o){return SolicitudDTO.desde(caso.transicion(a.getName(),id,o.operacion(),o.version(),true,o.comentario()));}
}
