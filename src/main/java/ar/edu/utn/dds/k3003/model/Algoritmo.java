package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;

import java.util.List;

public interface Algoritmo {
     public NecesidadMaterial correr(Paquete paquete, List<NecesidadMaterial> necesidadesDeEntidades);
}
