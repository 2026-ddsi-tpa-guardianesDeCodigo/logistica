package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.model.Paquete;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Listener de entidades JPA (@EntityListeners): un save() que no cambia nada no dispara
 * @PostUpdate, y los borrados en cascada (un depósito se lleva sus paquetes) llegan acá igual.
 * Spring Boot le pide este listener al contexto, por eso puede ser un bean.
 *
 * <p>Los paquetes sin depósito son los ya asignados a una necesidad: no son stock y no publican nada.
 * Lo que no pasa por acá es el borrado masivo por JPQL de {@code JpaPaquetesRepo.eliminarPorId};
 * ese caso lo publica {@code LogisticaRepository.eliminarPaquete}.
 */
@Component
public class PublicadorDeCambios {
    private final ApplicationEventPublisher eventos;

    public PublicadorDeCambios(ApplicationEventPublisher eventos) {
        this.eventos = eventos;
    }

    @PostPersist
    @PostUpdate
    public void alGuardar(Object entidad) {
        publicar(entidad, false);
    }

    @PostRemove
    public void alBorrar(Object entidad) {
        publicar(entidad, true);
    }

    private void publicar(Object entidad, boolean eliminada) {
        if (entidad instanceof Paquete p) {
            if (p.getDeposito() != null) {
                eventos.publishEvent(new CambioDeStock(p.getDeposito().getId(), p.getProducto()));
            }
            return;
        }
        eventos.publishEvent(new CambioDeEntidad(entidad, eliminada));
    }
}
