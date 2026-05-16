package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.exceptions.*;
import ar.edu.utn.dds.k3003.model.GestionDonacionRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class LogisticaController {

  private Fachada fachada;

  public LogisticaController(Fachada fachada) {
    this.fachada = fachada;
  }

  @PostMapping("/depositos")
  public ResponseEntity <DepositoDTO> postDeposito(@RequestBody DepositoDTO depositoDTO){
    DepositoDTO depositoAgregado = fachada.agregarDeposito (depositoDTO);
    return ResponseEntity.status(HttpStatus.CREATED).body(depositoAgregado);
  }

  @GetMapping("/depositos")
  public ResponseEntity<List<DepositoDTO>> getDeposito() {

    return ResponseEntity.ok(fachada.obtenerTodosLosDepositos());
  }

  @GetMapping("/depositos/{id}")
  public ResponseEntity<DepositoDTO> getDeposito(@PathVariable String id) {

    return ResponseEntity.ok(fachada.buscarDepositoPorID(id));
  }

  @DeleteMapping("/depositos/{id}")
  public ResponseEntity<DepositoDTO> deleteDeposito(@PathVariable String id) {

    return ResponseEntity.ok(fachada.borrarDeposito(id));
  }

  @GetMapping("/asignaciones/{id}")
  public ResponseEntity<AsignacionDTO> getAsignacion(@PathVariable String id) {

    return ResponseEntity.ok(fachada.buscarAsignacionPorID(id));
  }

  @PostMapping("/depositos/{id}/donacion")
  public ResponseEntity <DepositoDTO> postGestionarDonacion(@PathVariable String id, @RequestBody GestionDonacionRequestDTO request){

    DepositoDTO depositoDTO = fachada.gestionarDonacion(id, request.donacionID(), request.productoID(), request.cantidad());
    return ResponseEntity.status(HttpStatus.CREATED).body(depositoDTO);
  }

  @PostMapping("/entregas")
  public ResponseEntity<Void> postRegistrarEntregaDePaquete(@RequestBody PaqueteDTO paqueteDTO) {
    fachada.reportarEntrega(paqueteDTO);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

}
