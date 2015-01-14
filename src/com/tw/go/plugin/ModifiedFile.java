package com.tw.go.plugin;

public class ModifiedFile {
    String fileName;
    String action;

    public ModifiedFile(String fileName, String action) {
        this.fileName = fileName;
        this.action = action;
    }

    @Override
    public String toString() {
        return "ModifiedFile{" +
                "fileName='" + fileName + '\'' +
                ", action='" + action + '\'' +
                '}';
    }
}
