package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.exceptions.CantidadDeProductoInvalida;
import ar.edu.utn.dds.k3003.exceptions.DepositoLleno;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "depositos")
public class Deposito {

  @Id
  private String id;

  @Enumerated(EnumType.STRING)
  private TipoAlgoritmoEnum algoritmo;

  @Transient
  private Algoritmo algoritmoObj = null;

  private String nombre;
  private String direccion;
  private Integer capacidadMaxima;

  @OneToMany(mappedBy = "deposito", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  private List<Paquete> stockActual = new ArrayList<>();

  public Deposito() {
    this.id = UUID.randomUUID().toString();
  }

  public Deposito(String id, String nombre, String direccion, Integer capacidadMaxima) {
    this.id = (id != null) ? id : UUID.randomUUID().toString();
    this.nombre = nombre;
    this.direccion = direccion;
    this.capacidadMaxima = capacidadMaxima;
    this.stockActual = new ArrayList<>();
  }

  @PostLoad
  public void reconstruirAlgoritmo() {
    if (algoritmo != null) {
      this.algoritmoObj = switch (algoritmo) {
        case SUB_ATENDIDOS -> new PrioridadASubAtendidos();
        case PRIORIDAD_POR_SCORE -> new PrioridadPorScore();
      };
    }
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public TipoAlgoritmoEnum getAlgoritmo() { return algoritmo; }
  public void setAlgoritmo(TipoAlgoritmoEnum algoritmo) { this.algoritmo = algoritmo; }
  public Algoritmo getAlgoritmoObj() { return algoritmoObj; }
  public void setAlgoritmoObj(Algoritmo algoritmoObj) { this.algoritmoObj = algoritmoObj; }
  public String getNombre() { return nombre; }
  public void setNombre(String nombre) { this.nombre = nombre; }
  public String getDireccion() { return direccion; }
  public void setDireccion(String direccion) { this.direccion = direccion; }
  public Integer getCapacidadMaxima() { return capacidadMaxima; }
  public void setCapacidadMaxima(Integer capacidadMaxima) { this.capacidadMaxima = capacidadMaxima; }
  public List<Paquete> getStockActual() { return stockActual; }
  public void setStockActual(List<Paquete> stockActual) { this.stockActual = stockActual; }

  public void verificarCantidad(Integer cantidad) {
    if (cantidad == null || cantidad <= 0) {
      throw new CantidadDeProductoInvalida("La cantidad de productos debe ser mayor o igual a 1");
    }
  }

  public Paquete agregarPaquete(Paquete paquete) {
    if (capacidadMaxima != null && capacidadMaxima == stockActual.size()) {
      throw new DepositoLleno("El deposito esta lleno");
    }
    paquete.setId(UUID.randomUUID().toString());
    paquete.setDeposito(this);
    stockActual.add(paquete);
    return paquete;
  }
}
