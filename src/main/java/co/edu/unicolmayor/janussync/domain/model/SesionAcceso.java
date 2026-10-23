package co.edu.unicolmayor.janussync.domain.model;

import java.time.Instant;
import java.util.UUID;

public record SesionAcceso(UUID id, UUID usuarioId, Instant venceEn) {
}
