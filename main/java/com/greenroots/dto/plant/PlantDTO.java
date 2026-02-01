package com.greenroots.dto.plant;

import com.greenroots.entity.Plant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantDTO {
    private Long id;
    private String name;
    private String scientificName;
    private String description;
    private String category;
    private BigDecimal price;
    private Integer stockQuantity;
    private String lightRequirement;
    private String waterRequirement;
    private String imageUrl;
    private Boolean active;
    private LocalDateTime createdAt;

    public static PlantDTO fromEntity(Plant plant) {
        return PlantDTO.builder()
                .id(plant.getId())
                .name(plant.getName())
                .scientificName(plant.getScientificName())
                .description(plant.getDescription())
                .category(plant.getCategory().name())
                .price(plant.getPrice())
                .stockQuantity(plant.getStockQuantity())
                .lightRequirement(plant.getLightRequirement().name())
                .waterRequirement(plant.getWaterRequirement().name())
                .imageUrl(plant.getImageUrl())
                .active(plant.getActive())
                .createdAt(plant.getCreatedAt())
                .build();
    }
}
