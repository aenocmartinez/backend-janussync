package co.edu.unicolmayor.janussync.domain.valueobject;

import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Correo normalizado para JanusSync. No comprueba existencia, dominio
 * institucional ni unicidad.
 */
public record CorreoElectronico(String valor) {
    private static final Pattern PARTE_LOCAL = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*");
    private static final Pattern DOMINIO = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");

    public CorreoElectronico {
        if (valor == null)
            throw invalido();
        valor = valor.strip().toLowerCase(Locale.ROOT);
        int separador = valor.indexOf('@');
        if (separador <= 0 || separador != valor.lastIndexOf('@'))
            throw invalido();
        String local = valor.substring(0, separador);
        String dominio;
        try {
            dominio = IDN.toASCII(valor.substring(separador + 1), IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException error) {
            throw invalido();
        }
        valor = local + "@" + dominio;
        if (valor.length() > 254 || local.length() > 64
                || !PARTE_LOCAL.matcher(local).matches() || !DOMINIO.matcher(dominio).matches()) {
            throw invalido();
        }
    }

    private static DatoInvalidoException invalido() {
        return new DatoInvalidoException("El correo electrónico no tiene un formato válido.");
    }
}
