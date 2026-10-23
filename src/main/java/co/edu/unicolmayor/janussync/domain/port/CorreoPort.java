package co.edu.unicolmayor.janussync.domain.port;
import java.time.Instant;
public interface CorreoPort {
 enum Resultado { ENVIADO,FALLIDO,INCIERTO }
 Resultado enviar(String destinatario,String nombre,String proposito,String enlace,Instant venceEn);
}
