package co.edu.unicolmayor.janussync.infrastructure.adapter.web;
import co.edu.unicolmayor.janussync.application.usecase.ConsultarProgramasUseCase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto.PaginaDTO;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@org.springframework.context.annotation.Profile("jwt")
@RestController
@RequestMapping("/api/v1/programas")
public class ProgramasController {
 private final ConsultarProgramasUseCase programas;
 private final co.edu.unicolmayor.janussync.domain.port.CatalogoProgramasRepository catalogo;
 private final co.edu.unicolmayor.janussync.application.usecase.ActualizarCatalogoProgramasUseCase actualizar;
 private final java.time.Clock reloj;private final java.time.Duration vigencia;
 public ProgramasController(ConsultarProgramasUseCase programas,co.edu.unicolmayor.janussync.domain.port.CatalogoProgramasRepository catalogo,co.edu.unicolmayor.janussync.application.usecase.ActualizarCatalogoProgramasUseCase actualizar,java.time.Clock reloj,java.time.Duration vigenciaCatalogoProgramas ){this.programas=programas;this.catalogo=catalogo;this.actualizar=actualizar;this.reloj=reloj;this.vigencia=vigenciaCatalogoProgramas;}
 public record ProgramaDTO(String id,String codigo,String nombre,boolean activo,String modalidad,String metodologia,String jornada,String estadoOficial,boolean presenteEnFuente){
  static ProgramaDTO desde(ProgramaAcademico p){return new ProgramaDTO(p.id().valor(),p.codigo(),p.nombre(),p.activo(),p.modalidad(),p.metodologia(),p.jornada(),p.estadoOficial(),p.presenteEnFuente());}
 }
 public record EstadoDTO(java.time.Instant lastUpdatedAt,java.time.Instant lastAttemptAt,String lastResult,int total,boolean updating,boolean usable,long maxAgeSeconds){}
 private EstadoDTO estado(EstadoCatalogoProgramas e){return new EstadoDTO(e.ultimaActualizacion(),e.ultimoIntento(),e.resultado(),e.total(),e.actualizando(),e.utilizable(reloj.instant(),vigencia),vigencia.toSeconds());}
 @GetMapping("/estado") public EstadoDTO estado(){return estado(catalogo.estado());}
 @PostMapping("/actualizacion") public EstadoDTO actualizar(org.springframework.security.core.Authentication actor){
  return estado(actualizar.manual(actor.getName()));
 }
 @GetMapping public PaginaDTO<ProgramaDTO> buscar(@RequestParam(defaultValue="")String busqueda,@RequestParam(defaultValue="1")int pagina,@RequestParam(defaultValue="20")int tamano){return PaginaDTO.desde(programas.buscar(new ConsultaProgramas(busqueda,pagina,tamano)),ProgramaDTO::desde);}
 @GetMapping("/resolver") public List<ProgramaDTO> resolver(@RequestParam List<String> ids){return programas.resolver(ids).stream().map(ProgramaDTO::desde).toList();}
}
