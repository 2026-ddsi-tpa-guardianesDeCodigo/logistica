package ar.edu.utn.dds.k3003.infra.logging;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Se registra en cada cliente HTTP saliente hacia otro componente (logging-spec_v1.md §3.4 y §6):
 * propaga el X-Correlation-Id y emite http.client.completed con peer.service.
 */
public class ClienteHttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final EventLogger LOG = EventLogger.of(ClienteHttpLoggingInterceptor.class);
    private final String peerService;

    public ClienteHttpLoggingInterceptor(String peerService) {
        this.peerService = peerService;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest req, byte[] body, ClientHttpRequestExecution ex) throws IOException {
        String cid = MDC.get(LogFields.TRACE_ID);
        if (cid != null) {
            req.getHeaders().add(CorrelationAndAccessLogFilter.HEADER, cid);
        }
        long t0 = System.currentTimeMillis();
        try {
            ClientHttpResponse res = ex.execute(req, body);
            int status = res.getStatusCode().value();
            EventLogger.Evento e = LOG.evento(EventoLog.HTTP_CLIENT_COMPLETED, "Llamada a componente")
                    .dato(LogFields.PEER, peerService)
                    .dato("http.request.method", req.getMethod().name())
                    .dato("url.path", req.getURI().getPath())
                    .dato("http.response.status_code", status)
                    .duracion(System.currentTimeMillis() - t0)
                    .outcome(status < 400 ? Outcome.SUCCESS : Outcome.FAILURE);
            if (status >= 400) {
                e.warn();
            }
            e.emitir();
            return res;
        } catch (IOException io) {
            LOG.evento(EventoLog.HTTP_CLIENT_COMPLETED, "Llamada a componente fallida")
                    .dato(LogFields.PEER, peerService)
                    .dato("http.request.method", req.getMethod().name())
                    .dato("url.path", req.getURI().getPath())
                    .duracion(System.currentTimeMillis() - t0)
                    .outcome(Outcome.FAILURE)
                    .warn()
                    .emitir();
            throw io;
        }
    }
}
