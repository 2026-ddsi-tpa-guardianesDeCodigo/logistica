package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class DonacionesClient {

    private final RestClient restClient;

    public DonacionesClient(@Value("${donaciones.url}") String baseUrl){
        // JdkClientHttpRequestFactory y no SimpleClientHttpRequestFactory: este cliente hace
        // PATCH, y el Simple va sobre HttpURLConnection, que no soporta ese metodo
        // ("Invalid HTTP method: PATCH") y hacia fallar toda entrega reportada.
        // Timeouts generosos por los cold starts de Render (hasta ~90s); sin esto, un
        // downstream realmente caido (no solo dormido) cuelga el request en vez de fallar rapido.
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(90));

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
