package com.sicost.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.InputValidator;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.clients.ClientDtos.*;

@RestController
public class ClientController {
    private final ClientService service;
    public ClientController(ClientService service){this.service=service;}
    @GetMapping("/api/clients")
    public ApiResponse<Page> getClients(@RequestParam Map<String,String> query){return ApiResponse.ok(service.list(ClientInput.query(query)));}
    @GetMapping("/api/clients/{id}")
    public ApiResponse<Client> getClient(@PathVariable String id,@RequestParam Map<String,String> query){
        ClientInput.allowQuery(query,Set.of());InputValidator.text(id,30,"id");return ApiResponse.ok(service.get(id));
    }
    @PostMapping("/api/clients")
    public ResponseEntity<ApiResponse<Result>> createClient(@RequestBody JsonNode body,@RequestParam Map<String,String> query){
        ClientInput.allowQuery(query,Set.of());return ResponseEntity.status(201).body(ApiResponse.ok(service.create(ClientInput.parse(body,false))));
    }
    @PutMapping("/api/clients/{id}")
    public ApiResponse<Result> putClient(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){
        ClientInput.allowQuery(query,Set.of());InputValidator.text(id,30,"id");return ApiResponse.ok(service.update(id,ClientInput.parse(body,true)));
    }
}
