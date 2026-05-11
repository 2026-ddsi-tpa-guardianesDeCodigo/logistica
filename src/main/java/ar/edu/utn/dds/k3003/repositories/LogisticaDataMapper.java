package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.NecesidadMaterial;
import ar.edu.utn.dds.k3003.model.Paquete;

public class LogisticaDataMapper {

  public DepositoDTO toDepositoDTO(Deposito deposito) {
    var stockDTO = deposito.getStockActual().stream()
            .map(this::toPaqueteDTO)
            .toList();

    return new DepositoDTO(
            deposito.getId(),
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
    deposito.setId(depositoDTO.id());
    return deposito;
  }
  public PaqueteDTO toPaqueteDTO(Paquete paquete){
    return new PaqueteDTO(
            paquete.getId(),
            paquete.getDonacionID(),
            paquete.getProducto(),
            paquete.getCantidad()
    );
  }

  public Paquete toPaquete(PaqueteDTO paqueteDTO){
    var paquete =  new Paquete(
            paqueteDTO.donacionID(),
            paqueteDTO.producto(),
            paqueteDTO.cantidad()
    );
    paquete.setId(paqueteDTO.id());
    return paquete;
  }
  public Asignacion toAsignacion(AsignacionDTO asignacionDTO){
    var asignacion =  new Asignacion(
            asignacionDTO.id(),
            asignacionDTO.paqueteID(),
            asignacionDTO.necesidadID(),
            asignacionDTO.fecha(),
            asignacionDTO.estado()
    );
    asignacion.setId(asignacionDTO.id());
    return asignacion;
  }

  public AsignacionDTO toAsignacionDTO(Asignacion asignacion){
    return new AsignacionDTO(
            asignacion.getId(),
            asignacion.getPaqueteID(),
            asignacion.getNecesidadID(),
            asignacion.getFecha(),
            asignacion.getEstado()
    );
  }

  public NecesidadMaterialDTO toNecesidadDeEntidadDTO (NecesidadMaterial necesidad) {
    return new NecesidadMaterialDTO(
            necesidad.getId(),
            necesidad.getEntidadID(),
            necesidad.getNivelDeUrgencia(),
            necesidad.getDescripcion(),
            necesidad.getCantidadObjetivo(),
            necesidad.getProductoSolicitadoID(),
            necesidad.getTipo()
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
