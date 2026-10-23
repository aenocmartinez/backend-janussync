package co.edu.unicolmayor.janussync.infrastructure.adapter.correo;
import co.edu.unicolmayor.janussync.domain.port.CorreoPort;
import co.edu.unicolmayor.janussync.domain.valueobject.CorreoElectronico;
import java.time.Instant;
import java.net.SocketTimeoutException;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
public class CorreoSmtpAdapter implements CorreoPort {
 private final JavaMailSender mail;private final String remitente,nombre;private final boolean habilitado;
 public CorreoSmtpAdapter(JavaMailSender mail,Environment e){
 this.mail=mail;habilitado=e.getProperty("janussync.correo.habilitado",Boolean.class,false);
 remitente=e.getProperty("janussync.correo.remitente","from@example.com");nombre=e.getProperty("janussync.correo.nombre-remitente","JanusSync");
 if(habilitado){

 if(e.getProperty("spring.mail.username","").isBlank() || e.getProperty("spring.mail.password","").isBlank())throw new IllegalStateException("Completa MAIL_USERNAME y MAIL_PASSWORD para habilitar SMTP.");
 if(e.getProperty("spring.mail.host","").isBlank() || e.getProperty("spring.mail.port",Integer.class,0)<1 || nombre.contains("\r")||nombre.contains("\n"))throw new IllegalStateException("La configuración de correo no es válida.");
 new CorreoElectronico(remitente);
 if((!e.getProperty("spring.mail.properties.mail.smtp.auth",Boolean.class,true)||!e.getProperty("spring.mail.properties.mail.smtp.starttls.enable",Boolean.class,true)||!e.getProperty("spring.mail.properties.mail.smtp.starttls.required",Boolean.class,true)||e.getProperty("spring.mail.properties.mail.smtp.ssl.enable",Boolean.class,false)))throw new IllegalStateException("SMTP requiere autenticación y STARTTLS obligatorio, sin SSL implícito.");
 for(String clave:new String[]{"connectiontimeout","timeout","writetimeout"})if(e.getProperty("spring.mail.properties.mail.smtp."+clave,Integer.class,0)<=0)throw new IllegalStateException("Configura tiempos de espera SMTP positivos.");
 }
 }
 public Resultado enviar(String destinatario,String nombre,String proposito,String enlace,Instant vence){
 if(!habilitado)return Resultado.FALLIDO;
 try{var contenido=PlantillasCorreo.acceso(nombre,proposito,enlace,vence);var mensaje=mail.createMimeMessage();var helper=new MimeMessageHelper(mensaje,true,"UTF-8");helper.setFrom(remitente,this.nombre);helper.setTo(destinatario);helper.setSubject(contenido.asunto());helper.setText(contenido.texto(),contenido.html());mail.send(mensaje);return Resultado.ENVIADO;}
 catch(Exception e){
 // Nunca imprimir la excepción: puede contener destinatarios, cuerpo o credenciales.
 for(Throwable causa=e;causa!=null;causa=causa.getCause())if(causa instanceof SocketTimeoutException)return Resultado.INCIERTO;
 if(e instanceof MailAuthenticationException || e instanceof MailParseException || e instanceof MailPreparationException)return Resultado.FALLIDO;
 // Un rechazo SMTP explícito es definitivo; otros fallos de transporte son ambiguos.
 if(e instanceof MailSendException fallo && fallo.getFailedMessages().values().stream().anyMatch(x->x.getClass().getSimpleName().equals("SMTPSendFailedException") || x.getClass().getSimpleName().equals("SMTPAddressFailedException")))return Resultado.FALLIDO;
 return Resultado.INCIERTO;
 }
 }
}
