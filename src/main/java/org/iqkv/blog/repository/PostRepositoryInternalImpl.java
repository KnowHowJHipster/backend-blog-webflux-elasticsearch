package org.iqkv.blog.repository;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.List;
import org.iqkv.blog.domain.Post;
import org.iqkv.blog.domain.Tag;
import org.iqkv.blog.repository.rowmapper.BlogRowMapper;
import org.iqkv.blog.repository.rowmapper.PostRowMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.SimpleR2dbcRepository;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Comparison;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoinCondition;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.repository.support.MappingRelationalEntityInformation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC custom repository implementation for the Post entity.
 */
@SuppressWarnings("unused")
class PostRepositoryInternalImpl extends SimpleR2dbcRepository<Post, Long> implements PostRepositoryInternal {

    private final DatabaseClient db;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final EntityManager entityManager;

    private final BlogRowMapper blogMapper;
    private final PostRowMapper postMapper;

    private static final Table entityTable = Table.aliased("post", EntityManager.ENTITY_ALIAS);
    private static final Table blogTable = Table.aliased("blog", "blog");

    private static final EntityManager.LinkTable tagLink = new EntityManager.LinkTable("rel_post__tag", "post_id", "tag_id");

    public PostRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        BlogRowMapper blogMapper,
        PostRowMapper postMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter
    ) {
        super(
            new MappingRelationalEntityInformation(converter.getMappingContext().getRequiredPersistentEntity(Post.class)),
            entityOperations,
            converter
        );
        this.db = template.getDatabaseClient();
        this.r2dbcEntityTemplate = template;
        this.entityManager = entityManager;
        this.blogMapper = blogMapper;
        this.postMapper = postMapper;
    }

    @Override
    public Flux<Post> findAllBy(Pageable pageable) {
        return createQuery(pageable, null).all();
    }

    RowsFetchSpec<Post> createQuery(Pageable pageable, Condition whereClause) {
        List<Expression> columns = PostSqlHelper.getColumns(entityTable, EntityManager.ENTITY_ALIAS);
        columns.addAll(BlogSqlHelper.getColumns(blogTable, "blog"));
        SelectFromAndJoinCondition selectFrom = Select.builder()
            .select(columns)
            .from(entityTable)
            .leftOuterJoin(blogTable)
            .on(Column.create("blog_id", entityTable))
            .equals(Column.create("id", blogTable));
        // we do not support Criteria here for now as of https://github.com/jhipster/generator-jhipster/issues/18269
        String select = entityManager.createSelect(selectFrom, Post.class, pageable, whereClause);
        return db.sql(select).map(this::process);
    }

    @Override
    public Flux<Post> findAll() {
        return findAllBy(null);
    }

    @Override
    public Mono<Post> findById(Long id) {
        Comparison whereClause = Conditions.isEqual(entityTable.column("id"), Conditions.just(id.toString()));
        return createQuery(null, whereClause).one();
    }

    @Override
    public Mono<Post> findOneWithEagerRelationships(Long id) {
        return findById(id);
    }

    @Override
    public Flux<Post> findAllWithEagerRelationships() {
        return findAll();
    }

    @Override
    public Flux<Post> findAllWithEagerRelationships(Pageable page) {
        return findAllBy(page);
    }

    private Post process(Row row, RowMetadata metadata) {
        Post entity = postMapper.apply(row, "e");
        entity.setBlog(blogMapper.apply(row, "blog"));
        return entity;
    }

    @Override
    public <S extends Post> Mono<S> save(S entity) {
        return super.save(entity).flatMap((S e) -> updateRelations(e));
    }

    protected <S extends Post> Mono<S> updateRelations(S entity) {
        Mono<Void> result = entityManager.updateLinkTable(tagLink, entity.getId(), entity.getTags().stream().map(Tag::getId)).then();
        return result.thenReturn(entity);
    }

    @Override
    public Mono<Void> deleteById(Long entityId) {
        return deleteRelations(entityId).then(super.deleteById(entityId));
    }

    protected Mono<Void> deleteRelations(Long entityId) {
        return entityManager.deleteFromLinkTable(tagLink, entityId);
    }
}
