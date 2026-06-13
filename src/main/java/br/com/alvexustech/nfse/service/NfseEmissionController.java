package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.dto.ConsultarStatusResponse;
import br.com.alvexustech.nfse.dto.EmitirNfseRequest;
import br.com.alvexustech.nfse.dto.EmitirNfseResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nfse/emissoes")
public class NfseEmissionController {

    private final NfseEmissionService service;

    public NfseEmissionController(NfseEmissionService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ResponseEntity<EmitirNfseResponse>> emit(@Valid @RequestBody EmitirNfseRequest request) {
        return service.emit(request).map(response -> ResponseEntity.accepted().body(response));
    }

    @GetMapping("/{id}/status")
    public Mono<ResponseEntity<ConsultarStatusResponse>> status(@PathVariable UUID id) {
        return service.consultStatus(id).map(ResponseEntity::ok);
    }
}
