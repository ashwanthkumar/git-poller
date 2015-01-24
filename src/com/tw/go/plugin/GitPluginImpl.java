package com.tw.go.plugin;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.validator.routines.UrlValidator;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.util.Arrays.asList;

@Extension
public class GitPluginImpl implements GoPlugin {
    private static Logger LOGGER = Logger.getLoggerFor(GitPluginImpl.class);

    public static final String EXTENSION_NAME = "scm";
    private static final List<String> goSupportedVersions = asList("1.0");

    public static final String REQUEST_SCM_CONFIGURATION = "scm-configuration";
    public static final String REQUEST_SCM_VIEW = "scm-view";
    public static final String REQUEST_VALIDATE_SCM_CONFIGURATION = "validate-scm-configuration";
    public static final String REQUEST_CHECK_SCM_CONNECTION = "check-scm-connection";
    public static final String REQUEST_LATEST_REVISION = "latest-revision";
    public static final String REQUEST_LATEST_REVISIONS_SINCE = "latest-revisions-since";
    public static final String REQUEST_CHECKOUT = "checkout";

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final int SUCCESS_RESPONSE_CODE = 200;
    public static final int INTERNAL_ERROR_RESPONSE_CODE = 500;

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        // ignore
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        if (goPluginApiRequest.requestName().equals(REQUEST_SCM_CONFIGURATION)) {
            return handleSCMConfiguration();
        } else if (goPluginApiRequest.requestName().equals(REQUEST_SCM_VIEW)) {
            try {
                return handleSCMView();
            } catch (IOException e) {
                String message = "Failed to find template: " + e.getMessage();
                return renderJSON(500, message);
            }
        } else if (goPluginApiRequest.requestName().equals(REQUEST_VALIDATE_SCM_CONFIGURATION)) {
            return handleSCMValidation(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_CHECK_SCM_CONNECTION)) {
            return handleSCMCheckConnection(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_LATEST_REVISION)) {
            return handleGetLatestRevision(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_LATEST_REVISIONS_SINCE)) {
            return handleLatestRevisionSince(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_CHECKOUT)) {
            return handleCheckout(goPluginApiRequest);
        }
        return null;
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_NAME, goSupportedVersions);
    }

    private GoPluginApiResponse handleSCMConfiguration() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("url", createField("URL", null, true, true, false, "0"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleSCMView() throws IOException {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("displayValue", "JGit");
        response.put("template", IOUtils.toString(getClass().getResourceAsStream("/views/scm.template.html"), "UTF-8"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleSCMValidation(GoPluginApiRequest goPluginApiRequest) {
        final Map<String, String> configuration = keyValuePairs(goPluginApiRequest, "scm-configuration");

        List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
        validate(response, new FieldValidator() {
            @Override
            public void validate(Map<String, Object> fieldValidation) {
                String url = configuration.get("url");
                if (url == null || url.trim().isEmpty()) {
                    fieldValidation.put("key", "url");
                    fieldValidation.put("message", "URL is a required field");
                } else {
                    if (url.startsWith("/")) {
                        if (!new File(url).exists()) {
                            fieldValidation.put("key", "url");
                            fieldValidation.put("message", "Invalid URL. Directory does not exist");
                        }
                    } else {
                        if (!new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS).isValid(url)) {
                            fieldValidation.put("key", "url");
                            fieldValidation.put("message", "Invalid URL format");
                        }
                    }
                }
            }
        });
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleSCMCheckConnection(GoPluginApiRequest goPluginApiRequest) {
        Map<String, String> configuration = keyValuePairs(goPluginApiRequest, "scm-configuration");

        Map<String, Object> response = new HashMap<String, Object>();
        ArrayList<String> messages = new ArrayList<String>();
        try {
            URL url = new URL(configuration.get("url"));
            URLConnection connection = url.openConnection();
            connection.connect();
            response.put("status", "success");
            messages.add("Could connect to URL successfully");
        } catch (MalformedURLException e) {
            response.put("status", "failure");
            messages.add("Malformed URL");
        } catch (IOException e) {
            response.put("status", "failure");
            messages.add("Could not connect to URL");
        } catch (Exception e) {
            response.put("status", "failure");
            messages.add(e.getMessage());
        }

        response.put("messages", messages);
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleGetLatestRevision(GoPluginApiRequest goPluginApiRequest) {
        Map<String, String> configuration = keyValuePairs(goPluginApiRequest, "scm-configuration");
        String url = configuration.get("url");
        String flyweightFolder = (String) getValueFor(goPluginApiRequest, "flyweight-folder");

        LOGGER.warn("flyweight: " + flyweightFolder);

        try {
            JGitHelper.cloneOrFetch(url, flyweightFolder);

            Revision revision = JGitHelper.getLatestRevision(flyweightFolder);

            if (revision == null) {
                return renderJSON(SUCCESS_RESPONSE_CODE, null);
            } else {
                Map<String, Object> revisionMap = getRevisionMap(revision);
                return renderJSON(SUCCESS_RESPONSE_CODE, revisionMap);
            }
        } catch (Throwable t) {
            LOGGER.warn("get latest revision: ", t);
            return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, null);
        }
    }

    private GoPluginApiResponse handleLatestRevisionSince(GoPluginApiRequest goPluginApiRequest) {
        Map<String, String> configuration = keyValuePairs(goPluginApiRequest, "scm-configuration");
        String url = configuration.get("url");
        String flyweightFolder = (String) getValueFor(goPluginApiRequest, "flyweight-folder");
        Map<String, Object> previousRevisionMap = getMapFor(goPluginApiRequest, "previous-revision");
        String previousRevision = (String) previousRevisionMap.get("revision");

        LOGGER.warn("flyweight: " + flyweightFolder + ". previous commit: " + previousRevision);

        try {
            JGitHelper.cloneOrFetch(url, flyweightFolder);

            List<Revision> newerRevisions = JGitHelper.getNewerRevisions(flyweightFolder, previousRevision);

            if (newerRevisions == null || newerRevisions.isEmpty()) {
                return renderJSON(SUCCESS_RESPONSE_CODE, null);
            } else {
                LOGGER.warn("new commits: " + newerRevisions.size());

                Map<String, Object> response = new HashMap<String, Object>();
                List<Map> revisions = new ArrayList<Map>();
                for (Revision revisionObj : newerRevisions) {
                    Map<String, Object> revisionMap = getRevisionMap(revisionObj);
                    revisions.add(revisionMap);
                }
                response.put("revisions", revisions);
                return renderJSON(SUCCESS_RESPONSE_CODE, response);
            }
        } catch (Throwable t) {
            LOGGER.warn("get latest revisions since: ", t);
            return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, null);
        }
    }

    private GoPluginApiResponse handleCheckout(GoPluginApiRequest goPluginApiRequest) {
        Map<String, String> configuration = keyValuePairs(goPluginApiRequest, "scm-configuration");
        String url = configuration.get("url");
        String destinationFolder = (String) getValueFor(goPluginApiRequest, "destination-folder");
        Map<String, Object> revisionMap = getMapFor(goPluginApiRequest, "revision");
        String revision = (String) revisionMap.get("revision");

        LOGGER.warn("destination: " + destinationFolder + ". commit: " + revision);

        try {
            JGitHelper.cloneOrFetch(url, destinationFolder);

            JGitHelper.checkoutToRevision(destinationFolder, revision);

            Map<String, Object> response = new HashMap<String, Object>();
            ArrayList<String> messages = new ArrayList<String>();
            response.put("status", "success");
            messages.add("Checked out to revision " + revision);
            response.put("messages", messages);

            return renderJSON(SUCCESS_RESPONSE_CODE, response);
        } catch (Throwable t) {
            LOGGER.warn("checkout: ", t);
            return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, null);
        }
    }

    private void validate(List<Map<String, Object>> response, FieldValidator fieldValidator) {
        Map<String, Object> fieldValidation = new HashMap<String, Object>();
        fieldValidator.validate(fieldValidation);
        if (!fieldValidation.isEmpty()) {
            response.add(fieldValidation);
        }
    }

    private Map<String, Object> getRevisionMap(Revision revision) {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("revision", revision.revision);
        response.put("timestamp", new SimpleDateFormat(DATE_PATTERN).format(revision.timestamp));
        response.put("revisionComment", revision.comment);
        List<Map> modifiedFilesMapList = new ArrayList<Map>();
        for (ModifiedFile modifiedFile : revision.modifiedFiles) {
            Map<String, String> modifiedFileMap = new HashMap<String, String>();
            modifiedFileMap.put("fileName", modifiedFile.fileName);
            modifiedFileMap.put("action", modifiedFile.action);
            modifiedFilesMapList.add(modifiedFileMap);
        }
        response.put("modifiedFiles", modifiedFilesMapList);
        return response;
    }

    private Map<String, Object> getMapFor(GoPluginApiRequest goPluginApiRequest, String field) {
        Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), Object.class);
        Map<String, Object> fieldProperties = (Map<String, Object>) map.get(field);
        return fieldProperties;
    }

    private Object getValueFor(GoPluginApiRequest goPluginApiRequest, String field) {
        Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), Object.class);
        return map.get(field);
    }

    private Map<String, String> keyValuePairs(GoPluginApiRequest goPluginApiRequest, String mainKey) {
        Map<String, String> keyValuePairs = new HashMap<String, String>();
        Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), Object.class);
        Map<String, Object> fieldsMap = (Map<String, Object>) map.get(mainKey);
        for (String field : fieldsMap.keySet()) {
            Map<String, Object> fieldProperties = (Map<String, Object>) fieldsMap.get(field);
            String value = (String) fieldProperties.get("value");
            keyValuePairs.put(field, value);
        }
        return keyValuePairs;
    }

    private Map<String, Object> createField(String displayName, String defaultValue, boolean isPartOfIdentity, boolean isRequired, boolean isSecure, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<String, Object>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("part-of-identity", isPartOfIdentity);
        fieldProperties.put("required", isRequired);
        fieldProperties.put("secure", isSecure);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : new GsonBuilder().create().toJson(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }

            @Override
            public String responseBody() {
                return json;
            }
        };
    }
}
