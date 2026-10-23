package co.edu.unicolmayor.janussync.domain.model;
import java.time.Instant;
public record EnlaceContrasena(String id,String usuarioId,String proposito,String correo,Instant venceEn,Instant utilizadoEn,Instant invalidadoEn) {}
