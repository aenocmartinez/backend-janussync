package co.edu.unicolmayor.janussync.domain.exception;

public class UsuarioNoEncontradoException extends RuntimeException {
    public UsuarioNoEncontradoException() {
        super("No se encontró el usuario solicitado.");
    }
}
