package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
            AsignacionYaExistenteException.class})
    public ResponseEntity<String> handleConflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler({DonacionParcialNoPermitida.class,
            DonacionParcialNoPermitida.class,
            AlgoritmoNoConfiguradoException.class,
            NoHayNecesidades.class,
            IllegalArgumentException.class,
            CantidadDeProductoInvalida.class,
            DepositoLleno.class})
    public ResponseEntity<String> handleBadRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}