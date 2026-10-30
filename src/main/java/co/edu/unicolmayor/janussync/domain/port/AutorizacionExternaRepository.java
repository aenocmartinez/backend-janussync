package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
import java.util.Optional;
import java.util.function.Supplier;
public interface AutorizacionExternaRepository {
    void guardarIntento(String hashState, String hashNavegador, IntentoAutorizacion intento);
    Optional<IntentoAutorizacion> consumirIntento(String hashState, String hashNavegador);
    void resultadoIntento(String hashState, DiagnosticoConexion resultado);
    Optional<DiagnosticoConexion> ultimoResultado(java.util.UUID usuario);
    Optional<ConexionAutorizada> consultar();
    void guardar(ConexionAutorizada conexion);
    <T> T exclusivo(Supplier<T> operacion);
}
