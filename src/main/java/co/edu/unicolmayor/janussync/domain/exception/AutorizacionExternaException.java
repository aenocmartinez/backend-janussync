package co.edu.unicolmayor.janussync.domain.exception;
import co.edu.unicolmayor.janussync.domain.model.DiagnosticoConexion;
public final class AutorizacionExternaException extends RuntimeException {
    private final DiagnosticoConexion codigo;
    public AutorizacionExternaException(DiagnosticoConexion codigo) {
        super("No se completó la autorización externa."); this.codigo=codigo;
    }
    public DiagnosticoConexion codigo() { return codigo; }
}
