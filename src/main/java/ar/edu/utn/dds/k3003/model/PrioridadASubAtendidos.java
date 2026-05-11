package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;

import java.util.Comparator;
import java.util.List;

public class PrioridadASubAtendidos implements Algoritmo{

    @Override
    public NecesidadMaterial correr(Paquete paquete, List<NecesidadMaterial> necesidadesDeEntidades) {

        return necesidadesDeEntidades.stream()
                .max(Comparator.comparingInt(NecesidadMaterial::getCantidadObjetivo))
                .orElse(null);

    }
}
