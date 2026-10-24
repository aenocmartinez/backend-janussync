package co.edu.unicolmayor.janussync.application.usecase;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import java.time.Clock;
import java.util.*;
public class ComprobarIntegracionesUseCase {
    private final IntegracionesRepository repositorio;
    private final Map<Plataforma, ComprobarConexionPort> conexiones;
    private final TiemposConexion tiempos;
    private final Clock reloj;
    public ComprobarIntegracionesUseCase(IntegracionesRepository repositorio, List<ComprobarConexionPort> conexiones,
                                        TiemposConexion tiempos, Clock reloj) {
        this.repositorio = repositorio; this.tiempos = tiempos; this.reloj = reloj;
        this.conexiones = new EnumMap<>(Plataforma.class);
        conexiones.forEach(c -> this.conexiones.put(c.plataforma(), c));
        if (this.conexiones.size() != Plataforma.values().length) throw new IllegalArgumentException("Faltan comprobadores de plataformas.");
    }
    public List<EstadoIntegracion> listar() {
        return Arrays.stream(Plataforma.values()).map(this::consultar).toList();
    }
    private EstadoIntegracion consultar(Plataforma p) {
        return repositorio.consultar(p, conexiones.get(p).origen(), conexiones.get(p).configurada(), tiempos.desactualizadaTrasMinutos());
    }
    public EstadoIntegracion comprobar(Plataforma p, String actor) {
        try (var reserva = repositorio.reservar(p, conexiones.get(p).origen(), tiempos.pausa(), tiempos.ejecucion())) {
            long inicio = System.nanoTime();
            ResultadoConexion resultado;
            try { resultado = conexiones.get(p).comprobar(); }
            catch (RuntimeException e) { resultado = new ResultadoConexion(DiagnosticoConexion.ERROR_COMPROBACION); }
            var comprobacion = new ComprobacionConexion(reserva.id(), reserva.inicio(), reloj.instant(),
                    resultado, (System.nanoTime() - inicio) / 1_000_000, conexiones.get(p).alcance());
            repositorio.completar(p, conexiones.get(p).origen(), comprobacion, actor);
        }
        return consultar(p);
    }
}
