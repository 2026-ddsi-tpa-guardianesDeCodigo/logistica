package ar.edu.utn.dds.k3003.model;

public record WorkerResultadoDTO(
    String donacionID,
    String productoID,
    String necesidadElegidaID,
    Integer cantidadAsignada,
    Integer sobrante) {}
