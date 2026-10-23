package co.edu.unicolmayor.janussync.domain.exception;

public class ReglaRolException extends RuntimeException {
    private final String codigo, campo;

    public ReglaRolException(String codigo, String campo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
        this.campo = campo;
    }

    public String codigo() {
        return codigo;
    }

    public String campo() {
        return campo;
    }
}
