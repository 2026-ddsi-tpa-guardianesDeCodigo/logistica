package ar.edu.utn.dds.k3003.model;

import java.util.List;

public interface Algoritmo {
     public NecesidadMaterial correr(Paquete paquete, List<NecesidadMaterial> necesidadesDeEntidades);
}
