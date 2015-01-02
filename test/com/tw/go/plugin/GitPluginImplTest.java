package com.tw.go.plugin;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Test;

import java.io.File;
import java.util.Iterator;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class GitPluginImplTest {
    @Test
    public void test() throws Exception {
        GitPluginImpl gitPlugin = new GitPluginImpl();
        int count = 0;

        String url = "/Users/sriniup/Documents/repos/git-repo-1/.git";
        Repository repository = new FileRepositoryBuilder().setGitDir(new File(url)).readEnvironment().findGitDir().build();
        Git git = new Git(repository);
        Iterable<RevCommit> log = git.log().call();
        Iterator<RevCommit> iterator = log.iterator();
        while (iterator.hasNext()) {
            Revision revision = gitPlugin.getRevisionObj(repository, iterator.next());
            System.out.println(++count + " - " + revision.revision + " - " + revision.timestamp + " - " + revision.comment + " - " + revision.user);
            System.out.println("files:");
            for (ModifiedFile modifiedFile : revision.modifiedFiles) {
                System.out.println(modifiedFile.fileName + " - " + modifiedFile.action);
            }
        }
    }
}