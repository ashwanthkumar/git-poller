package com.tw.go.plugin;

import com.google.gson.Gson;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GitPluginImplTest {
    public static final String TEST_DIR = "/tmp/" + UUID.randomUUID();

    @Before
    public void setUp() {
        FileUtils.deleteQuietly(new File(TEST_DIR));
    }

    @After
    public void tearDown() {
        FileUtils.deleteQuietly(new File(TEST_DIR));
    }

    @Test
    public void shouldHandleInvalidURLCorrectly_ValidationRequest() {
        Map request = createRequestMap(Arrays.asList(new Pair("url", "crap")));

        GoPluginApiResponse response = new GitPluginImpl().handle(createGoPluginApiRequest(GitPluginImpl.REQUEST_VALIDATE_SCM_CONFIGURATION, request));

        verifyResponse(response.responseBody(), Arrays.asList(new Pair("url", "Invalid URL format")));
    }

    @Test
    public void shouldHandleValidURLCorrectly_ValidationRequest() throws IOException {
        verifyValidationSuccess("http://crap.com/git-repo");
        verifyValidationSuccess("https://test.co");
        verifyValidationSuccess("ftp://test.fake.org/git-repo?test=true");
        verifyValidationSuccess("http://localhost:8080/git-repo");
        FileUtils.forceMkdir(new File(TEST_DIR));
        verifyValidationSuccess(TEST_DIR);
    }

    private void verifyValidationSuccess(String url) {
        Map request = createRequestMap(Arrays.asList(new Pair("url", url)));

        GoPluginApiResponse response = new GitPluginImpl().handle(createGoPluginApiRequest(GitPluginImpl.REQUEST_VALIDATE_SCM_CONFIGURATION, request));

        verifyResponse(response.responseBody(), null);
    }

    private Map createRequestMap(List<Pair> pairs) {
        final Map request = new HashMap();
        Map scmConfiguration = new HashMap();

        for (Pair pair : pairs) {
            Map valueMap = new HashMap();
            valueMap.put("value", pair.value);
            scmConfiguration.put(pair.key, valueMap);
        }

        request.put("scm-configuration", scmConfiguration);
        return request;
    }

    private void verifyResponse(String responseBody, List<Pair> pairs) {
        List response = new Gson().fromJson(responseBody, List.class);
        if (pairs == null) {
            assertThat(response.size(), is(0));
        } else {
            for (int i = 0; i < pairs.size(); i++) {
                assertThat((String) ((Map) response.get(i)).get("key"), is(pairs.get(i).key));
                assertThat((String) ((Map) response.get(i)).get("message"), is(pairs.get(i).value));
            }
        }
    }

    class Pair {
        String key;
        String value;

        public Pair(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private GoPluginApiRequest createGoPluginApiRequest(final String requestName, final Map request) {
        return new GoPluginApiRequest() {
            @Override
            public String extension() {
                return null;
            }

            @Override
            public String extensionVersion() {
                return null;
            }

            @Override
            public String requestName() {
                return requestName;
            }

            @Override
            public Map<String, String> requestParameters() {
                return null;
            }

            @Override
            public Map<String, String> requestHeaders() {
                return null;
            }

            @Override
            public String requestBody() {
                return new Gson().toJson(request);
            }
        };
    }
}