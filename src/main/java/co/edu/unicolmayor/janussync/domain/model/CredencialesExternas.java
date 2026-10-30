package co.edu.unicolmayor.janussync.domain.model;
import java.time.Instant;
public record CredencialesExternas(String acceso, String renovacion, Instant venceEn, String scopes) {
    @Override public String toString() { return "CredencialesExternas[protegidas]"; }
}
