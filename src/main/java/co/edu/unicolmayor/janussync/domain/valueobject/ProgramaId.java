package co.edu.unicolmayor.janussync.domain.valueobject;
import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
/** Identificador institucional opaco: no convierte a número ni UUID ni elimina ceros. */
public record ProgramaId(String valor) {
    public ProgramaId {
        if (valor == null || valor.isBlank() || valor.length() > 80 || !valor.equals(valor.strip()) || valor.chars().anyMatch(Character::isISOControl))
            throw new DatoInvalidoException("El identificador del programa no es válido.");
    }
}
