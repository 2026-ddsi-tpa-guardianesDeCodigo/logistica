package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.exceptions.CantidadDeProductoInvalida;
import ar.edu.utn.dds.k3003.exceptions.DepositoLleno;
import ar.edu.utn.dds.k3003.repositories.LogisticaDataMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Deposito {

  private String id;
  private String nombre;
  private String direccion;
  private Integer capacidadMaxima;
  private List<Paquete> stockActual;
  private AtomicLong idSecuencial = new AtomicLong(1);


  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public String getDireccion() {
    return direccion;
  }

  public void setDireccion(String direccion) {
    this.direccion = direccion;
  }

  public void setCapacidadMaxima(Integer capacidadMaxima) {
    this.capacidadMaxima = capacidadMaxima;
  }

  public void setStockActual(List<Paquete> stockActual) {
    this.stockActual = stockActual;
  }

  public Integer getCapacidadMaxima() {
    return capacidadMaxima;
  }

  public List<Paquete> getStockActual() {
    return stockActual;
  }

  public Deposito(
      String nombre,
      String direccion,
      Integer capacidadMaxima) {
    this.nombre = nombre;
    this.direccion = direccion;
    this.capacidadMaxima = capacidadMaxima;
    this.stockActual = new ArrayList<>();
  }

  public void verificarCantidad(Integer cantidad){
    if (cantidad == null || cantidad <= 0){
      throw new CantidadDeProductoInvalida("La cantidad de productos debe ser mayor o igual a 1");
    }
  }

  public Paquete agregarPaquete (Paquete paquete){
    if(capacidadMaxima == stockActual.toArray().length){
      throw new DepositoLleno("El deposito esta lleno");
    }

    paquete.setId(String.valueOf(idSecuencial.getAndIncrement()));

    stockActual.add(paquete);

    return paquete;
  }
}
