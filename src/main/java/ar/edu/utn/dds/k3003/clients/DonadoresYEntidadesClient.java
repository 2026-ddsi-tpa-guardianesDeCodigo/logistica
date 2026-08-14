package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import org.springframework.beans.factory.annotation.Value;
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
                .body(new SatisfaccionRequest(cantidad))
                .retrieve()
                .toBodilessEntity();
    }

    record SatisfaccionRequest(Integer cantidad) {}
}