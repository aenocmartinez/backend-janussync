package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.application.usecase.AutorizarConexionUseCase;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones.BrightspaceOAuthAdapter;
import co.edu.unicolmayor.janussync.infrastructure.adapter.persistence.*;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Configuration(proxyBeanMethods=false)
public class AutorizacionExternaConfiguration {
    @Bean CifradoCredencialesExternas cifradoCredenciales(Environment e) {
        return new CifradoCredencialesExternas(e.getProperty("JANUS_OAUTH_KEY_FILE",""));
    }
    @Bean AutorizacionExternaRepository autorizacionesRepository(JdbcTemplate jdbc,PlatformTransactionManager tx,CifradoCredencialesExternas cifrado) {
        return new AutorizacionExternaMySqlRepository(jdbc,new TransactionTemplate(tx),cifrado);
    }
    @Bean AutorizacionExternaPort proveedorAutorizacion(Environment e,TiemposConexion t,CifradoCredencialesExternas cifrado) {
        return new BrightspaceOAuthAdapter(e.getProperty("BRIGHTSPACE_BASE_URL",""),e.getProperty("BRIGHTSPACE_API_VERSION",""),
            e.getProperty("BRIGHTSPACE_OAUTH_CLIENT_ID",""),e.getProperty("BRIGHTSPACE_OAUTH_CLIENT_SECRET",""),
            e.getProperty("BRIGHTSPACE_OAUTH_REDIRECT_URI","https://janussync.test/callback"),
            e.getProperty("BRIGHTSPACE_OAUTH_SCOPES","users:own_profile:read"),cifrado.disponible(),t.conexion(),t.ejecucion(),Clock.systemUTC());
    }
    @Bean AutorizarConexionUseCase autorizarConexion(AutorizacionExternaPort port,AutorizacionExternaRepository repo,AccesoRepository acceso) {
        return new AutorizarConexionUseCase(port,repo,acceso,Clock.systemUTC());
    }
}
