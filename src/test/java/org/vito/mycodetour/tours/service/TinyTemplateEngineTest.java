package org.vito.mycodetour.tours.service;

import junit.framework.TestCase;
import org.junit.Assert;

import java.util.Map;

/**
 * @author vito
 * @since 11.0
 * Created on 2025/5/21
 */
public class TinyTemplateEngineTest extends TestCase {

    public void testRender() {
        String template = "Hello, ${name}! Your age is ${age}. Missing: ${unknown}";
        Map<String, String> data = Map.of(
                "name", "Alice",
                "age", "30"
        );

        String result = TinyTemplateEngine.renderHtml(template, data);
        Assert.assertEquals("Hello, Alice! Your age is 30. Missing: ", result);
    }
}
