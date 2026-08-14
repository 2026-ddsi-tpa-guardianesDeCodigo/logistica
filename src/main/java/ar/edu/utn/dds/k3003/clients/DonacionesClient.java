package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DonacionesClient {

    private final RestClient restClient;

    public DonacionesClient(@Value("${donaciones.url}") String baseUrl){
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