package com.greenroots.service;

import com.greenroots.dto.plant.PlantDTO;
import com.greenroots.dto.plant.PlantRequest;
import com.greenroots.entity.Plant;
import com.greenroots.exception.ResourceNotFoundException;
import com.greenroots.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlantService {

    private final PlantRepository plantRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "plants", key = "'all'")
    public List<PlantDTO> getAllPlants() {
        return plantRepository.findByActiveTrue().stream()
                .map(PlantDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "plants", key = "#id")
    public PlantDTO getPlantById(Long id) {
        Plant plant = plantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plant not found with id: " + id));
        return PlantDTO.fromEntity(plant);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "plants", key = "'category_' + #category")
    public List<PlantDTO> getPlantsByCategory(Plant.Category category) {
        return plantRepository.findActivePlantsByCategory(category).stream()
                .map(PlantDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "plants", allEntries = true)
    public PlantDTO createPlant(PlantRequest request) {
        Plant plant = Plant.builder()
                .name(request.getName())
                .scientificName(request.getScientificName())
                .description(request.getDescription())
                .category(request.getCategory())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .lightRequirement(request.getLightRequirement())
                .waterRequirement(request.getWaterRequirement())
                .imageUrl(request.getImageUrl())
                .active(true)
                .build();

        plant = plantRepository.save(plant);
        log.info("Plant created successfully: {}", plant.getName());
        return PlantDTO.fromEntity(plant);
    }

    @Transactional
    @CacheEvict(value = "plants", allEntries = true)
    public PlantDTO updatePlant(Long id, PlantRequest request) {
        Plant plant = plantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plant not found with id: " + id));

        plant.setName(request.getName());
        plant.setScientificName(request.getScientificName());
        plant.setDescription(request.getDescription());
        plant.setCategory(request.getCategory());
        plant.setPrice(request.getPrice());
        plant.setStockQuantity(request.getStockQuantity());
        plant.setLightRequirement(request.getLightRequirement());
        plant.setWaterRequirement(request.getWaterRequirement());
        plant.setImageUrl(request.getImageUrl());

        plant = plantRepository.save(plant);
        log.info("Plant updated successfully: {}", plant.getName());
        return PlantDTO.fromEntity(plant);
    }

    @Transactional
    @CacheEvict(value = "plants", allEntries = true)
    public void deletePlant(Long id) {
        Plant plant = plantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plant not found with id: " + id));
        plant.setActive(false);
        plantRepository.save(plant);
        log.info("Plant soft deleted: {}", plant.getName());
    }
}
