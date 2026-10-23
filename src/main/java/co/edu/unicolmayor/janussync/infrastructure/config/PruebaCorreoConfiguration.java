package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.domain.port.CorreoPort;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="janus.probar-correo",havingValue="true")
public class PruebaCorreoConfiguration {
 @Bean ApplicationRunner probarCorreo(CorreoPort correo,Environment e,org.springframework.context.ConfigurableApplicationContext contexto){return args->{
 if(!"sandbox.smtp.mailtrap.io".equals(e.getProperty("spring.mail.host")))throw new IllegalStateException("La prueba solo está permitida con Mailtrap Sandbox.");
 var estado=correo.enviar("diagnostico@pruebas.invalid","JanusSync","PRUEBA","",java.time.Instant.now());
 System.out.println(estado==CorreoPort.Resultado.ENVIADO?"SMTP aceptó el mensaje de prueba. Comprueba Mailtrap Sandbox; no se confirma entrega a un buzón real.":estado==CorreoPort.Resultado.INCIERTO?"No se pudo confirmar la aceptación SMTP. Comprueba el Sandbox antes de repetir.":"El mensaje no fue aceptado. Comprueba que SMTP esté habilitado y sus variables estén configuradas.");
 contexto.close();
 };}
}
