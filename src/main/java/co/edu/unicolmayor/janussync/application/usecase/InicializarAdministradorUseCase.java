package co.edu.unicolmayor.janussync.application.usecase;
import co.edu.unicolmayor.janussync.domain.model.AdministradorInicial;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
import java.time.Clock;
public final class InicializarAdministradorUseCase {
    private final AccesoRepository repositorio;
    private final ProtectorCredenciales protector;
    private final Clock reloj;
    public InicializarAdministradorUseCase(AccesoRepository repositorio,ProtectorCredenciales protector,Clock reloj) {
        this.repositorio=repositorio;this.protector=protector;this.reloj=reloj;
    }
    public void ejecutar(AdministradorInicial datos,String contrasena) {
        if (contrasena==null || contrasena.isBlank() || contrasena.codePointCount(0,contrasena.length())<15 || contrasena.length()>128)
            throw new DatoInvalidoException("Usa una contraseña de 15 a 128 caracteres.");
        repositorio.inicializar(datos,protector.proteger(contrasena),reloj.instant());
    }
}
