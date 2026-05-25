package com.forfun.code_lineage.controller.dto;

import lombok.Data;

@Data
public class ScanRequest {
    private String appId;
    private String repoUrl;
    private String branch;
    private String scanType;
    private String commitSha;
}
