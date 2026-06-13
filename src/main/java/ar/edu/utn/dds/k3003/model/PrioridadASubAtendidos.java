package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.exceptions.NoHayNecesidades;

import java.util.Comparator;
import java.util.List;

public class PrioridadASubAtendidos implements Algoritmo{

    @Override
    public NecesidadMaterial correr(Paquete paquete, List<NecesidadMaterial> necesidadesDeEntidades) {

        return necesidadesDeEntidades.stream()
                .max(Comparator.comparingInt(NecesidadMaterial::getCantidadObjetivo))
                .orElseThrow(() -> new NoHayNecesidades("No hay necesidades materiales insatisfechas"));

    }
}
