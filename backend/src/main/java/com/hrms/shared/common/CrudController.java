package com.hrms.shared.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

/** CRUD controller for resources that allow updating existing records. */
public abstract class CrudController<T extends BaseEntity> extends CrudResourceController<T> {

    protected CrudController(JpaRepository<T, Long> repo) {
        super(repo);
    }

    @PutMapping("/{id}")
    public T update(@PathVariable Long id, @Valid @RequestBody T body) {
        T existing = load(id);
        body.setId(id);
        body.setCreatedAt(existing.getCreatedAt());
        body.setCreatedBy(existing.getCreatedBy());
        T saved = doUpdate(existing, body);
        flushMutation();
        return saved;
    }

    protected T doUpdate(T existing, T update) {
        return repo.save(update);
    }
}
