import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
  @Test
  public void applyPatchWithoutProvidingPatch_badRequest() throws Exception {
    initBaseWithFile(MODIFIED_FILE_NAME, MODIFIED_FILE_ORIGINAL_CONTENT);
    Throwable error = assertThrows(BadRequestException.class, () -> applyPatch(buildInput(null)));
    assertThat(error).hasMessageThat().isEqualTo("patch required");
  }

  @Test
  public void amendCommitWithValidTraditionalPatch_success() throws Exception {
    final String fileName = "file_name.txt";
    final String originalContent = "original line";
    final String newContent = "new line\n";
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-original line\n"
            + "+new line\n";

    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, "Test", fileName, "foo");
    PushOneCommit.Result base = push.to("refs/heads/foo");
    base.assertOkStatus();

    PushOneCommit.Result firstPatchSet =
        createChange(
            testRepo, "foo", "Add original file: " + fileName, fileName, originalContent, null);
    firstPatchSet.assertOkStatus();

    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = diff;
    in.amend = true;
    in.responseFormatOptions =
        ImmutableList.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

    ChangeInfo result = gApi.changes().id(firstPatchSet.getChangeId()).applyPatch(in);

    // Parent of patch set 2 = parent of patch set 1, so we actually amended
    assertThat(result.revisions.get(result.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(base.getCommit().getId().getName());
    DiffInfo fileDiff = gApi.changes().id(result.id).current().file(fileName).diff();
    assertDiffForFullyModifiedFile(fileDiff, result.currentRevision, fileName, "foo", newContent);
    assertThat(gApi.changes().id(firstPatchSet.getChangeId()).current().commit(false).message)
        .isEqualTo(firstPatchSet.getCommit().getFullMessage());
  }

  @Test
  public void amendCantBeUsedWithBase() throws Exception {
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-original line\n"
            + "+new line\n";
    PushOneCommit.Result change = createChange();
    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = diff;
    in.amend = true;
    in.base = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(change.getChangeId()).applyPatch(in));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("amend only works with existing revisions. omit base.");
  }

  @Test
  public void amendCommitWithConflict_appendErrorsToCommitMessage() throws Exception {
    final String fileName = "file_name.txt";
    final String originalContent = "original line";
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-xxx line\n"
            + "+new line\n";

    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, "Test", fileName, "foo");
    PushOneCommit.Result base = push.to("refs/heads/foo");
    base.assertOkStatus();

    PushOneCommit.Result firstPatchSet =
        createChange(
            testRepo, "foo", "Add original file: " + fileName, fileName, originalContent, null);
    firstPatchSet.assertOkStatus();

    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = diff;
    in.amend = true;
    in.responseFormatOptions =
        ImmutableList.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT);

    ChangeInfo result = gApi.changes().id(firstPatchSet.getChangeId()).applyPatch(in);
    assertThat(gApi.changes().id(result.id).current().commit(false).message)
        .startsWith(
            "Add original file: file_name.txt\n"
                + "\n"
                + "NOTE FOR REVIEWERS - errors occurred while applying the patch.\n"
                + "PLEASE REVIEW CAREFULLY.\n"
                + "Errors:\n"
                + "Error applying patch in file_name.txt, hunk HunkHeader[1,1->1,1]: Hunk cannot be applied\n"
                + "\n"
                + "Original patch:\n"
                + " diff file_name.txt file_name.txt\n"
                + "--- file_name.txt\n"
                + "+++ file_name.txt\n"
                + "@@ -1 +1 @@\n"
                + "-xxx line\n"
                + "+new line");
  }

  @Test
  public void amendCommitWithValidTraditionalPatchEmptyRepo_resourceNotFound() throws Exception {
    final String fileName = "file_name.txt";
    final String originalContent = "original line";
    final String diff =
        "diff file_name.txt file_name.txt\n"
            + "--- file_name.txt\n"
            + "+++ file_name.txt\n"
            + "@@ -1 +1 @@\n"
            + "-original line\n"
            + "+new line\n";

    Project.NameKey emptyProject = projectOperations.newProject().noEmptyCommit().create();
    TestRepository<InMemoryRepository> emptyClone = cloneProject(emptyProject);
    PushOneCommit.Result firstPatchSet =
        createChange(
            emptyClone,
            "master",
            "Add original file: " + fileName,
            fileName,
            originalContent,
            null);
    firstPatchSet.assertOkStatus();

    ApplyPatchPatchSetInput in = new ApplyPatchPatchSetInput();
    in.patch = new ApplyPatchInput();
    in.patch.patch = diff;
    in.amend = true;

    Throwable error =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.changes().id(firstPatchSet.getChangeId()).applyPatch(in));
    assertThat(error).hasMessageThat().contains("Branch refs/heads/master does not exist");
  }
