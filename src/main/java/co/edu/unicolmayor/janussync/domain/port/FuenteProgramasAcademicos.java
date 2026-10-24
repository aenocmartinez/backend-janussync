package co.edu.unicolmayor.janussync.domain.port;
import co.edu.unicolmayor.janussync.domain.model.LecturaProgramas;
/** Lectura íntegra de Academusoft, independiente del repositorio de consulta local. */
public interface FuenteProgramasAcademicos { LecturaProgramas leerCatalogo(); }
