package dev.phlp.stud.evaluator.service.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;

public class GitService {
    private static final String WORK_BRANCH = "evaluation-snapshot";

    private final TransportConfigCallback transportConfigCallback;

    public GitService() {
        Path homeDirectory = resolveUserHome();
        Path sshDirectory =
                homeDirectory != null ?
                homeDirectory.resolve(".ssh") :
                null;

        SshdSessionFactoryBuilder builder = new SshdSessionFactoryBuilder();
        if (homeDirectory != null) {
            builder.setHomeDirectory(homeDirectory.toFile());
        }
        if (sshDirectory != null) {
            builder.setSshDirectory(sshDirectory.toFile());
        }
        builder.setDefaultIdentities(dir -> discoverIdentityFiles(dir, sshDirectory));

        SshdSessionFactory sshdFactory = builder.build(null);
        SshSessionFactory.setInstance(sshdFactory);

        SshSessionFactory finalFactory = sshdFactory;
        this.transportConfigCallback = transport -> {
            if (transport instanceof SshTransport sshTransport) {
                sshTransport.setSshSessionFactory(finalFactory);
            }
        };
    }

    public Path cloneOrUpdate(String repositoryUrl, Path targetDirectory) throws GitServiceException {
        try {
            Path parent = targetDirectory.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(targetDirectory.resolve(".git"))) {
                try (Git git = Git.open(targetDirectory.toFile())) {
                    git.fetch()
                       .setRemote("origin")
                       .setTagOpt(TagOpt.FETCH_TAGS)
                       .setRemoveDeletedRefs(true)
                       .setTransportConfigCallback(transportConfigCallback)
                       .call();
                    String defaultBranch = resolveDefaultBranchName(git.getRepository());
                    if (defaultBranch != null) {
                        checkoutTrackingBranch(git, defaultBranch);
                        git.reset()
                           .setMode(ResetCommand.ResetType.HARD)
                           .setRef("refs/remotes/origin/" + defaultBranch)
                           .call();
                        git.pull()
                           .setRemote("origin")
                           .setRemoteBranchName(defaultBranch)
                           .setTransportConfigCallback(transportConfigCallback)
                           .call();
                    }
                }
            } else {
                try (Git git = Git.cloneRepository()
                                  .setURI(repositoryUrl)
                                  .setDirectory(targetDirectory.toFile())
                                  .setCloneAllBranches(true)
                                  .setTransportConfigCallback(transportConfigCallback)
                                  .call()) {
                    // cloneRepository already returns a Git instance; try-with-resources closes it.
                }
            }
            return targetDirectory;
        } catch (GitAPIException | IOException ex) {
            throw new GitServiceException("Repository konnte nicht geklont oder aktualisiert werden", ex);
        }
    }

    public String checkoutTag(Path repositoryRoot, String tagName) throws GitServiceException {
        if (tagName == null || tagName.isBlank()) {
            throw new IllegalArgumentException("tagName must not be blank");
        }
        try (Git git = Git.open(repositoryRoot.toFile())) {
            Repository repository = git.getRepository();
            TagReference reference = resolveTagReference(repository, tagName);
            checkoutCommit(git, reference.objectId());
            return reference.objectId().getName();
        } catch (GitServiceException ex) {
            throw ex;
        } catch (IOException | GitAPIException ex) {
            throw new GitServiceException("Tag konnte nicht ausgecheckt werden", ex);
        }
    }

    public Instant resolveTagCommitInstant(Path repositoryRoot, String tagName) throws GitServiceException {
        if (tagName == null || tagName.isBlank()) {
            throw new IllegalArgumentException("tagName must not be blank");
        }
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repositoryRoot.resolve(".git").toFile())
                .build()) {
            TagReference reference = resolveTagReference(repository, tagName);
            return reference.commitInstant();
        } catch (GitServiceException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new GitServiceException("Tag-Informationen konnten nicht ermittelt werden", ex);
        }
    }

    public String checkoutLatestBefore(Path repositoryRoot, LocalDate deadline) throws GitServiceException {
        if (deadline == null) {
            throw new IllegalArgumentException("deadline must not be null");
        }
        try (Git git = Git.open(repositoryRoot.toFile())) {
            Repository repository = git.getRepository();
            ObjectId startPoint = resolveDefaultHead(repository);
            if (startPoint == null) {
                throw new GitServiceException("Es konnte kein Standard-Branch ermittelt werden");
            }
            Instant cutOff = deadline.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();
            RevCommit targetCommit = null;
            Iterable<RevCommit> commits = git.log().add(startPoint).call();
            for (RevCommit commit : commits) {
                Instant commitInstant = Instant.ofEpochSecond(commit.getCommitTime());
                if (!commitInstant.isAfter(cutOff)) {
                    targetCommit = commit;
                    break;
                }
            }
            if (targetCommit == null) {
                throw new GitServiceException("Kein Commit am oder vor dem Stichtag gefunden");
            }
            checkoutCommit(git, targetCommit.getId());
            return targetCommit.getName();
        } catch (IOException | GitAPIException ex) {
            throw new GitServiceException("Checkout des Stichtag-Commits fehlgeschlagen", ex);
        }
    }

    public String resolveCurrentCommit(Path repositoryRoot) throws GitServiceException {
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(repositoryRoot.resolve(".git").toFile())
                .build()) {
            ObjectId head = repository.resolve(Constants.HEAD);
            return head != null ?
                   head.getName() :
                   null;
        } catch (IOException ex) {
            throw new GitServiceException("Aktueller Commit konnte nicht ermittelt werden", ex);
        }
    }

    private void checkoutTrackingBranch(Git git, String branch) throws GitAPIException, IOException {
        Repository repository = git.getRepository();
        Ref localRef = repository.findRef(Constants.R_HEADS + branch);
        if (localRef == null) {
            git.checkout()
               .setName(branch)
               .setCreateBranch(true)
               .setStartPoint("origin/" + branch)
               .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
               .setForced(true)
               .call();
        } else {
            git.checkout()
               .setName(branch)
               .setForced(true)
               .call();
        }
    }

    private String resolveDefaultBranchName(Repository repository) throws IOException {
        Ref originHead = repository.findRef(Constants.R_REMOTES + "origin/HEAD");
        if (originHead != null && originHead.isSymbolic()) {
            Ref target = originHead.getTarget();
            if (target != null) {
                String name = target.getName();
                String prefix = Constants.R_REMOTES + "origin/";
                if (name.startsWith(prefix)) {
                    return name.substring(prefix.length());
                }
            }
        }
        for (String candidate : new String[] {"main", "master"}) {
            if (repository.findRef(Constants.R_REMOTES + "origin/" + candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    private void checkoutCommit(Git git, ObjectId commitId) throws GitAPIException {
        try {
            git.branchDelete().setBranchNames(WORK_BRANCH).setForce(true).call();
        } catch (GitAPIException ignored) {
            // branch may not exist; ignore
        }
        git.checkout()
           .setName(WORK_BRANCH)
           .setCreateBranch(true)
           .setStartPoint(commitId.getName())
           .setForced(true)
           .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.NOTRACK)
           .call();
    }

    private TagReference resolveTagReference(Repository repository, String tagName) throws IOException, GitServiceException {
        Ref tagRef = repository.findRef(tagName);
        if (tagRef == null) {
            tagRef = repository.findRef(Constants.R_TAGS + tagName);
        }
        if (tagRef == null) {
            throw new GitServiceException("Tag '" + tagName + "' wurde nicht gefunden");
        }
        Ref peeled = repository.getRefDatabase().peel(tagRef);
        ObjectId objectId =
                peeled != null ?
                peeled.getPeeledObjectId() :
                null;
        if (objectId == null) {
            objectId = tagRef.getObjectId();
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            Instant commitInstant = Instant.ofEpochSecond(commit.getCommitTime());
            return new TagReference(objectId, commitInstant);
        }
    }

    private List<Path> discoverIdentityFiles(File providedDirectory, Path fallbackDirectory) {
        Path base =
                providedDirectory != null ?
                providedDirectory.toPath() :
                fallbackDirectory;
        if (base == null || !Files.isDirectory(base)) {
            return Collections.emptyList();
        }
        List<Path> identities = new ArrayList<>();
        for (String candidate : new String[] {"id_ed25519", "id_ecdsa", "id_rsa", "id_dsa"}) {
            Path key = base.resolve(candidate);
            if (Files.isRegularFile(key)) {
                identities.add(key);
            }
        }
        return identities;
    }

    private Path resolveUserHome() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return null;
        }
        try {
            return Path.of(home).toAbsolutePath();
        } catch (Exception ignored) {
            return null;
        }
    }

    private ObjectId resolveDefaultHead(Repository repository) throws IOException {
        ObjectId head = repository.resolve("refs/remotes/origin/HEAD");
        if (head != null) {
            return peelIfTag(repository, head);
        }
        for (String candidate : new String[] {"refs/heads/main", "refs/heads/master", Constants.HEAD}) {
            head = repository.resolve(candidate);
            if (head != null) {
                return peelIfTag(repository, head);
            }
        }
        return null;
    }

    private ObjectId peelIfTag(Repository repository, ObjectId objectId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseAny(objectId).getId();
        }
    }

    private record TagReference(
            ObjectId objectId,
            Instant commitInstant) {
    }
}
