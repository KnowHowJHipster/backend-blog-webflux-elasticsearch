package org.iqkv.blog.service.mapper;

import static org.iqkv.blog.domain.BlogAsserts.*;
import static org.iqkv.blog.domain.BlogTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlogMapperTest {

    private BlogMapper blogMapper;

    @BeforeEach
    void setUp() {
        blogMapper = new BlogMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getBlogSample1();
        var actual = blogMapper.toEntity(blogMapper.toDto(expected));
        assertBlogAllPropertiesEquals(expected, actual);
    }
}
