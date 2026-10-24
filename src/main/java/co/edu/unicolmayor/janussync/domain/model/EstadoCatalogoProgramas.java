package co.edu.unicolmayor.janussync.domain.model;
import java.time.*;
public record EstadoCatalogoProgramas(Instant ultimaActualizacion,Instant ultimoIntento,String resultado,int total,boolean actualizando) {
    public boolean utilizable(Instant ahora,Duration vigencia) {
        return ultimaActualizacion!=null && ahora.isBefore(ultimaActualizacion.plus(vigencia));
    }
}
