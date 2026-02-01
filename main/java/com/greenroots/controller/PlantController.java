package com.greenroots.controller;

import com.greenroots.dto.plant.PlantDTO;
import com.greenroots.dto.plant.PlantRequest;
import com.greenroots.entity.Plant;
import com.greenroots.service.PlantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/plants")
@RequiredArgsConstructor
public class PlantController {

    private final PlantService plantService;

    @GetMapping
    public ResponseEntity<List<PlantDTO>> getAllPlants() {
        List<PlantDTO> plants = plantService.getAllPlants();
        return ResponseEntity.ok(plants);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlantDTO> getPlantById(@PathVariable Long id) {
        PlantDTO plant = plantService.getPlantById(id);
        return ResponseEntity.ok(plant);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<PlantDTO>> getPlantsByCategory(@PathVariable Plant.Category category) {
        List<PlantDTO> plants = plantService.getPlantsByCategory(category);
        return ResponseEntity.ok(plants);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlantDTO> createPlant(@Valid @RequestBody PlantRequest request) {
        PlantDTO plant = plantService.createPlant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(plant);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlantDTO> updatePlant(
            @PathVariable Long id,
            @Valid @RequestBody PlantRequest request) {
        PlantDTO plant = plantService.updatePlant(id, request);
        return ResponseEntity.ok(plant);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePlant(@PathVariable Long id) {
        plantService.deletePlant(id);
        return ResponseEntity.noContent().build();
    }
}
