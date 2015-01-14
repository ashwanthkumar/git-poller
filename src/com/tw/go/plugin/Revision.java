package com.tw.go.plugin;

import java.util.Date;
import java.util.List;

public class Revision {
    String revision;
    Date timestamp;
    String comment;
    String user;
    List<ModifiedFile> modifiedFiles;

    public Revision(String revision, int timestamp, String comment, String user, List<ModifiedFile> modifiedFiles) {
        this.revision = revision;
        this.timestamp = new Date(timestamp);
        this.comment = comment;
        this.user = user;
        this.modifiedFiles = modifiedFiles;
    }

    @Override
    public String toString() {
        return "Revision{" +
                "revision='" + revision + '\'' +
                ", timestamp=" + timestamp +
                ", comment='" + comment + '\'' +
                ", user='" + user + '\'' +
                ", modifiedFiles=" + modifiedFiles +
                '}';
    }
}
