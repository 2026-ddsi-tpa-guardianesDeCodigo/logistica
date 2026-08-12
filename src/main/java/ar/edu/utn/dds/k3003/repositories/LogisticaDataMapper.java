package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.model.Paquete;
import lombok.val;

public class LogisticaDataMapper {

  public DepositoDTO toDepositoDTO(Deposito deposito) {
    var stockDTO = deposito.getStockActual().stream()
            .map(this::toPaqueteDTO)
            .toList();

    return new DepositoDTO(
            deposito.getId() != null ? deposito.getId().toString() : null,
            deposito.getAlgoritmo(),
            deposito.getNombre(),
            deposito.getDireccion(),
            deposito.getCapacidadMaxima(),
            stockDTO
    );
  }

  public Deposito toDeposito(DepositoDTO depositoDTO) {
    var deposito = new Deposito(
            depositoDTO.nombre(),
            depositoDTO.direccion(),
            depositoDTO.capacidadMaxima()
    );
    deposito.setAlgoritmo(depositoDTO.algoritmo());
    return deposito;
  }

  public PaqueteDTO toPaqueteDTO(Paquete paquete){
    return new PaqueteDTO(
            paquete.getId() != null ? paquete.getId().toString() : null,
            paquete.getDonacionID(),
            paquete.getProducto(),
            paquete.getCantidad()
    );
  }

  public Paquete toPaquete(PaqueteDTO paqueteDTO){
    // Ya no seteamos el id: si el DTO trae uno, se ignora — lo genera la BD.
    return new Paquete(
            paqueteDTO.donacionID(),
            paqueteDTO.producto(),
            paqueteDTO.cantidad()
    );
  }

  public Asignacion toAsignacion(AsignacionDTO asignacionDTO){
    return new Asignacion(
            asignacionDTO.paqueteID(),
            asignacionDTO.necesidadID(),
            asignacionDTO.fecha(),
            asignacionDTO.estado()
    );
  }

  public AsignacionDTO toAsignacionDTO(Asignacion asignacion){
    return new AsignacionDTO(
            asignacion.getId() != null ? asignacion.getId().toString() : null,
            asignacion.getPaqueteID(),
            asignacion.getNecesidadID(),
            asignacion.getFecha(),
            asignacion.getEstado()
    );
  }

  public NecesidadMaterial toNecesidadDeEntidad (NecesidadMaterialDTO necesidadDTO) {
    return new NecesidadMaterial(
            necesidadDTO.id(),
            necesidadDTO.entidadID(),
            necesidadDTO.nivelDeUrgencia(),
            necesidadDTO.descripcion(),
            necesidadDTO.cantidadObjetivo(),
            necesidadDTO.productoSolicitadoID(),
            necesidadDTO.tipo()
    );
  }
}
