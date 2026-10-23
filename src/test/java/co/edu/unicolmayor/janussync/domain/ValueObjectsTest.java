package co.edu.unicolmayor.janussync.domain;

import co.edu.unicolmayor.janussync.domain.exception.DatoInvalidoException;
import co.edu.unicolmayor.janussync.domain.model.ConsultaUsuarios;
import co.edu.unicolmayor.janussync.domain.valueobject.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class ValueObjectsTest {
    @Test void normalizaCorreoYComparaPorValor() {
        assertThat(new CorreoElectronico("  Laura.Mendez+admin@UNICOLEMAYOR.EDU.CO "))
                .isEqualTo(new CorreoElectronico("laura.mendez+admin@unicolemayor.edu.co"));
    }

    @Test void normalizaDominioInternacional() {
        assertThat(new CorreoElectronico("persona@españa.edu.co").valor()).isEqualTo("persona@xn--espaa-rta.edu.co");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "sin-arroba", "a@@dominio.co", ".a@dominio.co", "a..b@dominio.co",
            "a@-dominio.co", "a@dominio", "a @dominio.co", "a@dominio..co", "a@dominio.co\nBcc:persona@dominio.co"})
    void rechazaCorreoInvalido(String valor) {
        assertThatThrownBy(() -> new CorreoElectronico(valor)).isInstanceOf(DatoInvalidoException.class);
    }

    @Test void limitaParteLocalDelCorreo() {
        assertThatThrownBy(() -> new CorreoElectronico("a".repeat(65) + "@dominio.co"))
                .isInstanceOf(DatoInvalidoException.class);
    }

    @Test void admiteNombresRealesSinImponerSoloLetras() {
        assertThat(new NombrePersona("  María-José D'Ávila  ").valor()).isEqualTo("María-José D'Ávila");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "Ana\nMaría", "Pedro\tLuis"})
    void rechazaNombresInvalidos(String valor) {
        assertThatThrownBy(() -> new NombrePersona(valor)).isInstanceOf(DatoInvalidoException.class);
    }

    @Test void limitaNombreYRol() {
        assertThatThrownBy(() -> new NombrePersona("a".repeat(101))).isInstanceOf(DatoInvalidoException.class);
        assertThatThrownBy(() -> new NombreRol("a".repeat(81))).isInstanceOf(DatoInvalidoException.class);
        assertThatThrownBy(() -> new NombreRol(" ")).isInstanceOf(DatoInvalidoException.class);
        assertThat(new NombreRol(" Colaborador ").valor()).isEqualTo("Colaborador");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"1-1-1-1-1", "u-001", "no-es-uuid"})
    void rechazaIdentificadorNoCanonico(String valor) {
        assertThatThrownBy(() -> UsuarioId.desde(valor)).isInstanceOf(DatoInvalidoException.class);
    }

    @Test void identificadorSeComparaPorValor() {
        var uuid = UUID.randomUUID();
        assertThat(UsuarioId.desde(uuid.toString())).isEqualTo(new UsuarioId(uuid));
    }

    @ParameterizedTest @CsvSource({"0,10", "1,0", "1,101", "-1,10"})
    void limitaPaginacion(int pagina, int tamano) {
        assertThatThrownBy(() -> new ConsultaUsuarios(null, null, null, pagina, tamano))
                .isInstanceOf(DatoInvalidoException.class);
    }

    @Test void desplazamientoNoDesbordaEnteros() {
        var consulta = new ConsultaUsuarios("  Laura  ", null, "  ", Integer.MAX_VALUE, 100);
        assertThat(consulta.desplazamiento()).isEqualTo(214748364600L);
        assertThat(consulta.busqueda()).isEqualTo("Laura");
        assertThat(consulta.rol()).isNull();
    }
}
