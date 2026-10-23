package co.edu.unicolmayor.janussync.domain.port;

import co.edu.unicolmayor.janussync.domain.model.*;
import java.time.Instant;
import java.util.*;

public interface AccesoRepository {
    Optional<CuentaAcceso> buscarCuenta(String correo);

    void abrir(SesionAcceso sesion, String hashRefresh, Instant ahora);

    // La rotación es atómica; ante reutilización la revocación debe confirmarse
    // antes de devolver vacío.
    Optional<SesionAcceso> renovar(String hashAnterior, String hashNuevo, Instant ahora);

    void revocar(String hashRefresh, Instant ahora);

    void revocarSesion(UUID sesionId, UUID usuarioId, Instant ahora);

    Optional<Set<String>> permisosActuales(UUID sesionId, UUID usuarioId, Instant ahora);

    boolean consumirIntento(String claveHash, Instant ahora, int limite, long ventanaSegundos);

    void inicializar(AdministradorInicial administrador, String hashContrasena, Instant ahora);
}
