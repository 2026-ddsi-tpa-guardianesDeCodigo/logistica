package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.clients.DonacionesClient;
import ar.edu.utn.dds.k3003.clients.DonadoresYEntidadesClient;
import ar.edu.utn.dds.k3003.model.Asignacion;
import ar.edu.utn.dds.k3003.model.Paquete;
import ar.edu.utn.dds.k3003.repositories.LogisticaRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * A nivel HTTP real (servidor embebido con puerto random), no a nivel service, porque
 * GlobalExceptionHandler solo intercepta excepciones que suben desde un @RestController.
 * Reproduce lo que se vio en vivo contra Render: un donacionID que Donaciones rechaza durante
 * reportarEntrega terminaba en un 500 sin mensaje util.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LogisticaRepository logisticaRepository;

    @MockitoBean
    private DonadoresYEntidadesClient donadoresYEntidadesClient;

    @MockitoBean
    private DonacionesClient donacionesClient;

    private String paqueteID;

    @BeforeEach
    void setUp() {
        logisticaRepository.limpiarAsignaciones();
        logisticaRepository.limpiarDepositos();
        // reportarEntrega ahora lee donacionID/cantidad del Paquete persistido, no del body
        // (ver A4 en CONTEXTO_E5.md): hace falta un Paquete real, no solo un id inventado.
        Paquete paquete = logisticaRepository.guardarPaquete(new Paquete("donInexistente", "prodQA", 5));
        paqueteID = String.valueOf(paquete.getId());
        Asignacion asignacion = new Asignacion(
                paqueteID, "necGatewayQA", LocalDateTime.now(),
                EstadoAsginacionEnum.ASIGNADA, OrigenAsignacionEnum.MATCHMAKING);
        logisticaRepository.guardarAsignacion(asignacion);
    }

    @Test
    void reportarEntrega_donacionesRechazaElCambioDeEstado_devuelve502ConMensajeClaro() {
        doThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", null,
                        "donacion inexistente".getBytes(), null))
                .when(donacionesClient)
                .cambiarEstadoDeDonacion(any(), any());

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/entregas", new PaqueteDTO(paqueteID, "donInexistente", "prodQA", 5), String.class);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertTrue(response.getBody().contains("donacion inexistente"));
    }
}
