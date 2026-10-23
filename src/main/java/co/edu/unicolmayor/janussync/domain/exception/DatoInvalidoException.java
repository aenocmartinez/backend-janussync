package co.edu.unicolmayor.janussync.domain.exception;

public class DatoInvalidoException extends IllegalArgumentException {
    public DatoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
