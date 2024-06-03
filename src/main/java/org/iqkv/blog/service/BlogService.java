package org.iqkv.blog.service;

import org.iqkv.blog.service.dto.BlogDTO;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link org.iqkv.blog.domain.Blog}.
 */
public interface BlogService {
    /**
     * Save a blog.
     *
     * @param blogDTO the entity to save.
     * @return the persisted entity.
     */
    Mono<BlogDTO> save(BlogDTO blogDTO);

    /**
     * Updates a blog.
     *
     * @param blogDTO the entity to update.
     * @return the persisted entity.
     */
    Mono<BlogDTO> update(BlogDTO blogDTO);

    /**
     * Partially updates a blog.
     *
     * @param blogDTO the entity to update partially.
     * @return the persisted entity.
     */
    Mono<BlogDTO> partialUpdate(BlogDTO blogDTO);

    /**
     * Get all the blogs.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Flux<BlogDTO> findAll(Pageable pageable);

    /**
     * Get all the blogs with eager load of many-to-many relationships.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Flux<BlogDTO> findAllWithEagerRelationships(Pageable pageable);

    /**
     * Returns the number of blogs available.
     * @return the number of entities in the database.
     *
     */
    Mono<Long> countAll();

    /**
     * Returns the number of blogs available in search repository.
     *
     */
    Mono<Long> searchCount();

    /**
     * Get the "id" blog.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Mono<BlogDTO> findOne(Long id);

    /**
     * Delete the "id" blog.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    Mono<Void> delete(Long id);

    /**
     * Search for the blog corresponding to the query.
     *
     * @param query the query of the search.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Flux<BlogDTO> search(String query, Pageable pageable);
}
