package org.iqkv.blog.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.iqkv.blog.IntegrationTest;
import org.springframework.test.context.web.WebAppConfiguration;

@CucumberContextConfiguration
@IntegrationTest
@WebAppConfiguration
public class CucumberTestContextConfiguration {}
