package org.iqkv.blog.service;

import org.iqkv.blog.repository.PostRepository;
import org.iqkv.blog.repository.search.PostSearchRepository;
import org.iqkv.blog.service.dto.PostDTO;
import org.iqkv.blog.service.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Implementation for managing {@link org.iqkv.blog.domain.Post}.
 */
@Service
@Transactional
public class PostService {

    private final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;

    private final PostMapper postMapper;

    private final PostSearchRepository postSearchRepository;

    public PostService(PostRepository postRepository, PostMapper postMapper, PostSearchRepository postSearchRepository) {
        this.postRepository = postRepository;
        this.postMapper = postMapper;
        this.postSearchRepository = postSearchRepository;
    }

    /**
     * Save a post.
     *
     * @param postDTO the entity to save.
     * @return the persisted entity.
     */
    public Mono<PostDTO> save(PostDTO postDTO) {
        log.debug("Request to save Post : {}", postDTO);
        return postRepository.save(postMapper.toEntity(postDTO)).flatMap(postSearchRepository::save).map(postMapper::toDto);
    }

    /**
     * Update a post.
     *
     * @param postDTO the entity to save.
     * @return the persisted entity.
     */
    public Mono<PostDTO> update(PostDTO postDTO) {
        log.debug("Request to update Post : {}", postDTO);
        return postRepository.save(postMapper.toEntity(postDTO)).flatMap(postSearchRepository::save).map(postMapper::toDto);
    }

    /**
     * Partially update a post.
     *
     * @param postDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Mono<PostDTO> partialUpdate(PostDTO postDTO) {
        log.debug("Request to partially update Post : {}", postDTO);

        return postRepository
            .findById(postDTO.getId())
            .map(existingPost -> {
                postMapper.partialUpdate(existingPost, postDTO);

                return existingPost;
            })
            .flatMap(postRepository::save)
            .flatMap(savedPost -> {
                postSearchRepository.save(savedPost);
                return Mono.just(savedPost);
            })
            .map(postMapper::toDto);
    }

    /**
     * Get all the posts.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Flux<PostDTO> findAll(Pageable pageable) {
        log.debug("Request to get all Posts");
        return postRepository.findAllBy(pageable).map(postMapper::toDto);
    }

    /**
     * Get all the posts with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Flux<PostDTO> findAllWithEagerRelationships(Pageable pageable) {
        return postRepository.findAllWithEagerRelationships(pageable).map(postMapper::toDto);
    }

    /**
     * Returns the number of posts available.
     * @return the number of entities in the database.
     *
     */
    public Mono<Long> countAll() {
        return postRepository.count();
    }

    /**
     * Returns the number of posts available in search repository.
     *
     */
    public Mono<Long> searchCount() {
        return postSearchRepository.count();
    }

    /**
     * Get one post by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Mono<PostDTO> findOne(Long id) {
        log.debug("Request to get Post : {}", id);
        return postRepository.findOneWithEagerRelationships(id).map(postMapper::toDto);
    }

    /**
     * Delete the post by id.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    public Mono<Void> delete(Long id) {
        log.debug("Request to delete Post : {}", id);
        return postRepository.deleteById(id).then(postSearchRepository.deleteById(id));
    }

    /**
     * Search for the post corresponding to the query.
     *
     * @param query the query of the search.
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Flux<PostDTO> search(String query, Pageable pageable) {
        log.debug("Request to search for a page of Posts for query {}", query);
        return postSearchRepository.search(query, pageable).map(postMapper::toDto);
    }
}
