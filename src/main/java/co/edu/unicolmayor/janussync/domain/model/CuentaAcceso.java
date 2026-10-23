package co.edu.unicolmayor.janussync.domain.model;
import java.util.UUID;
public record CuentaAcceso(UUID usuarioId, String hashContrasena, boolean activa) {
    @Override public String toString() { return "CuentaAcceso[protegida]"; }
}
