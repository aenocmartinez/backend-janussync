package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
import java.time.*;
import java.util.*;
public interface CatalogoProgramasRepository extends ProgramaAcademicoRepository {
    EstadoCatalogoProgramas estado();
    /** Vacío significa que otra instancia ya consumió la ejecución programada. */
    Optional<Reserva> reservar(String actor,Duration pausa,Instant siguienteProgramada);
    void publicar(Reserva reserva,List<ProgramaAcademico> programas,String actor,Runnable reautorizar);
    void fallar(Reserva reserva,String actor,String resultado);
    interface Reserva extends AutoCloseable { String id(); void close(); }
}
