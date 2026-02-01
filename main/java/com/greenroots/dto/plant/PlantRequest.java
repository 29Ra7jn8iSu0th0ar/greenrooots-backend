package com.greenroots.dto.plant;

import com.greenroots.entity.Plant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlantRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Scientific name is required")
    private String scientificName;

    private String description;

    @NotNull(message = "Category is required")
    private Plant.Category category;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Positive(message = "Stock quantity must be positive")
    private Integer stockQuantity;

    @NotNull(message = "Light requirement is required")
    private Plant.LightRequirement lightRequirement;

    @NotNull(message = "Water requirement is required")
    private Plant.WaterRequirement waterRequirement;

    @NotBlank(message = "Image URL is required")
    private String imageUrl;
}
