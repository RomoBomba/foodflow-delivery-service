package com.foodflow.catalog.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return repository.findAllByOrderByNameAsc()
                .stream()
                .map(category -> new CategoryResponse(
                        category.getId(),
                        category.getName()
                ))
                .toList();
    }
}
