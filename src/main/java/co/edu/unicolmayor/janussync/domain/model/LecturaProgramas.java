package co.edu.unicolmayor.janussync.domain.model;

import java.util.List;

/**
 * Evidencia de una lectura completa y coherente de la fuente, no de una página
 * del catálogo.
 */
public record LecturaProgramas(List<ProgramaAcademico> programas, long totalEsperado, boolean completa) {
    public LecturaProgramas {
        programas = List.copyOf(programas);
    }
}
