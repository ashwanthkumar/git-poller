package com.tw.go.plugin;

import org.eclipse.jgit.api.*;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JGitHelper {
    public void cloneOrFetch(String url, String folder) throws Exception {
        if (!new File(folder).exists() || !getGitDir(folder).exists()) {
            cloneRepository(url, folder);
        } else {
            cleanRepository(folder);
            fetchRepository(url, folder);
            gcRepository(folder);
            resetRepository(folder, "origin/master");
            cleanRepository(folder);
        }
    }

    private void cloneRepository(String url, String folder) throws Exception {
        // delete if exists
        new File(folder).mkdirs();

        CloneCommand cloneCommand = Git.cloneRepository().setURI(url).setDirectory(new File(folder));
        if (url.startsWith("http") || url.startsWith("https")) {
            // set credentials
        }
        cloneCommand.call();
    }

    private void cleanRepository(String folder) throws Exception {
        Repository repository = null;
        try {
            repository = new FileRepositoryBuilder().setGitDir(getGitDir(folder)).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            CleanCommand clean = git.clean().setCleanDirectories(true);
            clean.call();
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    private void fetchRepository(String url, String folder) throws Exception {
        // check remote url - if ok

        checkoutToRevision(folder, "master");

        Repository repository = null;
        try {
            repository = new FileRepositoryBuilder().setGitDir(getGitDir(folder)).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            FetchCommand fetch = git.fetch();
            if (url.startsWith("http") || url.startsWith("https")) {
                // if url is http/https - set credentials
            }
            fetch.call();
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
        // else delete folder & clone
    }

    private void gcRepository(String folder) throws Exception {
        Repository repository = null;
        try {
            repository = new FileRepositoryBuilder().setGitDir(getGitDir(folder)).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            GarbageCollectCommand gc = git.gc();
            gc.call();
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    private void resetRepository(String folder, String revision) throws Exception {
        Repository repository = null;
        try {
            repository = new FileRepositoryBuilder().setGitDir(getGitDir(folder)).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            ResetCommand reset = git.reset().setMode(ResetCommand.ResetType.HARD).setRef(revision);
            reset.call();
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    public Revision getLatestRevision(String folder) throws Exception {
        Repository repository = null;
        try {
            repository = new FileRepositoryBuilder().setGitDir(getGitDir(folder)).readEnvironment().findGitDir().build();
            Git git = new Git(repository);
            Iterable<RevCommit> log = git.log().call();
            Iterator<RevCommit> iterator = log.iterator();
            if (iterator.hasNext()) {
                return getRevisionObj(repository, iterator.next());
            }
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
        return null;
    }

    public List<Revision> getNewerRevisions(String folder, String previousRevision) throws Exception {
        Repository repository = null;
        try {
            repository = new FileRepositoryBuilder().setGitDir(getGitDir(folder)).readEnvironment().findGitDir().build();
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

            if (newCommits.isEmpty()) {
                return null;
            }

            List<Revision> revisionObjs = new ArrayList<Revision>();
            for (RevCommit newCommit : newCommits) {
                Revision revisionObj = getRevisionObj(repository, newCommit);
                revisionObjs.add(revisionObj);
            }
            return revisionObjs;
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    public void checkoutToRevision(String folder, String revision) throws Exception {
        resetRepository(folder, revision);
    }

    private Revision getRevisionObj(Repository repository, RevCommit commit) throws IOException {
        String commitSHA = commit.getName();
        int commitTime = commit.getCommitTime();
        String comment = commit.getFullMessage().trim();
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

    private File getGitDir(String folder) {
        return new File(folder, ".git");
    }
}
