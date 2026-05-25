package com.forfun.code_lineage.analyzer;

/**
 * Per-file progress emitted during code analysis.
 */
public record FileProgress(String fileName, int currentFile, int totalFiles, String phase) {}
