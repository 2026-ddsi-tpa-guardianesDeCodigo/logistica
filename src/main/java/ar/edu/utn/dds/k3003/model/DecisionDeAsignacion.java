package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.TipoNecesidadMaterialEnum;
import ar.edu.utn.dds.k3003.exceptions.AlgoritmoNoConfiguradoException;
import java.util.ArrayList;
import java.util.List;

/**
 * Logica de matchmaking sin efectos de lado (no persiste nada), para poder correrla tanto
 * dentro del proceso principal de Logistica como en un Worker externo sin base de datos.
 */
public final class DecisionDeAsignacion {

    private DecisionDeAsignacion() {}

    public record Decision(String necesidadElegidaID, int cantidadAsignada, int sobrante) {}

    public static Decision decidir(
            Algoritmo algoritmo,
            String productoID,
            int cantidad,
            List<NecesidadMaterialDTO> necesidadesDisponibles) {

        if (necesidadesDisponibles.isEmpty()) {
            return new Decision(null, 0, cantidad);
        }
        if (algoritmo == null) {
            throw new AlgoritmoNoConfiguradoException("El depósito no tiene algoritmo configurado");
        }

        List<NecesidadMaterial> necesidadesDeEntidades =
                necesidadesDisponibles.stream().map(DecisionDeAsignacion::toNecesidadDeEntidad).toList();
        Paquete paqueteCandidato = new Paquete(null, productoID, cantidad);
        NecesidadMaterial necesidadElegidaModelo = algoritmo.correr(paqueteCandidato, necesidadesDeEntidades);
        int indice = necesidadesDeEntidades.indexOf(necesidadElegidaModelo);
        NecesidadMaterialDTO necesidadElegida = necesidadesDisponibles.get(indice);

        boolean noAlcanza = cantidad < necesidadElegida.cantidadObjetivo();
        if (noAlcanza && necesidadElegida.tipo() == TipoNecesidadMaterialEnum.RECURRENTE) {
            List<NecesidadMaterialDTO> restantes = new ArrayList<>(necesidadesDisponibles);
            restantes.remove(indice);
            return decidir(algoritmo, productoID, cantidad, restantes);
        }

        int cantidadAAsignar = Math.min(cantidad, necesidadElegida.cantidadObjetivo());
        int sobrante = cantidad - cantidadAAsignar;
        return new Decision(necesidadElegida.id(), cantidadAAsignar, sobrante);
    }

    private static NecesidadMaterial toNecesidadDeEntidad(NecesidadMaterialDTO dto) {
        return new NecesidadMaterial(
                dto.id(),
                dto.entidadID(),
                dto.nivelDeUrgencia(),
                dto.descripcion(),
                dto.cantidadObjetivo(),
                dto.productoSolicitadoID(),
                dto.tipo());
    }
}
