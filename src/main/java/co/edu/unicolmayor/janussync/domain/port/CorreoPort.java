package co.edu.unicolmayor.janussync.domain.port;

import java.time.Instant;

public interface CorreoPort {
    enum Resultado {
        ENVIADO, FALLIDO, INCIERTO
    }

    default Resultado avisarSolicitud(String destinatario,long numero,String programa,String accion,String enlace) {
        return enviar(destinatario,"Solicitud #"+numero+" · "+programa,"SOLICITUD_"+accion,enlace,Instant.EPOCH);
    }

    Resultado enviar(String destinatario, String nombre, String proposito, String enlace, Instant venceEn);
}
