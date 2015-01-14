package com.tw.go.plugin;

import org.junit.Test;

import java.util.List;

public class JGitHelperTest {
    @Test
    public void shouldCheckoutAndGetLatestRevision() throws Exception {
        JGitHelper.cloneOrFetch("/Users/sriniup/Documents/repos/git-repo-1", "/tmp/crap");

        Revision revision = JGitHelper.getLatestRevision("/tmp/crap");

        System.out.println(revision);
    }

    @Test
    public void shouldFetchAndGetLatestRevisionsSince() throws Exception {
        JGitHelper.cloneOrFetch("/Users/sriniup/Documents/repos/git-repo-1", "/tmp/crap");

        List<Revision> newerRevisions = JGitHelper.getNewerRevisions("/tmp/crap", "7f255d85652c1413f2611a10c77001b61bb13f1d");

        System.out.println(newerRevisions);
    }

    @Test
    public void shouldCheckoutToRevision() throws Exception {
        JGitHelper.cloneOrFetch("/Users/sriniup/Documents/repos/git-repo-1", "/tmp/crap");

        JGitHelper.checkoutToRevision("/tmp/crap", "7f255d85652c1413f2611a10c77001b61bb13f1d");
    }
}