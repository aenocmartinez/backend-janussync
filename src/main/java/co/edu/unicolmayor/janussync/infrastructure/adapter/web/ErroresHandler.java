package co.edu.unicolmayor.janussync.infrastructure.adapter.web;

import co.edu.unicolmayor.janussync.domain.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ErroresHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ErroresHandler.class);

    @ExceptionHandler(ReglaRolException.class)
    ProblemDetail reglaRol(ReglaRolException error) {
        var estado = switch (error.codigo()) {
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "CATALOG_UNAVAILABLE", "DUPLICATE_NAME", "DUPLICATE_EMAIL", "ADMIN_REQUIRED", "CONFLICT", "LINK_INVALID", "LINK_EXPIRED", "LINK_USED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        var problema = ProblemDetail.forStatusAndDetail(estado, error.getMessage());
        problema.setProperty("code", error.codigo());
        if (error.campo() != null)
            problema.setProperty("fieldErrors", java.util.Map.of(error.campo(), error.getMessage()));
        return problema;
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ProblemDetail cuerpoInvalido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido.");
    }

    @ExceptionHandler(ActualizacionCatalogoException.class)
    ProblemDetail catalogo(ActualizacionCatalogoException error){
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,error.getMessage());
        p.setProperty("code","CATALOG_UPDATE_FAILED");return p;
    }

    @ExceptionHandler(AcademusoftNoDisponibleException.class)
    ProblemDetail academusoft(AcademusoftNoDisponibleException error) {
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,error.getMessage());
        p.setProperty("code","ACADEMUSOFT_UNAVAILABLE");return p;
    }

    @ExceptionHandler(AccesoRechazadoException.class)
    ProblemDetail accesoRechazado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "No fue posible validar el acceso.");
    }

    @ExceptionHandler(DatoInvalidoException.class)
    ProblemDetail datoInvalido(DatoInvalidoException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Uno de los parámetros no tiene el formato esperado.");
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    ProblemDetail noEncontrado(UsuarioNoEncontradoException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail persistencia(DataAccessException error) {
        LOG.error("Fallo al consultar la base propia: {}", error.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "No fue posible consultar la información. Intenta nuevamente más tarde.");
    }
}
