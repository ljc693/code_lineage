package com.forfun.codel_ineage.analyzer;

/**
 * Per-file progress emitted during code analysis.
 */
public record FileProgress(String fileName, int currentFile, int totalFiles, String phase) {}
