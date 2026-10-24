package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
import java.time.*;
public interface IntegracionesRepository {
    EstadoIntegracion consultar(Plataforma plataforma, OrigenComprobacion origen, boolean configurada, int vigenciaMinutos);
    Reserva reservar(Plataforma plataforma, OrigenComprobacion origen, Duration pausa, Duration maximo);
    void completar(Plataforma plataforma, OrigenComprobacion origen, ComprobacionConexion comprobacion, String actor);
    default EstadoIntegracion consultar(Plataforma plataforma, boolean configurada, int vigenciaMinutos) {
        return consultar(plataforma, OrigenComprobacion.REAL, configurada, vigenciaMinutos);
    }
    default Reserva reservar(Plataforma plataforma, Duration pausa, Duration maximo) {
        return reservar(plataforma, OrigenComprobacion.REAL, pausa, maximo);
    }
    default void completar(Plataforma plataforma, ComprobacionConexion comprobacion, String actor) {
        completar(plataforma, OrigenComprobacion.REAL, comprobacion, actor);
    }
    interface Reserva extends AutoCloseable {
        String id();
        Instant inicio();
        void close();
    }
}
