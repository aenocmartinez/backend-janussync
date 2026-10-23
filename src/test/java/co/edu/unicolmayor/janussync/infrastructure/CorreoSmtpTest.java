package co.edu.unicolmayor.janussync.infrastructure;
import co.edu.unicolmayor.janussync.infrastructure.adapter.correo.CorreoSmtpAdapter;
import co.edu.unicolmayor.janussync.domain.port.CorreoPort;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CorreoSmtpTest {
 MockEnvironment ambiente(){return new MockEnvironment().withProperty("janussync.correo.habilitado","true").withProperty("spring.mail.host","sandbox.smtp.mailtrap.io").withProperty("spring.mail.port","2525").withProperty("spring.mail.username","usuario-prueba").withProperty("spring.mail.password","clave-prueba").withProperty("spring.mail.properties.mail.smtp.connectiontimeout","5000").withProperty("spring.mail.properties.mail.smtp.timeout","5000").withProperty("spring.mail.properties.mail.smtp.writetimeout","5000");}
 @Test void exigeCredencialesYStartTls(){var mail=mock(JavaMailSender.class);assertThatThrownBy(()->new CorreoSmtpAdapter(mail,ambiente().withProperty("spring.mail.password",""))).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("clave-prueba");assertThatThrownBy(()->new CorreoSmtpAdapter(mail,ambiente().withProperty("spring.mail.properties.mail.smtp.starttls.required","false"))).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->new CorreoSmtpAdapter(mail,ambiente().withProperty("spring.mail.properties.mail.smtp.ssl.enable","true"))).isInstanceOf(IllegalStateException.class);}
 @Test void enviaMultipartAlternativoYEscapaHtml() throws Exception {
  var mail=mock(JavaMailSender.class);var mensaje=new MimeMessage(Session.getInstance(new Properties()));when(mail.createMimeMessage()).thenReturn(mensaje);
  var adaptador=new CorreoSmtpAdapter(mail,ambiente());assertThat(adaptador.enviar("destino@pruebas.invalid","<Persona>","INVITACION","https://janus.example/establecer-contrasena?token=opaco",Instant.now().plusSeconds(60))).isEqualTo(CorreoPort.Resultado.ENVIADO);verify(mail).send(mensaje);
  var contenido=new java.io.ByteArrayOutputStream();mensaje.writeTo(contenido);String mime=contenido.toString(java.nio.charset.StandardCharsets.UTF_8);assertThat(mime).contains("multipart/alternative","text/plain","text/html").doesNotContain("spring.mail.password");
 }
 @Test void noAfirmaEntregaAnteTimeout(){var mail=mock(JavaMailSender.class);when(mail.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));doThrow(new org.springframework.mail.MailSendException("Fallo",new java.net.SocketTimeoutException())).when(mail).send(any(MimeMessage.class));assertThat(new CorreoSmtpAdapter(mail,ambiente()).enviar("destino@pruebas.invalid","Persona","INVITACION","https://janus.example/enlace",Instant.now())).isEqualTo(CorreoPort.Resultado.INCIERTO);}
}
