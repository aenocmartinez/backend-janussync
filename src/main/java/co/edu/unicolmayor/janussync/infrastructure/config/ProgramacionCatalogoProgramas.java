package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.application.usecase.ActualizarCatalogoProgramasUseCase;
import java.time.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.*;
import org.slf4j.LoggerFactory;
@Configuration(proxyBeanMethods=false)
@EnableScheduling
@Profile("jwt")
public class ProgramacionCatalogoProgramas implements SchedulingConfigurer {
    private final ActualizarCatalogoProgramasUseCase caso;
    private final Clock reloj;
    private final boolean habilitada;
    private final String expresion;
    private final ZoneId zona;
    public ProgramacionCatalogoProgramas(ActualizarCatalogoProgramasUseCase caso,Clock reloj,Environment e) {
        this.caso=caso;this.reloj=reloj;
        habilitada=e.getProperty("JANUS_PROGRAMAS_SYNC_ENABLED",Boolean.class,true);
        expresion=e.getProperty("JANUS_PROGRAMAS_SYNC_CRON","0 0 2 * * *");
        zona=ZoneId.of(e.getProperty("JANUS_PROGRAMAS_SYNC_ZONE","America/Bogota"));
        CronExpression.parse(expresion);
    }
    public Instant siguiente(Instant ahora) {
        var fecha=CronExpression.parse(expresion).next(ahora.atZone(zona));
        if(fecha==null)throw new IllegalStateException("La programación no tiene próxima ejecución.");
        return fecha.toInstant();
    }
    public void configureTasks(ScheduledTaskRegistrar tareas) {
        if(!habilitada)return;
        tareas.addTriggerTask(()->{
            try {caso.sistema(siguiente(reloj.instant()));}
            catch(RuntimeException e){LoggerFactory.getLogger(getClass()).warn("No se completó la actualización programada de programas; el catálogo anterior se conserva.");}
        },new CronTrigger(expresion,zona));
    }
}
