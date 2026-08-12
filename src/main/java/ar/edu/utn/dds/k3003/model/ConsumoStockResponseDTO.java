package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import java.util.List;

public record ConsumoStockResponseDTO(Integer cantidadAsignada, List<AsignacionDTO> asignaciones) {}
