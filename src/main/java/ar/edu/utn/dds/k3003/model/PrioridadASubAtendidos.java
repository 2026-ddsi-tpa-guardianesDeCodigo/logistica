package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.NecesidadDeEntidadDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;

import java.util.Comparator;
import java.util.List;

public class PrioridadASubAtendidos implements Algoritmo{

    @Override
    public NecesidadDeEntidad correr(Paquete paquete, List<NecesidadDeEntidad> necesidadesDeEntidades) {

        return necesidadesDeEntidades.stream()
                .max(Comparator.comparingInt(NecesidadDeEntidad::getCantidadObjetivo))
                .orElse(null);

    }
}
