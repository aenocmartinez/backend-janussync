package co.edu.unicolmayor.janussync.domain.model;
import java.time.Instant;
import java.util.UUID;
public record IntentoAutorizacion(UUID usuario, UUID sesion, Instant venceEn, String configuracion) {}
