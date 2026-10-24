package co.edu.unicolmayor.janussync.domain.model;
import java.time.Instant;
public record ComprobacionConexion(String id, Instant inicio, Instant fin, ResultadoConexion resultado, long duracionMs, String alcance) {
    public ComprobacionConexion(String id, Instant inicio, Instant fin, ResultadoConexion resultado, long duracionMs) {
        this(id,inicio,fin,resultado,duracionMs,"Consulta de comunicación de solo lectura.");
    }
    public ComprobacionConexion {
        if (id == null || inicio == null || fin == null || resultado == null || duracionMs < 0 || alcance == null || alcance.length() > 250)
            throw new IllegalArgumentException("La comprobación no es válida.");
    }
}
