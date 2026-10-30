package co.edu.unicolmayor.janussync.infrastructure;
import co.edu.unicolmayor.janussync.infrastructure.adapter.persistence.CifradoCredencialesExternas;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class CifradoCredencialesTest {
    @TempDir Path dir;
    @Test void cifradoAleatorioAutenticadoConClaveExternaYContextoPorColumna() throws Exception {
        byte[] clave=new byte[32];new java.security.SecureRandom().nextBytes(clave);
        var archivo=dir.resolve("clave");Files.writeString(archivo,Base64.getEncoder().encodeToString(clave));
        var c=new CifradoCredencialesExternas(archivo.toString());
        var a=c.cifrar("token-secreto","acceso"); var b=c.cifrar("token-secreto","acceso");
        assertThat(a).isNotEqualTo(b);assertThat(new String(a,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("token-secreto");
        assertThat(c.descifrar(a,"acceso")).isEqualTo("token-secreto");
        assertThatThrownBy(()->c.descifrar(a,"refresh")).isInstanceOf(IllegalStateException.class);
        a[a.length-1]^=1;assertThatThrownBy(()->c.descifrar(a,"acceso")).isInstanceOf(IllegalStateException.class);
        assertThat(new CifradoCredencialesExternas("").disponible()).isFalse();
    }
}
