package co.edu.unicolmayor.janussync.infrastructure.config;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("janussync.auth")
public record PropiedadesAutenticacion(String emisor,String audiencia,String clavePrivada,String clavePublica,
        Duration acceso,Duration refresh,boolean cookieSegura,boolean permitirSinCaptchaLocal) {
    public PropiedadesAutenticacion {
        if (emisor==null || emisor.isBlank() || audiencia==null || audiencia.isBlank()
            || acceso==null || acceso.compareTo(Duration.ofSeconds(30))<0 || acceso.compareTo(Duration.ofMinutes(15))>0
            || refresh==null || refresh.compareTo(acceso)<=0 || refresh.compareTo(Duration.ofDays(30))>0)
            throw new IllegalArgumentException("Configuración de autenticación inválida.");
    }
}
