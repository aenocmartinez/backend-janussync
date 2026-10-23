package co.edu.unicolmayor.janussync.domain.exception;

public class AccesoRechazadoException extends RuntimeException {
    public AccesoRechazadoException() {
        super("No fue posible validar el acceso.");
    }
}
