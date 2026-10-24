package co.edu.unicolmayor.janussync.domain.model;
public record ResultadoConexion(DiagnosticoConexion codigo) {
    public ResultadoConexion { java.util.Objects.requireNonNull(codigo); }
    public String estado() {
        return switch (codigo) {
            case SIMULADO -> "SIMULADO";
            case OK -> "DISPONIBLE";
            case SIN_CONFIGURAR, CONFIGURACION_INVALIDA -> "SIN_CONFIGURAR";
            default -> "NO_DISPONIBLE";
        };
    }
}
