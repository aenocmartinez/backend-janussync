package co.edu.unicolmayor.janussync.domain.valueobject;

import co.edu.unicolmayor.janussync.domain.exception.ReglaRolException;

public record DescripcionRol(String valor) {
    public DescripcionRol {
        if (valor == null)
            valor = "";
        valor = valor.strip();
        if (valor.codePointCount(0, valor.length()) > 500
                || valor.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
            throw new ReglaRolException("VALIDATION", "description", "La descripción admite hasta 500 caracteres.");
    }
}
