package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;

@Component
public class DonadoresYEntidadesClient {

    private final RestClient restClient;

    public DonadoresYEntidadesClient() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
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