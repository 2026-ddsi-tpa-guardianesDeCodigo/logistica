package ar.edu.utn.dds.k3003.eventos;

/**
 * El stock no es una entidad: es la suma de los paquetes de un depósito para un producto. Cuando
 * cambia cualquiera de esos paquetes se avisa qué par (depósito, producto) hay que volver a sumar.
 */
public record CambioDeStock(Long depositoId, String producto) {
}
