package co.edu.unicolmayor.janussync.domain.port;

import co.edu.unicolmayor.janussync.domain.model.SesionAcceso;
import java.time.Instant;

public interface EmisorAcceso {
    String emitir(SesionAcceso sesion, Instant ahora, Instant venceEn);
}
