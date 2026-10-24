package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.domain.port.*;
import co.edu.unicolmayor.janussync.domain.valueobject.TiemposConexion;
import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.time.Duration;
@Configuration(proxyBeanMethods=false)
public class ProgramasConfiguration {
    @Bean Duration vigenciaCatalogoProgramas(Environment e) {
        var valor=Duration.parse(e.getProperty("JANUS_PROGRAMAS_MAX_AGE","PT48H"));
        if(valor.isNegative()||valor.isZero())throw new IllegalArgumentException("Configura una vigencia positiva del catálogo.");
        return valor;
    }
    @Bean ConsultarProgramasUseCase consultarProgramas(ProgramaAcademicoRepository programas){return new ConsultarProgramasUseCase(programas);}
    @Bean @Profile("jwt") ActualizarCatalogoProgramasUseCase actualizarCatalogoProgramas(CatalogoProgramasRepository catalogo,FuenteProgramasAcademicos fuente,GestionarUsuariosUseCase autorizacion,Environment e) {
        return new ActualizarCatalogoProgramasUseCase(catalogo,fuente,autorizacion,Duration.parse(e.getProperty("JANUS_PROGRAMAS_UPDATE_COOLDOWN","PT1M")));
    }
    @Bean FuenteProgramasAcademicos fuenteProgramasAcademicos(Environment e,TiemposConexion t) {
        var lectura=new TiemposConexion(Duration.parse(e.getProperty("ACADEMUSOFT_PROGRAMAS_CONNECTION_TIMEOUT","PT5S")),
            Duration.parse(e.getProperty("ACADEMUSOFT_PROGRAMAS_QUERY_TIMEOUT","PT10S")),t.pausa(),t.desactualizadaTrasMinutos());
        return new ProgramaAcademicoOracleRepository(new ConexionOracle(e.getProperty("ACADEMUSOFT_MECANISMO","ORACLE"),
            e.getProperty("ACADEMUSOFT_ORACLE_URL",""),e.getProperty("ACADEMUSOFT_ORACLE_USERNAME",""),
            e.getProperty("ACADEMUSOFT_ORACLE_PASSWORD",""),lectura),lectura);
    }
}
