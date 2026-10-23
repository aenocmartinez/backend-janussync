package co.edu.unicolmayor.janussync.domain.valueobject;

import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;

/** Se utiliza tanto para nombres como para apellidos; admite acentos, guiones y apóstrofos. */
public record NombrePersona(String valor) {
    public NombrePersona {
        if (valor == null) throw invalido();
        valor = valor.strip();
        if (valor.isBlank() || valor.codePointCount(0, valor.length()) > 100
                || valor.codePoints().anyMatch(Character::isISOControl)) throw invalido();
    }

    private static DatoInvalidoException invalido() {
        return new DatoInvalidoException("Los nombres y apellidos deben tener entre 1 y 100 caracteres, sin caracteres de control.");
    }
}
