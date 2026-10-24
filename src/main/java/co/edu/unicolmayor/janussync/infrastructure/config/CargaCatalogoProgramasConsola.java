package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.application.usecase.ActualizarCatalogoProgramasUseCase;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@Profile("jwt")
@ConditionalOnProperty(name="janus.actualizar-programas",havingValue="true")
public class CargaCatalogoProgramasConsola {
    @Bean ApplicationRunner cargaCatalogoProgramas(ActualizarCatalogoProgramasUseCase caso,ConfigurableApplicationContext contexto) {
        return args->{
            int salida=0;
            try {
                var estado=caso.sistema(null);
                System.out.println("Catálogo de programas actualizado: "+estado.total()+" programas; fecha UTC: "+estado.ultimaActualizacion()+".");
            } catch(RuntimeException e){
                System.out.println("No se actualizó el catálogo; se conserva la última versión válida.");
                Throwable causa=e;while(causa.getCause()!=null)causa=causa.getCause();
                System.out.println("Diagnóstico técnico: "+causa.getClass().getSimpleName()+(causa instanceof java.sql.SQLException sql?"; código "+sql.getErrorCode():""));salida=1;
            }
            int codigo=salida;System.exit(SpringApplication.exit(contexto,()->codigo));
        };
    }
}
