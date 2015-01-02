package com.tw.go.plugin;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import org.apache.commons.validator.routines.UrlValidator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

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
    public static final String REQUEST_VALIDATE_SCM_CONFIGURATION = "validate-scm-configuration";
    public static final String REQUEST_CHECK_SCM_CONNECTION = "check-scm-connection";
    public static final String REQUEST_LATEST_REVISION = "latest-revision";
    public static final String REQUEST_LATEST_REVISIONS_SINCE = "latest-revisions-since";
    public static final String REQUEST_CHECKOUT = "checkout";

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final int SUCCESS_RESPONSE_CODE = 200;

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        // ignore
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        if (goPluginApiRequest.requestName().equals(REQUEST_SCM_CONFIGURATION)) {
            return handleSCMConfiguration();
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
        response.put("url", createField("URL", null, true, true, false, "1"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleSCMValidation(GoPluginApiRequest goPluginApiRequest) {
        final Map<String, String> configuration = keyValuePairs(goPluginApiRequest, "scm-configuration");

        List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
        validate(response, new FieldValidator() {
            @Override
            public void validate(Map<String, Object> fieldValidation) {
                if (!new UrlValidator().isValid(configuration.get("url"))) {
                    fieldValidation.put("key", "url");
                    fieldValidation.put("message", "Invalid URL format");
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
        String flyweightFolder = (String) getValueFor(goPluginApiRequest, "flyweight-folder");

        LOGGER.warn("flyweight: " + flyweightFolder);

        String url = configuration.get("url");
        Repository repository = null;
        try {
            repository = new FileRepositoryBuilder().setGitDir(new File(url)).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            Iterable<RevCommit> log = git.log().call();
            Iterator<RevCommit> iterator = log.iterator();
            if (iterator.hasNext()) {
                Revision revision = getRevisionObj(repository, iterator.next());
                Map<String, Object> revisionMap = getRevisionMap(revision);
                return renderJSON(SUCCESS_RESPONSE_CODE, revisionMap);
            }
            return renderJSON(SUCCESS_RESPONSE_CODE, null);
        } catch (Exception e) {
            // add exception message
            LOGGER.warn("crap: ", e);
            return renderJSON(500, null);
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    private GoPluginApiResponse handleLatestRevisionSince(GoPluginApiRequest goPluginApiRequest) {
        Map<String, String> configuration = keyValuePairs(goPluginApiRequest, "scm-configuration");
        String flyweightFolder = (String) getValueFor(goPluginApiRequest, "flyweight-folder");
        Map<String, Object> previousRevisionMap = getMapFor(goPluginApiRequest, "previous-revision");
        String previousRevision = (String) previousRevisionMap.get("revision");

        LOGGER.warn("flyweight: " + flyweightFolder + ". previous commit: " + previousRevision);

        String url = configuration.get("url");
        Repository repository = null;
        try {
            repository = new FileRepositoryBuilder().setGitDir(new File(url)).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            Iterable<RevCommit> log = git.log().call();
            Iterator<RevCommit> iterator = log.iterator();
            List<RevCommit> newCommits = new ArrayList<RevCommit>();
            while (iterator.hasNext()) {
                RevCommit commit = iterator.next();
                if (commit.getName().equals(previousRevision)) {
                    break;
                }
                newCommits.add(commit);
            }
            LOGGER.warn("new commits: " + newCommits.size());
            if (newCommits.isEmpty()) {
                return renderJSON(SUCCESS_RESPONSE_CODE, null);
            }

            Map<String, Object> response = new HashMap<String, Object>();
            List<Map> revisions = new ArrayList<Map>();
            for (RevCommit newCommit : newCommits) {
                Revision revisionObj = getRevisionObj(repository, newCommit);
                Map<String, Object> revisionMap = getRevisionMap(revisionObj);
                revisions.add(revisionMap);
            }
            response.put("revisions", revisions);
            return renderJSON(SUCCESS_RESPONSE_CODE, response);
        } catch (Exception e) {
            // add exception message
            LOGGER.warn("crap: ", e);
            return renderJSON(500, null);
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    private GoPluginApiResponse handleCheckout(GoPluginApiRequest goPluginApiRequest) {
        Map<String, String> configuration = keyValuePairs(goPluginApiRequest, "scm-configuration");
        String destinationFolder = (String) getValueFor(goPluginApiRequest, "destination-folder");
        Map<String, Object> revisionMap = getMapFor(goPluginApiRequest, "revision");
        String revision = (String) revisionMap.get("revision");

        LOGGER.warn("destination: " + destinationFolder + ". commit: " + revision);

        Map<String, Object> response = new HashMap<String, Object>();
        ArrayList<String> messages = new ArrayList<String>();
        response.put("status", "success");
        messages.add("Checked out to revision " + revision);
        response.put("messages", messages);

        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private void validate(List<Map<String, Object>> response, FieldValidator fieldValidator) {
        Map<String, Object> fieldValidation = new HashMap<String, Object>();
        fieldValidator.validate(fieldValidation);
        if (!fieldValidation.isEmpty()) {
            response.add(fieldValidation);
        }
    }

    Revision getRevisionObj(Repository repository, RevCommit commit) throws IOException {
        String commitSHA = commit.getName();
        int commitTime = commit.getCommitTime();
        String comment = commit.getFullMessage();
        String user = commit.getAuthorIdent().getEmailAddress();
        List<ModifiedFile> modifiedFiles = new ArrayList<ModifiedFile>();
        if (commit.getParentCount() == 0) {
            TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(false);
            while (treeWalk.next()) {
                modifiedFiles.add(new ModifiedFile(treeWalk.getPathString(), "add"));
            }
        } else {
            RevWalk rw = new RevWalk(repository);
            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
            DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);
            List<DiffEntry> diffEntries = diffFormatter.scan(parent.getTree(), commit.getTree());
            for (DiffEntry diffEntry : diffEntries) {
                modifiedFiles.add(new ModifiedFile(diffEntry.getNewPath(), getAction(diffEntry.getChangeType().name())));
            }
        }

        return new Revision(commitSHA, commitTime, comment, user, modifiedFiles);
    }

    private String getAction(String gitAction) {
        if (gitAction.equalsIgnoreCase("ADD") || gitAction.equalsIgnoreCase("RENAME")) {
            return "added";
        }
        if (gitAction.equals("MODIFY")) {
            return "modified";
        }
        if (gitAction.equals("DELETE")) {
            return "deleted";
        }
        return "unknown";
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