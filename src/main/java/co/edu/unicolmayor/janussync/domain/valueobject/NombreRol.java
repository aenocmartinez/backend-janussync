package co.edu.unicolmayor.janussync.domain.valueobject;

import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;

public record NombreRol(String valor) {
    public NombreRol {
        if (valor == null) throw invalido();
        valor = valor.strip();
        if (valor.isBlank() || valor.codePointCount(0, valor.length()) > 80
                || valor.codePoints().anyMatch(Character::isISOControl)) throw invalido();
    }

    private static DatoInvalidoException invalido() {
        return new DatoInvalidoException("El nombre del rol debe tener entre 1 y 80 caracteres, sin caracteres de control.");
    }
}
