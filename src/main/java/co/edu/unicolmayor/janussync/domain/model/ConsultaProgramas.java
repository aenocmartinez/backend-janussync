package co.edu.unicolmayor.janussync.domain.model;
import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
public record ConsultaProgramas(String busqueda, int pagina, int tamano) {
    public ConsultaProgramas {
        busqueda = busqueda == null ? "" : busqueda.strip();
        if (busqueda.length() > 100 || pagina < 1 || pagina > 10000 || tamano < 1 || tamano > 50)
            throw new DatoInvalidoException("La búsqueda de programas no es válida.");
    }
}
