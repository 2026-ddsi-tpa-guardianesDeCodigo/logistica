package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DonacionesClient {

    private final RestClient restClient;

    public DonacionesClient() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8082")
                .build();
    }

    public void cambiarEstadoDeDonacion(String donacionID, EstadoDonacionEnum estado) {
        restClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/donaciones/estado")
                        .queryParam("donacionID", donacionID)
                        .build())
                .body(estado)
                .retrieve()
                .toBodilessEntity();
    }
}