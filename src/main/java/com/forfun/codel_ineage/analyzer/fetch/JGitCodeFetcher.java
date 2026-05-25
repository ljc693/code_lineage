package com.forfun.codel_ineage.analyzer.fetch;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class JGitCodeFetcher implements CodeFetcher {

    @Override
    public FetchedCode fetch(FetchTask task) {
        try {
            Path workDir = Files.createTempDirectory("lineage-repo-");
            File repoDir = workDir.toFile();

            Git git = Git.cloneRepository()
                    .setURI(task.getRepoUrl())
                    .setDirectory(repoDir)
                    .setBranch(task.getBranch() != null ? task.getBranch().replace("refs/heads/", "") : null)
                    .call();

            List<String> changedFiles = new ArrayList<>();
            String commitLog = "";

            Repository repo = git.getRepository();
            if (task.getCommitSha() != null) {
                ObjectId commitId = repo.resolve(task.getCommitSha());
                try (RevWalk walk = new RevWalk(repo)) {
                    RevCommit commit = walk.parseCommit(commitId);
                    commitLog = commit.getFullMessage();
                    if (commit.getParentCount() > 0) {
                        RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
                        try (TreeWalk treeWalk = new TreeWalk(repo)) {
                            treeWalk.addTree(commit.getTree());
                            treeWalk.addTree(parent.getTree());
                            treeWalk.setRecursive(true);
                            while (treeWalk.next()) {
                                if (treeWalk.getRawMode(0) != treeWalk.getRawMode(1)
                                        || !treeWalk.idEqual(0, 1)) {
                                    changedFiles.add(treeWalk.getPathString());
                                }
                            }
                        }
                    }
                }
            } else {
                // Full scan: collect all .java files
                java.nio.file.Files.walk(workDir)
                        .filter(p -> p.toString().endsWith(".java"))
                        .map(p -> workDir.relativize(p).toString())
                        .forEach(changedFiles::add);
            }

            git.close();

            return FetchedCode.builder()
                    .baseDir(workDir.toString())
                    .changedFiles(changedFiles)
                    .commitLog(commitLog)
                    .appId(task.getAppId())
                    .branch(task.getBranch())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch code: " + task.getRepoUrl(), e);
        }
    }
}
