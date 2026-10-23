package co.edu.unicolmayor.janussync.infrastructure;

import co.edu.unicolmayor.janussync.application.usecase.*;
import co.edu.unicolmayor.janussync.domain.exception.UsuarioNoEncontradoException;
import co.edu.unicolmayor.janussync.domain.model.*;
import co.edu.unicolmayor.janussync.infrastructure.adapter.web.*;
import co.edu.unicolmayor.janussync.infrastructure.config.SeguridadLocalConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UsuarioController.class, RolController.class}, properties = {
        "janussync.local.usuario=desarrollador", "janussync.local.contrasena=SoloPruebasLocales2026"})
@ActiveProfiles("local")
@Import({SeguridadLocalConfiguration.class, ErroresHandler.class})
class ConsultasHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean ListarUsuariosUseCase listar;
    @MockitoBean ObtenerUsuarioUseCase obtener;
    @MockitoBean ListarRolesUseCase roles;
    @MockitoBean GestionarRolesUseCase gestionar;

    @Test void requiereAutenticacion() throws Exception {
        mvc.perform(get("/api/v1/usuarios")).andExpect(status().isUnauthorized());
        verifyNoInteractions(listar);
    }

    @Test void rechazaCredencialIncorrecta() throws Exception {
        mvc.perform(get("/api/v1/usuarios").with(httpBasic("desarrollador", "incorrecta")))
                .andExpect(status().isUnauthorized());
    }

    @Test void exigePermisoAunqueEsteAutenticado() throws Exception {
        mvc.perform(get("/api/v1/usuarios").with(user("sin-permiso").authorities(() -> "PERFIL_CONSULTAR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(listar);
    }

    @Test void devuelvePaginaVaciaRealConCredencialLocal() throws Exception {
        when(listar.ejecutar(any())).thenReturn(new Pagina<>(List.of(), 1, 10, 0));
        mvc.perform(get("/api/v1/usuarios").with(httpBasic("desarrollador", "SoloPruebasLocales2026")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.elementos").isEmpty())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test void rechazaPaginaYEstadoInvalidos() throws Exception {
        mvc.perform(get("/api/v1/usuarios?pagina=0").with(user("lector").authorities(() -> "USUARIOS_CONSULTAR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/usuarios?estado=DESCONOCIDO").with(user("lector").authorities(() -> "USUARIOS_CONSULTAR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(listar);
    }

    @Test void rechazaUuidInvalidoAntesDeConsultar() throws Exception {
        mvc.perform(get("/api/v1/usuarios/u-001").with(user("lector").authorities(() -> "USUARIOS_CONSULTAR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(obtener);
    }

    @Test void usuarioAusenteEs404() throws Exception {
        when(obtener.ejecutar(any())).thenThrow(new UsuarioNoEncontradoException());
        mvc.perform(get("/api/v1/usuarios/00000000-0000-0000-0000-000000000001")
                .with(user("lector").authorities(() -> "USUARIOS_CONSULTAR"))).andExpect(status().isNotFound());
    }

    @Test void falloDeBaseNoSeConvierteEnListaVaciaNiExponeSql() throws Exception {
        when(listar.ejecutar(any())).thenThrow(new DataAccessResourceFailureException("jdbc:mysql://interno secreto"));
        mvc.perform(get("/api/v1/usuarios").with(user("lector").authorities(() -> "USUARIOS_CONSULTAR")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc:mysql"))));
    }

    @Test void consultaRolesExigeSuPropioPermiso() throws Exception {
        when(roles.ejecutar()).thenReturn(List.of());
        mvc.perform(get("/api/v1/roles").with(user("lector").authorities(() -> "USUARIOS_CONSULTAR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles").with(user("lector").authorities(() -> "ROLES_CONSULTAR")))
                .andExpect(status().isOk());
    }
    @Test void falloDeRolesNoSeConvierteEnCatalogoVacio() throws Exception {
        when(roles.ejecutar()).thenThrow(new DataAccessResourceFailureException("detalle interno"));
        mvc.perform(get("/api/v1/roles").with(user("lector").authorities(() -> "ROLES_CONSULTAR")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalle interno"))));
    }

}
