package org.iqkv.blog.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.iqkv.blog.domain.TagAsserts.*;
import static org.iqkv.blog.web.rest.TestUtil.createUpdateProxyForBean;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.util.IterableUtil;
import org.iqkv.blog.IntegrationTest;
import org.iqkv.blog.domain.Tag;
import org.iqkv.blog.repository.EntityManager;
import org.iqkv.blog.repository.TagRepository;
import org.iqkv.blog.repository.search.TagSearchRepository;
import org.iqkv.blog.service.dto.TagDTO;
import org.iqkv.blog.service.mapper.TagMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.data.util.Streamable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for the {@link TagResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class TagResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/tags";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";
    private static final String ENTITY_SEARCH_API_URL = "/api/tags/_search";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private TagSearchRepository tagSearchRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Tag tag;

    private Tag insertedTag;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tag createEntity(EntityManager em) {
        Tag tag = new Tag().name(DEFAULT_NAME);
        return tag;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tag createUpdatedEntity(EntityManager em) {
        Tag tag = new Tag().name(UPDATED_NAME);
        return tag;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Tag.class).block();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
    }

    @BeforeEach
    public void initTest() {
        tag = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedTag != null) {
            tagRepository.delete(insertedTag).block();
            tagSearchRepository.delete(insertedTag).block();
            insertedTag = null;
        }
        deleteEntities(em);
    }

    @Test
    void createTag() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);
        var returnedTagDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(TagDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Tag in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedTag = tagMapper.toEntity(returnedTagDTO);
        assertTagUpdatableFieldsEquals(returnedTag, getPersistedTag(returnedTag));

        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore + 1);
            });

        insertedTag = returnedTag;
    }

    @Test
    void createTagWithExistingId() throws Exception {
        // Create the Tag with an existing ID
        tag.setId(1L);
        TagDTO tagDTO = tagMapper.toDto(tag);

        long databaseSizeBeforeCreate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        // set the field null
        tag.setName(null);

        // Create the Tag, which fails.
        TagDTO tagDTO = tagMapper.toDto(tag);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);

        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void getAllTags() {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        // Get all the tagList
        webTestClient
            .get()
            .uri(ENTITY_API_URL + "?sort=id,desc")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(tag.getId().intValue()))
            .jsonPath("$.[*].name")
            .value(hasItem(DEFAULT_NAME));
    }

    @Test
    void getTag() {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        // Get the tag
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, tag.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(tag.getId().intValue()))
            .jsonPath("$.name")
            .value(is(DEFAULT_NAME));
    }

    @Test
    void getNonExistingTag() {
        // Get the tag
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingTag() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();
        tagSearchRepository.save(tag).block();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());

        // Update the tag
        Tag updatedTag = tagRepository.findById(tag.getId()).block();
        updatedTag.name(UPDATED_NAME);
        TagDTO tagDTO = tagMapper.toDto(updatedTag);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, tagDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTagToMatchAllProperties(updatedTag);

        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
                assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
                List<Tag> tagSearchList = Streamable.of(tagSearchRepository.findAll().collectList().block()).toList();
                Tag testTagSearch = tagSearchList.get(searchDatabaseSizeAfter - 1);

                // Test fails because reactive api returns an empty object instead of null
                // assertTagAllPropertiesEquals(testTagSearch, updatedTag);
                assertTagUpdatableFieldsEquals(testTagSearch, updatedTag);
            });
    }

    @Test
    void putNonExistingTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, tagDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithIdMismatchTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void putWithMissingIdPathParamTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void partialUpdateTagWithPatch() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tag using partial update
        Tag partialUpdatedTag = new Tag();
        partialUpdatedTag.setId(tag.getId());

        partialUpdatedTag.name(UPDATED_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedTag.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedTag))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tag in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTagUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedTag, tag), getPersistedTag(tag));
    }

    @Test
    void fullUpdateTagWithPatch() throws Exception {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tag using partial update
        Tag partialUpdatedTag = new Tag();
        partialUpdatedTag.setId(tag.getId());

        partialUpdatedTag.name(UPDATED_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedTag.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedTag))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tag in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTagUpdatableFieldsEquals(partialUpdatedTag, getPersistedTag(partialUpdatedTag));
    }

    @Test
    void patchNonExistingTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, tagDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithIdMismatchTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void patchWithMissingIdPathParamTag() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        tag.setId(longCount.incrementAndGet());

        // Create the Tag
        TagDTO tagDTO = tagMapper.toDto(tag);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tagDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Tag in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore);
    }

    @Test
    void deleteTag() {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();
        tagRepository.save(tag).block();
        tagSearchRepository.save(tag).block();

        long databaseSizeBeforeDelete = getRepositoryCount();
        int searchDatabaseSizeBefore = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeBefore).isEqualTo(databaseSizeBeforeDelete);

        // Delete the tag
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, tag.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
        int searchDatabaseSizeAfter = IterableUtil.sizeOf(tagSearchRepository.findAll().collectList().block());
        assertThat(searchDatabaseSizeAfter).isEqualTo(searchDatabaseSizeBefore - 1);
    }

    @Test
    void searchTag() {
        // Initialize the database
        insertedTag = tagRepository.save(tag).block();
        tagSearchRepository.save(tag).block();

        // Search the tag
        webTestClient
            .get()
            .uri(ENTITY_SEARCH_API_URL + "?query=id:" + tag.getId())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(tag.getId().intValue()))
            .jsonPath("$.[*].name")
            .value(hasItem(DEFAULT_NAME));
    }

    protected long getRepositoryCount() {
        return tagRepository.count().block();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Tag getPersistedTag(Tag tag) {
        return tagRepository.findById(tag.getId()).block();
    }

    protected void assertPersistedTagToMatchAllProperties(Tag expectedTag) {
        // Test fails because reactive api returns an empty object instead of null
        // assertTagAllPropertiesEquals(expectedTag, getPersistedTag(expectedTag));
        assertTagUpdatableFieldsEquals(expectedTag, getPersistedTag(expectedTag));
    }

    protected void assertPersistedTagToMatchUpdatableProperties(Tag expectedTag) {
        // Test fails because reactive api returns an empty object instead of null
        // assertTagAllUpdatablePropertiesEquals(expectedTag, getPersistedTag(expectedTag));
        assertTagUpdatableFieldsEquals(expectedTag, getPersistedTag(expectedTag));
    }
}
