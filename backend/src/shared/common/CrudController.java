package shared.common;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

/**
 * List / get / create / update / delete for one entity.
 * Subclasses change behaviour by overriding the do* hooks, not the mapped methods.
 */
public abstract class CrudController<T extends BaseEntity> {

    protected final JpaRepository<T, Long> repo;

    protected CrudController(JpaRepository<T, Long> repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<T> list() {
        return findAll();
    }

    @GetMapping("/{id}")
    public T get(@PathVariable Long id) {
        return load(id);
    }

    @PostMapping
    public T create(@Valid @RequestBody T body) {
        body.setId(null);
        return doCreate(body);
    }

    @PutMapping("/{id}")
    public T update(@PathVariable Long id, @Valid @RequestBody T body) {
        T existing = load(id);
        body.setId(id);
        body.setCreatedAt(existing.getCreatedAt());
        body.setCreatedBy(existing.getCreatedBy());
        return doUpdate(existing, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        doDelete(load(id));
    }

    protected T load(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Record " + id + " not found"));
    }

    protected List<T> findAll() {
        return repo.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    protected T doCreate(T entity) {
        return repo.save(entity);
    }

    protected T doUpdate(T existing, T update) {
        return repo.save(update);
    }

    protected void doDelete(T entity) {
        repo.delete(entity);
    }
}
