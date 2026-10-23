package co.edu.unicolmayor.janussync.infrastructure.adapter.web.dto;

import co.edu.unicolmayor.janussync.domain.model.Pagina;
import java.util.List;
import java.util.function.Function;

public record PaginaDTO<T>(List<T> elementos, int pagina, int tamano, long total, long totalPaginas) {
    public static <D, T> PaginaDTO<T> desde(Pagina<D> pagina, Function<D, T> convertir) {
        return new PaginaDTO<>(pagina.elementos().stream().map(convertir).toList(), pagina.pagina(), pagina.tamano(),
                pagina.total(), pagina.totalPaginas());
    }
}
