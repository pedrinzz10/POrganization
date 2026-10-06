package com.porganization.settings;

import com.porganization.security.CurrentUser;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Apagar os dados de uma seção: commitments, tasks, studies ou finance (B14). */
@RestController
@RequestMapping("/api/data")
public class DataResetController {

    private final DataResetService service;

    public DataResetController(DataResetService service) {
        this.service = service;
    }

    @DeleteMapping("/{section}")
    public ResponseEntity<Void> reset(@CurrentUser UUID userId, @PathVariable String section) {
        service.reset(userId, section);
        return ResponseEntity.noContent().build();
    }
}
