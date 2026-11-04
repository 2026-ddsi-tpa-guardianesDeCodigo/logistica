package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.eventos.RepublicadorEventos;
import ar.edu.utn.dds.k3003.infra.logging.EventLogger;
import ar.edu.utn.dds.k3003.infra.logging.EventoLog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminEventosController {
    private static final EventLogger LOG = EventLogger.of(AdminEventosController.class);

    private final RepublicadorEventos republicador;

    public AdminEventosController(RepublicadorEventos republicador) {
        this.republicador = republicador;
    }

    /** Reenvía el snapshot de todo lo que publica este módulo al modelo de lectura (E6). */
    @PostMapping("/admin/republicar-eventos")
    public ResponseEntity<Map<String, Integer>> republicarEventos() {
        int publicados = republicador.republicarTodo();
        LOG.evento(EventoLog.EVENTOS_REPUBLICADOS, "Eventos republicados").dato("cantidad", publicados).emitir();
        return ResponseEntity.ok(Map.of("publicados", publicados));
    }
}
