package co.edu.unicolmayor.janussync.domain.port;

public interface AuditoriaPort {
    void registrar(String actor, String accion, String tipo, String entidadId, String nombre, String descripcion);
}
