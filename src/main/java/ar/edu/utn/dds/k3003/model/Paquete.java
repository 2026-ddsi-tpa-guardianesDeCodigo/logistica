package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.*;

@Entity
@Table(name = "paquetes")
public class Paquete {

    @Id
    private String id;

    private String donacionID;
    private String producto;
    private Integer cantidad;

    @ManyToOne
    @JoinColumn(name = "deposito_id")
    private Deposito deposito;

    public Paquete() {}

    public Paquete(String donacionID, String producto, Integer cantidad) {
        this.donacionID = donacionID;
        this.producto = producto;
        this.cantidad = cantidad;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDonacionID() { return donacionID; }
    public void setDonacionID(String donacionID) { this.donacionID = donacionID; }
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public Deposito getDeposito() { return deposito; }
    public void setDeposito(Deposito deposito) { this.deposito = deposito; }
}