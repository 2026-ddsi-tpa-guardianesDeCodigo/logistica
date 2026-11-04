package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Deposito;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.JpaPaquetesRepo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EventosDeDominioTest {

    private final JpaPaquetesRepo paquetesRepo = mock(JpaPaquetesRepo.class);
    private final ConstructorDeEventos constructor = new ConstructorDeEventos(paquetesRepo);

    private Deposito deposito(long id) {
        Deposito d = new Deposito("Central", "calle 1", 500);
        d.setId(id);
        d.setAlgoritmo(TipoAlgoritmoEnum.SUB_ATENDIDOS);
        return d;
    }

    private Paquete paquete(Long id, String donacion, String producto, int cantidad, Deposito dep) {
        Paquete p = new Paquete(donacion, producto, cantidad);
        p.setId(id);
        p.setDeposito(dep);
        return p;
    }

    private Asignacion asignacion() {
        Asignacion a = new Asignacion("7", "4", LocalDateTime.of(2026, 11, 5, 10, 0),
                EstadoAsginacionEnum.ASIGNADA, OrigenAsignacionEnum.MATCHMAKING);
        a.setId(21L);
        a.setDepositoID("3");
        return a;
    }

    @Test
    @DisplayName("el depósito publica su capacidad y algoritmo")
    void snapshotDeDeposito() {
        EventoDominio e = constructor.construir(deposito(3), false).orElseThrow();

        assertEquals("logistica.deposito.actualizada", e.routingKey());
        assertEquals(500, e.payload().get("capacidad"));
        assertEquals("SUB_ATENDIDOS", e.payload().get("algoritmo"));
    }

    @Test
    @DisplayName("la asignación toma producto, cantidad y donación del paquete asignado")
    void snapshotDeAsignacion() {
        when(paquetesRepo.findById(7L)).thenReturn(Optional.of(paquete(7L, "57", "9", 30, null)));

        EventoDominio e = constructor.construir(asignacion(), false).orElseThrow();

        assertEquals("AsignacionActualizada", e.tipo());
        assertEquals("57", e.payload().get("donacionId"));
        assertEquals("9", e.payload().get("productoId"));
        assertEquals(30, e.payload().get("cantidad"));
        assertEquals("3", e.payload().get("depositoId"));
        assertEquals("ASIGNADA", e.payload().get("estado"));
        assertNull(e.payload().get("completadaEn"));
        assertNotNull(e.payload().get("creadaEn"));
    }

    @Test
    @DisplayName("una asignación con paqueteID no numérico (matchmaking síncrono heredado) no rompe")
    void asignacionConPaqueteNoNumerico() {
        Asignacion a = asignacion();
        a.setPaqueteID("no-es-un-numero");

        EventoDominio e = assertDoesNotThrow(() -> constructor.construir(a, false).orElseThrow());

        assertNull(e.payload().get("productoId"));
    }

    @Test
    @DisplayName("completar la asignación fija completadaEn una sola vez")
    void completadaEnSeFijaUnaVez() throws InterruptedException {
        Asignacion a = asignacion();
        assertNull(a.getCompletadaEn());

        a.setEstado(EstadoAsginacionEnum.COMPLETADA);
        var primera = a.getCompletadaEn();
        Thread.sleep(5);
        a.setEstado(EstadoAsginacionEnum.COMPLETADA);

        assertNotNull(primera);
        assertEquals(primera, a.getCompletadaEn());
    }

    @Test
    @DisplayName("el stock suma solo los paquetes del producto en ese depósito")
    void stockSumaPorDepositoYProducto() {
        Deposito d = deposito(3);
        when(paquetesRepo.findByDeposito_Id(3L)).thenReturn(List.of(
                paquete(1L, "50", "9", 10, d), paquete(2L, "51", "9", 5, d), paquete(3L, "52", "otro", 99, d)));

        EventoDominio e = constructor.stock(3L, "9");

        assertEquals("logistica.stock.actualizada", e.routingKey());
        assertEquals("3:9", e.entidadId());
        assertEquals(15, e.payload().get("cantidad"));
    }

    @Test
    @DisplayName("las versiones de stock siempre crecen, aun con eventos en el mismo instante")
    void versionesDeStockMonotonas() {
        when(paquetesRepo.findByDeposito_Id(3L)).thenReturn(List.of());

        long anterior = 0;
        for (int i = 0; i < 200; i++) {
            long v = constructor.stock(3L, "9").version();
            assertTrue(v > anterior, "la versión " + v + " no supera a " + anterior);
            anterior = v;
        }
    }

    @Test
    @DisplayName("un paquete ya asignado (sin depósito) no es stock y no publica nada")
    void paqueteAsignadoNoPublica() {
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        PublicadorDeCambios listener = new PublicadorDeCambios(eventos);

        listener.alGuardar(paquete(7L, "57", "9", 30, null));

        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("un paquete en depósito avisa qué par depósito/producto hay que volver a sumar")
    void paqueteEnDepositoAvisaStock() {
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        PublicadorDeCambios listener = new PublicadorDeCambios(eventos);

        listener.alBorrar(paquete(1L, "50", "9", 10, deposito(3)));

        verify(eventos).publishEvent(new CambioDeStock(3L, "9"));
    }

    @Test
    @DisplayName("un borrado se publica con version + 1 para no ser ignorado por el consumidor")
    void borradoSubeLaVersion() {
        EventoDominio e = constructor.construir(deposito(3), true).orElseThrow();

        assertTrue(e.eliminado());
        assertEquals("logistica.deposito.eliminada", e.routingKey());
        assertEquals(1L, e.version());
    }

    @Test
    @DisplayName("si el broker no responde, publicar no lanza: cuenta la falla y sigue")
    void brokerCaidoNoRompeElComando() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new RuntimeException("caido")))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PublicadorEventos publicador = new PublicadorEventos(rabbit, constructor, registry, "donatrack.eventos", true);

        assertDoesNotThrow(() -> publicador.alCambiarEntidad(new CambioDeEntidad(deposito(3), false)));
        assertEquals(1.0, registry.counter("logistica.eventos.publicacion_fallida", "tipo", "DepositoActualizado").count());
    }

    @Test
    @DisplayName("si falla leer la base para armar el snapshot, el comando tampoco se entera")
    void falloAlArmarElSnapshotNoSePropaga() {
        when(paquetesRepo.findByDeposito_Id(3L)).thenThrow(new IllegalStateException("db caida"));
        PublicadorEventos publicador = new PublicadorEventos(mock(RabbitTemplate.class), constructor,
                new SimpleMeterRegistry(), "donatrack.eventos", true);

        assertDoesNotThrow(() -> publicador.alCambiarElStock(new CambioDeStock(3L, "9")));
    }

    @Test
    @DisplayName("publicación deshabilitada: no toca el broker")
    void deshabilitado() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        PublicadorEventos publicador = new PublicadorEventos(rabbit, constructor, new SimpleMeterRegistry(), "x", false);

        publicador.alCambiarEntidad(new CambioDeEntidad(deposito(3), false));
        publicador.alCambiarElStock(new CambioDeStock(3L, "9"));

        verifyNoInteractions(rabbit);
    }
}
