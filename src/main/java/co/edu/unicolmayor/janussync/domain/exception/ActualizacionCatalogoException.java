package co.edu.unicolmayor.janussync.domain.exception;
public final class ActualizacionCatalogoException extends RuntimeException {
    public ActualizacionCatalogoException(Throwable causa) {this();initCause(causa); }
    public ActualizacionCatalogoException() { super("No fue posible actualizar los programas. Se conserva el último catálogo válido."); }
}
