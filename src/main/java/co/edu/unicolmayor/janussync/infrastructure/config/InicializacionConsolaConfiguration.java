package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.application.usecase.InicializarAdministradorUseCase;
import co.edu.unicolmayor.janussync.domain.model.AdministradorInicial;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import java.util.Arrays;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@Profile("jwt")
@ConditionalOnProperty(name="janus.inicializar-admin",havingValue="true")
public class InicializacionConsolaConfiguration {
    @Bean ApplicationRunner inicializacionConsola(InicializarAdministradorUseCase inicializar,ConfigurableApplicationContext contexto) {
        return args->{
            var consola=System.console();
            if (consola==null) throw new IllegalStateException("La inicialización requiere una consola interactiva (TTY).");
            char[] contrasena=null,confirmacion=null;
            int salida=0;
            try {
                var datos=new AdministradorInicial(new NombrePersona(consola.readLine("Nombres: ")),
                    new NombrePersona(consola.readLine("Apellidos: ")),new CorreoElectronico(consola.readLine("Correo: ")));
                contrasena=consola.readPassword("Contraseña (15–128 caracteres): ");
                confirmacion=consola.readPassword("Confirmar contraseña: ");
                if (contrasena==null || confirmacion==null || !Arrays.equals(contrasena,confirmacion))
                    throw new IllegalArgumentException();
                inicializar.ejecutar(datos,new String(contrasena));
                consola.printf("Administrador inicial creado.\n");
            } catch (RuntimeException e) { consola.printf("No se completó la inicialización. Comprueba los datos y si ya fue realizada.\n");salida=1; }
            finally {
                if (contrasena!=null) Arrays.fill(contrasena,'\0');
                if (confirmacion!=null) Arrays.fill(confirmacion,'\0');
            }
            int codigo=salida;
            System.exit(SpringApplication.exit(contexto,()->codigo));
        };
    }
}
