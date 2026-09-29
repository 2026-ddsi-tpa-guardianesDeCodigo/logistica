package ar.edu.utn.dds.k3003.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlación entre componentes (logging-spec_v1.md §6) + access log (§5.1 http.request.completed).
 * Pone el trace.id en el MDC y lo limpia siempre en el finally, para que el thread del pool
 * no arrastre el ID de un request anterior.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationAndAccessLogFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final EventLogger LOG = EventLogger.of(CorrelationAndAccessLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String cid = req.getHeader(HEADER);
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString();
        }
        MDC.put(LogFields.TRACE_ID, cid);
        res.setHeader(HEADER, cid);
        long t0 = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            int status = res.getStatus();
            EventLogger.Evento e = LOG.evento(EventoLog.HTTP_REQUEST_COMPLETED, "Request atendido")
                    .dato("http.request.method", req.getMethod())
                    .dato("url.path", req.getRequestURI())
                    .dato("http.response.status_code", status)
                    .duracion(System.currentTimeMillis() - t0)
                    .outcome(status < 400 ? Outcome.SUCCESS : Outcome.FAILURE);
            if (status >= 500) {
                e.error(null);
            } else if (status >= 400) {
                e.warn();
            }
            e.emitir();
            MDC.clear();
        }
    }
}
