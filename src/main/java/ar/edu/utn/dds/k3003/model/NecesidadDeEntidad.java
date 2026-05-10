package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.NecesidadDeEntidadDTO;

import java.time.LocalDateTime;

public class NecesidadDeEntidad {

    private String id;
    private String entidadID;
    private Integer nivelDeUrgencia;
    private String descripcion;
    private Integer cantidadObjetivo;
    private String productoSolicitado;


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEntidadID() {
        return entidadID;
    }

    public void setEntidadID(String entidadID) {
        this.entidadID = entidadID;
    }

    public Integer getNivelDeUrgencia() {
        return nivelDeUrgencia;
    }

    public void setNivelDeUrgencia(Integer nivelDeUrgencia) {
        this.nivelDeUrgencia = nivelDeUrgencia;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getCantidadObjetivo() {
        return cantidadObjetivo;
    }

    public void setCantidadObjetivo(Integer cantidadObjetivo) {
        this.cantidadObjetivo = cantidadObjetivo;
    }

    public String getProductoSolicitado() {
        return productoSolicitado;
    }

    public void setProductoSolicitado(String productoSolicitado) {
        this.productoSolicitado = productoSolicitado;
    }

    public NecesidadDeEntidad (
            String id,
            String entidadID,
            Integer nivelDeUrgencia,
            String  descripcion,
            Integer cantidadObjetivo,
            String productoSolicitado
    ) {
        this.id = id;
        this.entidadID = entidadID;
        this.nivelDeUrgencia  = nivelDeUrgencia;
        this.descripcion  = descripcion;
        this.cantidadObjetivo = cantidadObjetivo;
        this.productoSolicitado = productoSolicitado;

    }
}
