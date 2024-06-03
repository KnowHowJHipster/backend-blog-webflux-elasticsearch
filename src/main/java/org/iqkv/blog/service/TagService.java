package org.iqkv.blog.service;

import org.iqkv.blog.repository.TagRepository;
import org.iqkv.blog.repository.search.TagSearchRepository;
import org.iqkv.blog.service.dto.TagDTO;
import org.iqkv.blog.service.mapper.TagMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Implementation for managing {@link org.iqkv.blog.domain.Tag}.
 */
@Service
@Transactional
public class TagService {

    private final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;

    private final TagMapper tagMapper;

    private final TagSearchRepository tagSearchRepository;

    public TagService(TagRepository tagRepository, TagMapper tagMapper, TagSearchRepository tagSearchRepository) {
        this.tagRepository = tagRepository;
        this.tagMapper = tagMapper;
        this.tagSearchRepository = tagSearchRepository;
    }

    /**
     * Save a tag.
     *
     * @param tagDTO the entity to save.
     * @return the persisted entity.
     */
    public Mono<TagDTO> save(TagDTO tagDTO) {
        log.debug("Request to save Tag : {}", tagDTO);
        return tagRepository.save(tagMapper.toEntity(tagDTO)).flatMap(tagSearchRepository::save).map(tagMapper::toDto);
    }

    /**
     * Update a tag.
     *
     * @param tagDTO the entity to save.
     * @return the persisted entity.
     */
    public Mono<TagDTO> update(TagDTO tagDTO) {
        log.debug("Request to update Tag : {}", tagDTO);
        return tagRepository.save(tagMapper.toEntity(tagDTO)).flatMap(tagSearchRepository::save).map(tagMapper::toDto);
    }

    /**
     * Partially update a tag.
     *
     * @param tagDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Mono<TagDTO> partialUpdate(TagDTO tagDTO) {
        log.debug("Request to partially update Tag : {}", tagDTO);

        return tagRepository
            .findById(tagDTO.getId())
            .map(existingTag -> {
                tagMapper.partialUpdate(existingTag, tagDTO);

                return existingTag;
            })
            .flatMap(tagRepository::save)
            .flatMap(savedTag -> {
                tagSearchRepository.save(savedTag);
                return Mono.just(savedTag);
            })
            .map(tagMapper::toDto);
    }

    /**
     * Get all the tags.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Flux<TagDTO> findAll(Pageable pageable) {
        log.debug("Request to get all Tags");
        return tagRepository.findAllBy(pageable).map(tagMapper::toDto);
    }

    /**
     * Returns the number of tags available.
     * @return the number of entities in the database.
     *
     */
    public Mono<Long> countAll() {
        return tagRepository.count();
    }

    /**
     * Returns the number of tags available in search repository.
     *
     */
    public Mono<Long> searchCount() {
        return tagSearchRepository.count();
    }

    /**
     * Get one tag by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Mono<TagDTO> findOne(Long id) {
        log.debug("Request to get Tag : {}", id);
        return tagRepository.findById(id).map(tagMapper::toDto);
    }

    /**
     * Delete the tag by id.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    public Mono<Void> delete(Long id) {
        log.debug("Request to delete Tag : {}", id);
        return tagRepository.deleteById(id).then(tagSearchRepository.deleteById(id));
    }

    /**
     * Search for the tag corresponding to the query.
     *
     * @param query the query of the search.
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Flux<TagDTO> search(String query, Pageable pageable) {
        log.debug("Request to search for a page of Tags for query {}", query);
        return tagSearchRepository.search(query, pageable).map(tagMapper::toDto);
    }
}
