package co.edu.unicolmayor.janussync.infrastructure.adapter.web;

import co.edu.unicolmayor.janussync.application.usecase.ComprobarIntegracionesUseCase;
import co.edu.unicolmayor.janussync.domain.model.*;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Profile("jwt")
@RestController
@RequestMapping("/api/v1/integraciones")
public class IntegracionesController {
    private final ComprobarIntegracionesUseCase caso;
    public IntegracionesController(ComprobarIntegracionesUseCase caso) { this.caso=caso; }
    public record ComprobacionDTO(String id,String trigger,String requestedByName,Instant startedAt,Instant finishedAt,
                                  String result,DiagnosticoConexion code,long durationMs,String incidentId,String scope,boolean simulated) {}
    public record EnCursoDTO(Instant startedAt,String trigger,String requestedByName) {}
    public record EstadoDTO(Plataforma id,String name,boolean configured,ComprobacionDTO lastCheck,EnCursoDTO checking,
                            Instant failingSince,int checkIntervalMinutes,int staleAfterMinutes,boolean simulated) {
        static EstadoDTO desde(EstadoIntegracion e) {
            var c=e.ultima();
            return new EstadoDTO(e.plataforma(),e.plataforma().nombre(),e.configurada(),c==null?null:
                new ComprobacionDTO(c.id(),"MANUAL",null,c.inicio(),c.fin(),c.resultado().estado(),c.resultado().codigo(),c.duracionMs(),
                    c.resultado().codigo()==DiagnosticoConexion.OK || e.origen()==OrigenComprobacion.SIMULADO?null:c.id(),c.alcance(),e.origen()==OrigenComprobacion.SIMULADO),
                e.comprobandoDesde()==null?null:new EnCursoDTO(e.comprobandoDesde(),"MANUAL",null),
                e.fallandoDesde(),0,e.desactualizadaTrasMinutos(),e.origen()==OrigenComprobacion.SIMULADO);
        }
    }
    @GetMapping public List<EstadoDTO> listar() { return caso.listar().stream().map(EstadoDTO::desde).toList(); }
    @PostMapping("/{plataforma}/comprobaciones")
    public EstadoDTO comprobar(@PathVariable Plataforma plataforma,Authentication a) {
        return EstadoDTO.desde(caso.comprobar(plataforma,a.getName()));
    }
}
