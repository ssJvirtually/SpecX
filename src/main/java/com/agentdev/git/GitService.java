package com.agentdev.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.agentdev.core.exception.GitOperationException;

@Service
public class GitService {

    @Value("${github.token}")
    private String gitToken;

    @Value("${git.workspace-dir:/tmp/agent-workspaces}")
    private String workspaceDir;

    // Clones repo and checks out a new branch. Returns local path.
    public String cloneAndBranch(String repoUrl, String branchName) {
        String localPath = workspaceDir + "/"
            + branchName.replace("/", "-") + "-" + System.currentTimeMillis();
        try {
            Files.createDirectories(Path.of(localPath));
            Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(credentials())
                .call();
            git.checkout().setCreateBranch(true).setName(branchName).call();
            git.close();
            return localPath;
        } catch (Exception e) {
            throw new GitOperationException("Clone failed for " + repoUrl, e);
        }
    }

    // Returns the unified diff of all uncommitted changes
    public String getDiff(String repoPath) {
        try (Git git = Git.open(new File(repoPath))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            git.diff().setOutputStream(out).call();
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GitOperationException("Diff failed at " + repoPath, e);
        }
    }

    // Stages all changes, commits, and pushes the branch
    public void commitAndPush(String repoPath, String commitMessage, String branchName) {
        try (Git git = Git.open(new File(repoPath))) {
            git.add().addFilepattern(".").call();
            git.commit()
                .setMessage(commitMessage)
                .setAuthor("Agent Bot", "agent@agentdev.com")
                .call();
            git.push()
                .setCredentialsProvider(credentials())
                .setRefSpecs(new RefSpec(branchName + ":" + branchName))
                .call();
        } catch (Exception e) {
            throw new GitOperationException("Commit/push failed", e);
        }
    }

    // Clones the default branch of the remote repository directly without creating a new local feature branch
    public String cloneDefaultBranch(String repoUrl) {
        String localPath = workspaceDir + "/default-branch-" + System.currentTimeMillis();
        try {
            Files.createDirectories(Path.of(localPath));
            Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(credentials())
                .call();
            git.close();
            return localPath;
        } catch (Exception e) {
            throw new GitOperationException("Clone failed for default branch of " + repoUrl, e);
        }
    }

    // Stages all changes, commits, and pushes to the default branch
    public void commitAndPushDefault(String repoPath, String commitMessage) {
        try (Git git = Git.open(new File(repoPath))) {
            String branchName = git.getRepository().getBranch();
            git.add().addFilepattern(".").call();
            git.commit()
                .setMessage(commitMessage)
                .setAuthor("Agent Bot", "agent@agentdev.com")
                .call();
            git.push()
                .setCredentialsProvider(credentials())
                .setRefSpecs(new RefSpec(branchName + ":" + branchName))
                .call();
        } catch (Exception e) {
            throw new GitOperationException("Commit/push to default branch failed", e);
        }
    }

    // Always call in a finally block to free disk space
    public void cleanup(String repoPath) {
        FileUtils.deleteQuietly(new File(repoPath));
    }

    private CredentialsProvider credentials() {
        return new UsernamePasswordCredentialsProvider(gitToken, "");
    }
}
