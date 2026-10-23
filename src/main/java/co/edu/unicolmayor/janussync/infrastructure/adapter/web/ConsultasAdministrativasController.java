package co.edu.unicolmayor.janussync.infrastructure.adapter.web;
import co.edu.unicolmayor.janussync.domain.port.ConsultaAdministrativaRepository;
import co.edu.unicolmayor.janussync.domain.model.EventoAuditoria;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class ConsultasAdministrativasController {
 private final ConsultaAdministrativaRepository repo;public ConsultasAdministrativasController(ConsultaAdministrativaRepository r){repo=r;}
 public record Evento(String id,String actorId,String actorName,String action,String targetType,String targetId,String targetName,Instant occurredAt,String description){static Evento desde(EventoAuditoria e){return new Evento(e.id(),e.actorId(),e.actorNombre(),e.accion(),e.entidadTipo(),e.entidadId(),e.entidadNombre(),e.instante(),e.descripcion());}}
 @GetMapping("/auditoria") public Map<String,Object> consultar(@RequestParam(required=false)String actorId,@RequestParam(required=false)String targetType,@RequestParam(required=false)String targetId,@RequestParam(required=false)String action,@RequestParam(required=false)LocalDate from,@RequestParam(required=false)LocalDate to,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int pageSize){
 var zona=ZoneId.of("America/Bogota");var p=repo.auditoria(actorId,targetType,targetId,action,from==null?null:from.atStartOfDay(zona).toInstant(),to==null?null:to.plusDays(1).atStartOfDay(zona).toInstant(),page,pageSize);
 return Map.of("items",p.elementos().stream().map(Evento::desde).toList(),"total",p.total(),"page",page,"pageSize",pageSize,"totalPages",p.totalPaginas());
 }
 @GetMapping("/auditoria/actores") public Object actores(){return repo.actores();}
 @GetMapping("/auditoria/entidades") public Object entidades(){return repo.entidades();}
 @GetMapping("/usuarios/estadisticas") public Object cantidades(){return repo.cantidadesUsuarios();}
 @GetMapping("/usuarios/incidencias") public Object incidencias(){return repo.incidencias();}
}
