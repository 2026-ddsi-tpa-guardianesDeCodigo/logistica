package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.infra.logging.ClienteHttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;

@Component
public class DonadoresYEntidadesClient {

    private final RestClient restClient;

    public DonadoresYEntidadesClient(@Value("${donadores.url}") String baseUrl) {
        // Timeouts generosos por los cold starts de Render (hasta ~90s); sin esto, un
        // downstream realmente caido (no solo dormido) cuelga el request en vez de fallar rapido.
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15_000);
        requestFactory.setReadTimeout(90_000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new ClienteHttpLoggingInterceptor("donadores"))
                .build();
    }

    public List<NecesidadMaterialDTO> obtenerNecesidadesInsatisfechasDe(String productoID) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/necesidades")
                        .queryParam("productoID", productoID)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<NecesidadMaterialDTO>>() {});
    }

    public void satisfacerNecesidad(String necesidadID, Integer cantidad) {
        restClient.post()
                .uri("/necesidades/{necesidadID}/satisfaccion", necesidadID)
                // Explícito: con jackson-dataformat-xml en el classpath RestClient serializaría el body como XML.
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SatisfaccionRequest(cantidad))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Avisa que se reservaron unidades para una necesidad por matchmaking, antes de que se
     * reporte su entrega (docs/coherencia-necesidades_v1.md del repo de Donadores). Sin este
     * aviso, la necesidad sigue figurando como insatisfecha en obtenerNecesidadesInsatisfechasDe
     * y puede recibir más asignaciones para algo que ya está cubierto.
     */
    public void comprometerNecesidad(String necesidadID, Integer cantidad) {
        restClient.post()
                .uri("/necesidades/{necesidadID}/compromiso", necesidadID)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CompromisoRequest(cantidad))
                .retrieve()
                .toBodilessEntity();
    }

    record SatisfaccionRequest(Integer cantidad) {}
    record CompromisoRequest(Integer cantidad) {}
}