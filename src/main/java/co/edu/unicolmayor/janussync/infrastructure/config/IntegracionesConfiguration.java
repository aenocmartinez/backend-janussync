package co.edu.unicolmayor.janussync.infrastructure.config;

import co.edu.unicolmayor.janussync.application.usecase.ComprobarIntegracionesUseCase;
import co.edu.unicolmayor.janussync.application.usecase.AutorizarConexionUseCase;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones.*;
import java.time.*;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
public class IntegracionesConfiguration {
    @Bean TiemposConexion tiemposConexion(Environment e) {
        return new TiemposConexion(Duration.parse(e.getProperty("JANUS_CONNECTION_TIMEOUT","PT3S")),
            Duration.parse(e.getProperty("JANUS_CHECK_TIMEOUT","PT10S")),Duration.parse(e.getProperty("JANUS_CHECK_COOLDOWN","PT1M")),
            e.getProperty("JANUS_CHECK_STALE_MINUTES",Integer.class,15));
    }
    @Bean(destroyMethod="close") ConexionConTiempoLimite conexionAcademusoft(Environment e,TiemposConexion t) {
        return new ConexionConTiempoLimite(new AcademusoftOracleAdapter(e.getProperty("ACADEMUSOFT_MECANISMO","ORACLE"),
            e.getProperty("ACADEMUSOFT_ORACLE_URL",""),e.getProperty("ACADEMUSOFT_ORACLE_USERNAME",""),
            e.getProperty("ACADEMUSOFT_ORACLE_PASSWORD",""),t),t.ejecucion());
    }
    @Bean(destroyMethod="close") ConexionConTiempoLimite conexionBrightspace(Environment e,TiemposConexion t,AutorizarConexionUseCase oauth) {
        String modo = e.getProperty("BRIGHTSPACE_MODE","real");
        if (!modo.equals("real") && !modo.equals("mock"))
            throw new IllegalArgumentException("BRIGHTSPACE_MODE debe ser real o mock.");
        if (modo.equals("mock")) {
            if (!java.util.Arrays.asList(e.getActiveProfiles()).contains("local") ||
                java.util.Arrays.stream(e.getActiveProfiles()).anyMatch(p -> p.equals("prod") || p.equals("production")))
                throw new IllegalArgumentException("Brightspace mock solo está permitido en el entorno local.");
            return new ConexionConTiempoLimite(new BrightspaceSimuladoAdapter(),t.ejecucion());
        }
        return new ConexionConTiempoLimite(oauth,t.ejecucion());
    }
    @Bean ComprobarIntegracionesUseCase comprobarIntegraciones(IntegracionesRepository r,List<ConexionConTiempoLimite> conexiones,TiemposConexion t) {
        return new ComprobarIntegracionesUseCase(r,new java.util.ArrayList<ComprobarConexionPort>(conexiones),t,Clock.systemUTC());
    }
}
