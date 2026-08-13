package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
            CantidadDeProductoInvalida.class,
            DepositoLleno.class})
    public ResponseEntity<String> handleBadRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<String> handleNumberFormat(NumberFormatException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El ID debe ser un valor numérico válido");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleMalformedBody(HttpMessageNotReadableException e) {
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
}