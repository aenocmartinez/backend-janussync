package co.edu.unicolmayor.janussync.domain.model;

import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;

public record ConsultaUsuarios(String busqueda, EstadoRegistro estado, String rol, int pagina, int tamano) {
    public ConsultaUsuarios {
        if (pagina < 1 || tamano < 1 || tamano > 100) {
            throw new DatoInvalidoException("La página debe ser mayor que cero y el tamaño estar entre 1 y 100.");
        }
        busqueda = busqueda == null ? "" : busqueda.strip();
        rol = rol == null || rol.isBlank() ? null : rol.strip();
        if (busqueda.length() > 254 || (rol != null && rol.length() > 80)) {
            throw new DatoInvalidoException("Los filtros superan la longitud permitida.");
        }
    }

    public long desplazamiento() {
        return ((long) pagina - 1) * tamano;
    }
}
