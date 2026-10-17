package com.alphamind.investment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PersonalModelCatalog {
    private final PersonalModelDefinition definition;

    public PersonalModelCatalog(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("investment/personal-model-v1.1.json").getInputStream()) {
            definition = mapper.readValue(input, PersonalModelDefinition.class);
        }
    }
    public PersonalModelDefinition definition() { return definition; }
    public PersonalModelDefinition.Asset asset(String code) {
        return definition.assets().stream().filter(asset -> asset.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("该标的不在 v1.1 的 13 个观察标的内"));
    }
}
