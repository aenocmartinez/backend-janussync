package co.edu.unicolmayor.janussync.domain.model;

import java.time.Instant;

public record CorreoAcceso(String proposito, String estado, Instant instante, Instant venceEn,
        String solicitadoPorNombre, Instant invalidadoEn, Instant utilizadoEn) {
}
