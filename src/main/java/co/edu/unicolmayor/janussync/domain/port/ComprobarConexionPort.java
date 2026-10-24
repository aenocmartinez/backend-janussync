package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
public interface ComprobarConexionPort {
    Plataforma plataforma();
    boolean configurada();
    default OrigenComprobacion origen() { return OrigenComprobacion.REAL; }
    default String alcance() { return plataforma().alcance(); }
    ResultadoConexion comprobar();
}
