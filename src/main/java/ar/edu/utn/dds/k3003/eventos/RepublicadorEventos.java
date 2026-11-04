package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.JpaAsignacionesRepo;
import ar.edu.utn.dds.k3003.repositories.JpaDepositosRepo;
import ar.edu.utn.dds.k3003.repositories.JpaPaquetesRepo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reenvía el snapshot actual de todo lo que publica este módulo. Cubre el evento perdido entre el
 * commit y el publish y deja el modelo de lectura sincronizado antes de la demo.
 */
@Component
public class RepublicadorEventos {
    private final JpaDepositosRepo depositos;
    private final JpaAsignacionesRepo asignaciones;
    private final JpaPaquetesRepo paquetes;
    private final ConstructorDeEventos constructor;
    private final PublicadorEventos publicador;

    public RepublicadorEventos(JpaDepositosRepo depositos, JpaAsignacionesRepo asignaciones,
                               JpaPaquetesRepo paquetes, ConstructorDeEventos constructor,
                               PublicadorEventos publicador) {
        this.depositos = depositos;
        this.asignaciones = asignaciones;
        this.paquetes = paquetes;
        this.constructor = constructor;
        this.publicador = publicador;
    }

    /** @return cantidad de eventos que el broker aceptó. */
    public int republicarTodo() {
        List<EventoDominio> eventos = new ArrayList<>();
        depositos.findAll().forEach(d -> constructor.construir(d, false).ifPresent(eventos::add));
        asignaciones.findAll().forEach(a -> constructor.construir(a, false).ifPresent(eventos::add));

        // Un evento de stock por cada par (depósito, producto) que tenga paquetes hoy.
        Set<String> pares = new LinkedHashSet<>();
        for (Paquete p : paquetes.findAll()) {
            if (p.getDeposito() != null && pares.add(p.getDeposito().getId() + "|" + p.getProducto())) {
                eventos.add(constructor.stock(p.getDeposito().getId(), p.getProducto()));
            }
        }
        return (int) eventos.stream().filter(publicador::publicar).count();
    }
}
