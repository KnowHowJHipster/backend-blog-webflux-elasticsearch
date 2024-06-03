package org.iqkv.blog.service.impl;

import org.iqkv.blog.repository.BlogRepository;
import org.iqkv.blog.repository.search.BlogSearchRepository;
import org.iqkv.blog.service.BlogService;
import org.iqkv.blog.service.dto.BlogDTO;
import org.iqkv.blog.service.mapper.BlogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Implementation for managing {@link org.iqkv.blog.domain.Blog}.
 */
@Service
@Transactional
public class BlogServiceImpl implements BlogService {

    private final Logger log = LoggerFactory.getLogger(BlogServiceImpl.class);

    private final BlogRepository blogRepository;

    private final BlogMapper blogMapper;

    private final BlogSearchRepository blogSearchRepository;

    public BlogServiceImpl(BlogRepository blogRepository, BlogMapper blogMapper, BlogSearchRepository blogSearchRepository) {
        this.blogRepository = blogRepository;
        this.blogMapper = blogMapper;
        this.blogSearchRepository = blogSearchRepository;
    }

    @Override
    public Mono<BlogDTO> save(BlogDTO blogDTO) {
        log.debug("Request to save Blog : {}", blogDTO);
        return blogRepository.save(blogMapper.toEntity(blogDTO)).flatMap(blogSearchRepository::save).map(blogMapper::toDto);
    }

    @Override
    public Mono<BlogDTO> update(BlogDTO blogDTO) {
        log.debug("Request to update Blog : {}", blogDTO);
        return blogRepository.save(blogMapper.toEntity(blogDTO)).flatMap(blogSearchRepository::save).map(blogMapper::toDto);
    }

    @Override
    public Mono<BlogDTO> partialUpdate(BlogDTO blogDTO) {
        log.debug("Request to partially update Blog : {}", blogDTO);

        return blogRepository
            .findById(blogDTO.getId())
            .map(existingBlog -> {
                blogMapper.partialUpdate(existingBlog, blogDTO);

                return existingBlog;
            })
            .flatMap(blogRepository::save)
            .flatMap(savedBlog -> {
                blogSearchRepository.save(savedBlog);
                return Mono.just(savedBlog);
            })
            .map(blogMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<BlogDTO> findAll(Pageable pageable) {
        log.debug("Request to get all Blogs");
        return blogRepository.findAllBy(pageable).map(blogMapper::toDto);
    }

    public Flux<BlogDTO> findAllWithEagerRelationships(Pageable pageable) {
        return blogRepository.findAllWithEagerRelationships(pageable).map(blogMapper::toDto);
    }

    public Mono<Long> countAll() {
        return blogRepository.count();
    }

    public Mono<Long> searchCount() {
        return blogSearchRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<BlogDTO> findOne(Long id) {
        log.debug("Request to get Blog : {}", id);
        return blogRepository.findOneWithEagerRelationships(id).map(blogMapper::toDto);
    }

    @Override
    public Mono<Void> delete(Long id) {
        log.debug("Request to delete Blog : {}", id);
        return blogRepository.deleteById(id).then(blogSearchRepository.deleteById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<BlogDTO> search(String query, Pageable pageable) {
        log.debug("Request to search for a page of Blogs for query {}", query);
        return blogSearchRepository.search(query, pageable).map(blogMapper::toDto);
    }
}
