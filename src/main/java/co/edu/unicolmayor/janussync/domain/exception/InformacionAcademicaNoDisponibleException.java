package co.edu.unicolmayor.janussync.domain.exception;
public class InformacionAcademicaNoDisponibleException extends RuntimeException {
 public InformacionAcademicaNoDisponibleException(Throwable causa){super("No fue posible validar la información de Academusoft. Se conserva el trabajo guardado; vuelve a intentarlo cuando la fuente esté disponible.",causa);}
}
