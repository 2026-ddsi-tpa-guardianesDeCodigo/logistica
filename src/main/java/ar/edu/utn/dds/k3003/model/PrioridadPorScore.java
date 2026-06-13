package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.exceptions.NoHayNecesidades;

import java.util.Comparator;
import java.util.List;

public class PrioridadPorScore implements Algoritmo{
    @Override
    public NecesidadMaterial correr(Paquete paquete, List<NecesidadMaterial> necesidades) {
        return necesidades.stream()
                .max(Comparator.comparingInt(necesidad -> calcularScore(paquete, necesidad)))
                .orElseThrow(() -> new NoHayNecesidades("No hay necesidades materiales insatisfechas"));
    }

    private int calcularScore(Paquete paquete, NecesidadMaterial necesidad) {
        int cantidadDonada = paquete.getCantidad();
        int cantidadObjetivo = necesidad.getCantidadObjetivo();
        int urgencia = necesidad.getNivelDeUrgencia();

        int cobertura = Math.max(1, cantidadDonada / cantidadObjetivo);
        return urgencia / cobertura;
    }
}
