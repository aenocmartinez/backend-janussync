package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
public interface AutorizacionExternaPort {
    boolean configurada();
    String configuracion();
    String urlAutorizacion(String state);
    CredencialesExternas intercambiar(String codigo);
    CredencialesExternas renovar(String refresh);
    ResultadoConexion verificar(String access);
}
