package ar.edu.utn.dds.k3003.worker;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.model.Algoritmo;
import ar.edu.utn.dds.k3003.model.DecisionDeAsignacion;
import ar.edu.utn.dds.k3003.model.DonacionMensajeDTO;
import ar.edu.utn.dds.k3003.model.PrioridadASubAtendidos;
import ar.edu.utn.dds.k3003.model.PrioridadPorScore;
import ar.edu.utn.dds.k3003.model.WorkerResultadoDTO;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/**
 * Worker standalone (TIP 3 de la consigna): sin Spring Boot, sin base de datos. Se conecta a
 * la misma cola que el worker embebido (DonacionQueueListener) y, para cada donacion, decide
 * la asignacion via HTTP (GET a Donadores y Entidades, GET al deposito en Logistica) y reporta
 * el resultado con un POST a Logistica. Pensado para correrse local: `mvn -q exec:java
 * -Dexec.mainClass=ar.edu.utn.dds.k3003.worker.WorkerApp` (o desde la IDE), con las mismas
 * variables de entorno QUEUE_* que usa la app principal, mas LOGISTICA_URL y DONADORES_URL.
 */
public final class WorkerApp {

    private WorkerApp() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> env = System.getenv();

        // QUEUE_URL es la URI completa que da CloudAMQP (amqps://usuario:password@host/vhost).
        // setUri activa TLS solo cuando corresponde (esquema "amqps"), a diferencia de setear
        // host/port/user/pass a mano, donde es facil olvidarse del TLS y fallar la conexion.
        com.rabbitmq.client.ConnectionFactory rabbitConnectionFactory = new com.rabbitmq.client.ConnectionFactory();
        rabbitConnectionFactory.setUri(env.getOrDefault("QUEUE_URL", "amqp://guest:guest@localhost:5672/"));
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(rabbitConnectionFactory);

        String queueName = env.getOrDefault("QUEUE_NAME", "donaciones-pendientes");
        RestClient logisticaClient = RestClient.create(env.getOrDefault("LOGISTICA_URL", "http://localhost:8084"));
        RestClient donadoresClient = RestClient.create(env.getOrDefault("DONADORES_URL", "http://localhost:8081"));
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

        // Declara la cola si todavia no existe, para no depender de que la app principal
        // (que la declara via RabbitConfig) haya arrancado antes que este worker.
        new RabbitAdmin(connectionFactory).declareQueue(new Queue(queueName, true));

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(message -> {
            try {
                DonacionMensajeDTO mensaje = (DonacionMensajeDTO) converter.fromMessage(message);
                procesar(mensaje, logisticaClient, donadoresClient);
            } catch (Exception e) {
                System.err.println("Error procesando mensaje de la cola: " + e.getMessage());
                e.printStackTrace();
            }
        });
        container.start();

        System.out.println("Worker standalone escuchando la cola '" + queueName + "'. Ctrl+C para salir.");
        Thread.currentThread().join();
    }

    private static void procesar(DonacionMensajeDTO mensaje, RestClient logisticaClient, RestClient donadoresClient) {
        DepositoDTO deposito = logisticaClient
                .get()
                .uri("/depositos/{id}", mensaje.depositoID())
                .retrieve()
                .body(DepositoDTO.class);

        Algoritmo algoritmo =
                switch (deposito.algoritmo()) {
                    case SUB_ATENDIDOS -> new PrioridadASubAtendidos();
                    case PRIORIDAD_POR_SCORE -> new PrioridadPorScore();
                    case null -> null;
                };

        List<NecesidadMaterialDTO> necesidades = donadoresClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/necesidades")
                        .queryParam("productoID", mensaje.productoID())
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<NecesidadMaterialDTO>>() {});

        DecisionDeAsignacion.Decision decision =
                DecisionDeAsignacion.decidir(algoritmo, mensaje.productoID(), mensaje.cantidad(), necesidades);

        WorkerResultadoDTO resultado = new WorkerResultadoDTO(
                mensaje.donacionID(),
                mensaje.productoID(),
                decision.necesidadElegidaID(),
                decision.cantidadAsignada(),
                decision.sobrante());

        logisticaClient
                .post()
                .uri("/depositos/{id}/asignaciones/worker", mensaje.depositoID())
                .body(resultado)
                .retrieve()
                .toBodilessEntity();

        System.out.println("Donacion " + mensaje.donacionID() + " procesada: asignada="
                + decision.cantidadAsignada() + " sobrante=" + decision.sobrante());
    }
}
