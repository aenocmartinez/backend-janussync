package co.edu.unicolmayor.janussync.domain.exception;
public final class AcademusoftNoDisponibleException extends RuntimeException {
    public AcademusoftNoDisponibleException(Throwable causa) { this();initCause(causa); }
    public AcademusoftNoDisponibleException() { super("No fue posible consultar los programas de Academusoft. Se conservan las asignaciones existentes; intenta nuevamente más tarde."); }
}
