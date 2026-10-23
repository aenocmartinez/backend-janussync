package co.edu.unicolmayor.janussync.domain.valueobject;

import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
import java.util.UUID;

public record UsuarioId(UUID valor) {
    public UsuarioId {
        if (valor == null) throw invalido();
    }

    public static UsuarioId desde(String texto) {
        try {
            UUID uuid = UUID.fromString(texto);
            if (!uuid.toString().equalsIgnoreCase(texto)) throw invalido();
            return new UsuarioId(uuid);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw invalido();
        }
    }

    private static DatoInvalidoException invalido() {
        return new DatoInvalidoException("El identificador del usuario debe ser un UUID válido.");
    }
}
