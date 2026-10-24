package co.edu.unicolmayor.janussync.infrastructure.config;

import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.port.IntegracionesRepository;
import co.edu.unicolmayor.janussync.infrastructure.adapter.integraciones.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrightspaceModoTest {
    private ApplicationContextRunner contexto(String... perfiles) {
        return new ApplicationContextRunner().withUserConfiguration(IntegracionesConfiguration.class)
            .withBean(IntegracionesRepository.class, () -> mock(IntegracionesRepository.class))
            .withInitializer(c -> c.getEnvironment().setActiveProfiles(perfiles));
    }
    @Test void mockLocalSinCredencialesNoConstruyeClienteHttpYAcademusoftSigueReal() {
        try (var http = mockConstruction(BrightspaceHttpAdapter.class)) {
            contexto("local", "jwt").withPropertyValues("BRIGHTSPACE_MODE=mock").run(c -> {
                assertThat(c).hasNotFailed();
                var brightspace = c.getBean("conexionBrightspace", ConexionConTiempoLimite.class);
                assertThat(brightspace.origen()).isEqualTo(OrigenComprobacion.SIMULADO);
                assertThat(brightspace.configurada()).isTrue();
                var resultado = brightspace.comprobar();
                assertThat(resultado.codigo()).isEqualTo(DiagnosticoConexion.SIMULADO);
                assertThat(resultado.estado()).isEqualTo("SIMULADO");
                assertThat(brightspace.alcance()).contains("no se contactó al proveedor");
                var academusoft = c.getBean("conexionAcademusoft", ConexionConTiempoLimite.class);
                assertThat(academusoft.origen()).isEqualTo(OrigenComprobacion.REAL);
                assertThat(academusoft.configurada()).isFalse();
                assertThat(http.constructed()).isEmpty();
            });
        }
    }
    @Test void realPorDefectoYExplicitoSinFallback() {
        for (String modo : new String[] {"real", ""}) {
            var runner = contexto("local", "jwt");
            if (!modo.isEmpty()) runner = runner.withPropertyValues("BRIGHTSPACE_MODE=" + modo);
            runner.run(c -> {
                assertThat(c).hasNotFailed();
                var brightspace = c.getBean("conexionBrightspace", ConexionConTiempoLimite.class);
                assertThat(brightspace.origen()).isEqualTo(OrigenComprobacion.REAL);
                assertThat(brightspace.configurada()).isFalse();
                assertThat(brightspace.comprobar().codigo()).isEqualTo(DiagnosticoConexion.SIN_CONFIGURAR);
            });
        }
    }
    @Test void rechazaMockSinLocalOConProduccionYValoresDesconocidos() {
        for (String[] perfiles : new String[][] {{}, {"jwt"}, {"production", "jwt"}, {"local", "prod"}}) {
            contexto(perfiles).withPropertyValues("BRIGHTSPACE_MODE=mock").run(c -> {
                assertThat(c).hasFailed();
                assertThat(c.getStartupFailure()).hasRootCauseMessage("Brightspace mock solo está permitido en el entorno local.");
            });
        }
        for (String modo : new String[] {"demo", "MOCK", ""}) {
            contexto("local", "jwt").withPropertyValues("BRIGHTSPACE_MODE=" + modo).run(c -> {
                assertThat(c).hasFailed();
                assertThat(c.getStartupFailure()).hasRootCauseMessage("BRIGHTSPACE_MODE debe ser real o mock.");
            });
        }
    }
}
