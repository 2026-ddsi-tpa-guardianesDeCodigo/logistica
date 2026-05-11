package ar.edu.utn.dds.k3003.model;

public record GestionDonacionRequestDTO(
        String depositoID,
        String donacionID,
        String productoID,
        Integer cantidad
) {
}
