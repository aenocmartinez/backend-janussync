package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.valueobject.ProgramaId;
import java.util.*;
/** Catálogo institucional de solo lectura. Una ausencia no equivale a un fallo de integración. */
public interface ProgramaAcademicoRepository {
    Pagina<ProgramaAcademico> buscar(ConsultaProgramas consulta);
    List<ProgramaAcademico> resolver(Set<ProgramaId> ids);
}
