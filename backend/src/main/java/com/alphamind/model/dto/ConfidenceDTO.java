package com.alphamind.model.dto;

import com.alphamind.model.enums.ConfidenceLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfidenceDTO {
    private Double value;
    private ConfidenceLevel level;
    private String explanation;
}
