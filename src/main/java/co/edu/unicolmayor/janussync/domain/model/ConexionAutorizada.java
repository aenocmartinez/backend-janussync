package co.edu.unicolmayor.janussync.domain.model;
import java.time.Instant;
public record ConexionAutorizada(CredencialesExternas credenciales, String estado,
        String configuracion, Instant autorizadaEn, DiagnosticoConexion diagnostico) {
    @Override public String toString() { return "ConexionAutorizada[" + estado + "]"; }
}
