package co.edu.unicolmayor.janussync.application.usecase;

import co.edu.unicolmayor.janussync.domain.exception.AccesoRechazadoException;
import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
import co.edu.unicolmayor.janussync.domain.valueobject.CorreoElectronico;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.*;
import java.time.*;
import java.util.*;

public final class AutenticacionUseCase {
    private final AccesoRepository repositorio;
    private final ProtectorCredenciales protector;
    private final EmisorAcceso emisor;
    private final Clock reloj;
    private final Duration acceso, refresh;
    private final String hashFicticio;
    private final GestionUsuariosRepository transacciones;

    public record Resultado(String accessToken, String refreshToken, Instant accessVenceEn, SesionAcceso sesion) {
        @Override
        public String toString() {
            return "Resultado[protegido]";
        }
    }

    public AutenticacionUseCase(AccesoRepository repositorio, ProtectorCredenciales protector,
            EmisorAcceso emisor, Clock reloj, Duration acceso, Duration refresh, GestionUsuariosRepository transacciones) {
        this.repositorio = repositorio;
        this.transacciones = transacciones;
        this.protector = protector;
        this.emisor = emisor;
        this.reloj = reloj;
        this.acceso = acceso;
        this.refresh = refresh;
        this.hashFicticio = protector.proteger(protector.aleatorio());
    }

    public Resultado ingresar(String correo, String contrasena) {
        if (correo == null || correo.length() > 254 || contrasena == null || contrasena.length() > 1024)
            throw new AccesoRechazadoException();
        String normalizado;
        try {
            normalizado = new CorreoElectronico(correo).valor();
        } catch (DatoInvalidoException error) {
            throw new AccesoRechazadoException();
        }
        return transacciones.enTransaccion(() -> {
        var cuenta = repositorio.buscarCuenta(normalizado);
        boolean coincide = protector.coincide(contrasena,
                cuenta.map(CuentaAcceso::hashContrasena).orElse(hashFicticio));
        if (!coincide || cuenta.isEmpty() || !cuenta.get().activa())
            throw new AccesoRechazadoException();
        Instant ahora = reloj.instant();
        var sesion = new SesionAcceso(UUID.randomUUID(), cuenta.get().usuarioId(), ahora.plus(refresh));
        String token = protector.aleatorio();
        repositorio.abrir(sesion, protector.hash(token), ahora);
        return resultado(sesion, token, ahora);
        });
    }

    public Resultado renovar(String anterior) {
        validarRefresh(anterior);
        String nuevo = protector.aleatorio();
        Instant ahora = reloj.instant();
        var sesion = repositorio.renovar(protector.hash(anterior), protector.hash(nuevo), ahora)
                .orElseThrow(AccesoRechazadoException::new);
        return resultado(sesion, nuevo, ahora);
    }

    public void salir(String token) {
        if (token != null && token.length() <= 128)
            repositorio.revocar(protector.hash(token), reloj.instant());
    }

    public void salir(UUID sesion, UUID usuario) {
        repositorio.revocarSesion(sesion, usuario, reloj.instant());
    }

    private void validarRefresh(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            throw new AccesoRechazadoException();
    }

    private Resultado resultado(SesionAcceso sesion, String token, Instant ahora) {
        Instant vence = ahora.plus(acceso);
        if (vence.isAfter(sesion.venceEn()))
            vence = sesion.venceEn();
        return new Resultado(emisor.emitir(sesion, ahora, vence), token, vence, sesion);
    }
}
