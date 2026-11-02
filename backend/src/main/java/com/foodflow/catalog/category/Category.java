package com.foodflow.catalog.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected Category() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
