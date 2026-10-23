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

    @ExceptionHandler(DatoInvalidoException.class)
    ProblemDetail datoInvalido(DatoInvalidoException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Uno de los parámetros no tiene el formato esperado.");
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    ProblemDetail noEncontrado(UsuarioNoEncontradoException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail persistencia(DataAccessException error) {
        LOG.error("Fallo al consultar la base propia: {}", error.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "No fue posible consultar la información. Intenta nuevamente más tarde.");
    }
}
