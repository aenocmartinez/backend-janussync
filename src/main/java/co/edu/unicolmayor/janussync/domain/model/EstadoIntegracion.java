package co.edu.unicolmayor.janussync.domain.model;
import java.time.Instant;
public record EstadoIntegracion(Plataforma plataforma, boolean configurada, ComprobacionConexion ultima,
                               Instant comprobandoDesde, Instant fallandoDesde, int desactualizadaTrasMinutos, OrigenComprobacion origen) {
    public EstadoIntegracion(Plataforma plataforma, boolean configurada, ComprobacionConexion ultima,
                             Instant comprobandoDesde, Instant fallandoDesde, int desactualizadaTrasMinutos) {
        this(plataforma, configurada, ultima, comprobandoDesde, fallandoDesde, desactualizadaTrasMinutos, OrigenComprobacion.REAL);
    }
}
