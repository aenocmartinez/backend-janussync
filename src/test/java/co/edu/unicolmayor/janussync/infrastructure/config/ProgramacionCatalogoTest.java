package co.edu.unicolmayor.janussync.infrastructure.config;
import co.edu.unicolmayor.janussync.application.usecase.ActualizarCatalogoProgramasUseCase;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProgramacionCatalogoTest {
 @Test void programaDosAmBogotaSieteUtcYPermiteDeshabilitar(){
  var env=new MockEnvironment();var clock=Clock.fixed(Instant.parse("2026-10-24T06:59:00Z"),ZoneOffset.UTC);
  var caso=mock(ActualizarCatalogoProgramasUseCase.class);
  var programacion=new ProgramacionCatalogoProgramas(caso,clock,env);
  assertThat(programacion.siguiente(clock.instant())).isEqualTo(Instant.parse("2026-10-24T07:00:00Z"));
  assertThat(programacion.siguiente(Instant.parse("2026-10-24T07:00:00Z"))).isEqualTo(Instant.parse("2026-10-25T07:00:00Z"));
  var tareas=new ScheduledTaskRegistrar();programacion.configureTasks(tareas);
  assertThat(tareas.getTriggerTaskList()).hasSize(1);
  tareas.getTriggerTaskList().getFirst().getRunnable().run();
  verify(caso).sistema(Instant.parse("2026-10-24T07:00:00Z"));
  var apagada=new ScheduledTaskRegistrar();new ProgramacionCatalogoProgramas(caso,clock,env.withProperty("JANUS_PROGRAMAS_SYNC_ENABLED","false")).configureTasks(apagada);
  assertThat(apagada.getTriggerTaskList()).isEmpty();
 }
}
