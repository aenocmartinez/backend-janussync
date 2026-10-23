package co.edu.unicolmayor.janussync.infrastructure;

import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.UsuarioController;
import co.edu.unicolmayor.janussync.infrastructure.config.SeguridadCerradaConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
@Import(SeguridadCerradaConfiguration.class)
class SeguridadCerradaTest {
    @Autowired MockMvc mvc;
    @MockitoBean ListarUsuariosUseCase listar;
    @MockitoBean ObtenerUsuarioUseCase obtener;

    @Test void sinPerfilLocalNoSeAbrenRutasAdministrativas() throws Exception {
        mvc.perform(get("/api/v1/usuarios")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/usuarios").with(user("administrador").authorities(() -> "USUARIOS_CONSULTAR")))
                .andExpect(status().isForbidden());
    }
}
