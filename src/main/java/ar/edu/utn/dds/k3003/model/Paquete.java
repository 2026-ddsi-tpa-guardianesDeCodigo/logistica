package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;

import java.util.ArrayList;

public class Paquete {

    private String id;
    private String donacionID;
    private String producto;
    private Integer cantidad;



    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDonacionID() {
        return donacionID;
    }

    public void setDonacionID(String donacionID) {
        this.donacionID = donacionID;
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }


    public Paquete(
            String donacionID,
            String producto,
            Integer cantidad
            ) {
        this.donacionID = donacionID;
        this.producto = producto;
        this.cantidad = cantidad;
    }
}
