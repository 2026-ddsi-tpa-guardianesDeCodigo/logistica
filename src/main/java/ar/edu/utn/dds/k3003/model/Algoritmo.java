package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.NecesidadDeEntidadDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;

import java.util.List;

public interface Algoritmo {
     public NecesidadDeEntidad correr(Paquete paquete, List<NecesidadDeEntidad> necesidadesDeEntidades);
}
