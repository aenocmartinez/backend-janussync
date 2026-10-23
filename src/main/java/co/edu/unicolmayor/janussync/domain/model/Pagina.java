package co.edu.unicolmayor.janussync.domain.model;

import java.util.List;

public record Pagina<T>(List<T> elementos, int pagina, int tamano, long total) {
    public Pagina {
        elementos = List.copyOf(elementos);
        if (pagina < 1 || tamano < 1 || total < 0)
            throw new IllegalArgumentException("Paginación inválida.");
    }

    public long totalPaginas() {
        return total / tamano + (total % tamano == 0 ? 0 : 1);
    }
}
