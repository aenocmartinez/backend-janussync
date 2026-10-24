package co.edu.unicolmayor.janussync.domain.valueobject;
import java.time.Duration;
public record TiemposConexion(Duration conexion, Duration ejecucion, Duration pausa, int desactualizadaTrasMinutos) {
    public TiemposConexion {
        if (conexion == null || ejecucion == null || pausa == null || conexion.toMillis() < 100 ||
            conexion.compareTo(ejecucion) > 0 || ejecucion.toMillis() > 60000 || pausa.toSeconds() < 1 ||
            pausa.toSeconds() > 86400 || desactualizadaTrasMinutos < 1 || desactualizadaTrasMinutos > 10080)
            throw new IllegalArgumentException("Configura tiempos de conexión, ejecución, pausa y vigencia válidos.");
    }
}
