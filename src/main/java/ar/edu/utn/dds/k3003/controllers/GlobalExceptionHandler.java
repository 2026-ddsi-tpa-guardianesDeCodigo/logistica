package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.exceptions.*;
import ar.edu.utn.dds.k3003.infra.logging.EventLogger;
import ar.edu.utn.dds.k3003.infra.logging.EventoLog;
import ar.edu.utn.dds.k3003.infra.logging.Outcome;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final EventLogger LOG = EventLogger.of(GlobalExceptionHandler.class);

    private static void validacionFallida(Exception e) {
        LOG.evento(EventoLog.VALIDATION_FAILED, "Validación fallida")
                .dato("error.type", e.getClass().getSimpleName())
                .outcome(Outcome.FAILURE).warn().emitir();
    }

    @ExceptionHandler({DepositoNoEncontradoException.class,
            AsignacionNoEncontrada.class,
            NoSuchElementException.class})
    public ResponseEntity<String> handleNotFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler({DepositoYaExistenteException.class,
            AsignacionYaExistenteException.class,
            EntregaYaReportadaException.class})
    public ResponseEntity<String> handleConflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler({DonacionParcialNoPermitida.class,
            AlgoritmoNoConfiguradoException.class,
            NoHayNecesidades.class,
            IllegalArgumentException.class,
            CantidadDeProductoInvalida.class})
    public ResponseEntity<String> handleBadRequest(Exception e) {
        validacionFallida(e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    // DepositoLleno ya se logeó como deposito.capacidad.rechazada en el servicio (no log-and-throw).
    @ExceptionHandler(DepositoLleno.class)
    public ResponseEntity<String> handleDepositoLleno(DepositoLleno e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<String> handleNumberFormat(NumberFormatException e) {
        validacionFallida(e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El ID debe ser un valor numérico válido");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleMalformedBody(HttpMessageNotReadableException e) {
        // Solo el tipo de error: el mensaje de Jackson incluye fragmentos del body recibido.
        validacionFallida(e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("El cuerpo de la solicitud es inválido o contiene un valor no soportado");
    }

    // Cualquier llamada a Donaciones o Donadores y Entidades que devuelva un error (ej. un
    // donacionID inexistente, o el servicio caido) termina aca en vez de tirar un 500 pelado.
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> handleDownstreamError(RestClientResponseException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("Fallo un servicio externo (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
    }

    // A diferencia de RestClientResponseException (el downstream respondió con un error),
    // esto es cuando el downstream ni siquiera es alcanzable (caído, timeout, DNS, etc).
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<String> handleDownstreamUnreachable(ResourceAccessException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("Un servicio externo no está disponible: " + e.getMessage());
    }

    // Un query param/path variable con tipo inválido: 400, no 500.
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<String> handleTypeMismatch(TypeMismatchException e) {
        validacionFallida(e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Un parámetro tiene un valor inválido");
    }

    // Único lugar donde se logea una excepción no manejada, con stack trace. Los errores 4xx del
    // propio framework (ruta inexistente, método/media type no soportado, falta un parámetro) se
    // devuelven con su status: el access log (WARN) ya deja constancia.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse respuesta) {
            return ResponseEntity.status(respuesta.getStatusCode()).body(respuesta.getBody().getDetail());
        }
        LOG.evento(EventoLog.UNHANDLED_EXCEPTION, "Excepción no manejada")
                .error(e).emitir();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno");
    }
}
