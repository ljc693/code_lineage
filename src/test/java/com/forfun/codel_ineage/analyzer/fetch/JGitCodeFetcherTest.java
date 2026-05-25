package com.forfun.codel_ineage.analyzer.fetch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.File;
import java.nio.file.Path;

class JGitCodeFetcherTest {

    @Test
    void shouldCloneAndReturnFetchedCode(@TempDir Path tempDir) throws Exception {
        File repoDir = new File(tempDir.toFile(), "test-repo");
        org.eclipse.jgit.api.Git.init().setDirectory(repoDir).call().close();

        File srcFile = new File(repoDir, "Test.java");
        java.nio.file.Files.writeString(srcFile.toPath(), "public class Test {}");
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir)) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").call();
        }

        JGitCodeFetcher fetcher = new JGitCodeFetcher();
        FetchTask task = FetchTask.builder()
                .repoUrl(repoDir.getAbsolutePath())
                .branch("refs/heads/master")
                .build();

        FetchedCode result = fetcher.fetch(task);

        assertThat(result.getBaseDir()).isNotNull();
        assertThat(result.getChangedFiles()).isNotEmpty();
        assertThat(new File(result.getBaseDir(), "Test.java")).exists();
    }
}
