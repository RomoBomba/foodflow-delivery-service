package com.foodflow;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.data.repository.Repository;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleBoundariesTest {
    @Test
    void componentsUseOnlyRepositoriesOfTheirOwnModule() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        for (var definition : scanner.findCandidateComponents("com.foodflow")) {
            Class<?> component = Class.forName(definition.getBeanClassName());
            for (var field : component.getDeclaredFields()) {
                if (Repository.class.isAssignableFrom(field.getType())) {
                    assertThat(module(field.getType())).as("%s.%s must access another module through a service",
                            component.getSimpleName(), field.getName()).isEqualTo(module(component));
                }
            }
        }
    }

    private String module(Class<?> type) { return type.getPackageName().split("\\.")[2]; }
}
