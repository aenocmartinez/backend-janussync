package co.edu.unicolmayor.janussync.domain.port;

public interface ProtectorCredenciales {
    boolean coincide(String contrasena, String hash);

    String proteger(String contrasena);

    String aleatorio();

    String hash(String valor);
}
