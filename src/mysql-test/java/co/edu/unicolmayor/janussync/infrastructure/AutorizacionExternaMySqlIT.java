package co.edu.unicolmayor.janussync.infrastructure;
import co.edu.unicolmayor.janussync.infrastructure.adapter.persistence.*;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.domain.exception.AutorizacionExternaException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class AutorizacionExternaMySqlIT {
    @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4");
    @TempDir Path dir;
    JdbcTemplate jdbc;AutorizacionExternaMySqlRepository repo,otro;
    @BeforeEach void preparar() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        org.flywaydb.core.Flyway.configure().dataSource(ds).load().migrate();
        jdbc=new JdbcTemplate(ds);jdbc.update("DELETE FROM oauth_intentos");jdbc.update("DELETE FROM oauth_conexion");
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);var path=dir.resolve("key");Files.writeString(path,Base64.getEncoder().encodeToString(key));
        var cifrado=new CifradoCredencialesExternas(path.toString());
        repo=new AutorizacionExternaMySqlRepository(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),cifrado);
        otro=new AutorizacionExternaMySqlRepository(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),cifrado);
    }
    @Test void migracionAditivaYCifradoPersistenteSinTextoPlano() {
        var tokens=new CredencialesExternas("access-secreto","refresh-secreto",Instant.now().plusSeconds(3600),"users:own_profile:read");
        repo.guardar(new ConexionAutorizada(tokens,"AUTORIZADA","f".repeat(64),Instant.now(),DiagnosticoConexion.OK));
        assertThat(otro.consultar().orElseThrow().credenciales().acceso()).isEqualTo(tokens.acceso());
        byte[] guardado=jdbc.queryForObject("SELECT acceso_cifrado FROM oauth_conexion",byte[].class);
        assertThat(new String(guardado,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(tokens.acceso());
    }
    @Test void consumoUnaSolaVezEntreInstanciasYCookieVinculada() throws Exception {
        var i=new IntentoAutorizacion(UUID.randomUUID(),UUID.randomUUID(),Instant.now().plusSeconds(300),"f".repeat(64));
        repo.guardarIntento("a".repeat(64),"b".repeat(64),i);
        assertThat(repo.consumirIntento("a".repeat(64),"x".repeat(64))).isEmpty();
        try(var e=Executors.newFixedThreadPool(2)) {
            var a=e.submit(()->repo.consumirIntento("a".repeat(64),"b".repeat(64)).isPresent());
            var b=e.submit(()->otro.consumirIntento("a".repeat(64),"b".repeat(64)).isPresent());
            assertThat(a.get() ^ b.get()).isTrue();
        }
    }
    @Test void lockEsGlobalYMarcaRenovandoSobreviveEntreInstancias() throws Exception {
        var dentro=new CountDownLatch(1);var salir=new CountDownLatch(1);
        try(var e=Executors.newSingleThreadExecutor()) {
            var tarea=e.submit(()->repo.exclusivo(()->{
                repo.guardar(new ConexionAutorizada(null,"RENOVANDO","f".repeat(64),Instant.now(),DiagnosticoConexion.OK));
                dentro.countDown();try { salir.await(10,TimeUnit.SECONDS); } catch(InterruptedException ex){Thread.currentThread().interrupt();}
                return null;
            }));
            try {
                assertThat(dentro.await(10,TimeUnit.SECONDS)).isTrue();
                assertThat(otro.consultar().orElseThrow().estado()).isEqualTo("RENOVANDO");
                assertThatThrownBy(()->otro.exclusivo(()->true)).isInstanceOf(AutorizacionExternaException.class);
            } finally { salir.countDown(); }
            tarea.get(10,TimeUnit.SECONDS);
        }
        assertThat(otro.exclusivo(()->true)).isTrue();
    }
}
