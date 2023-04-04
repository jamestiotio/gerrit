import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_FILES;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.server.patch.DiffUtil.cleanPatch;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.client.ListChangesOption;
import org.eclipse.jgit.revwalk.RevCommit;
    assertThat(cleanPatch(resultPatch)).isEqualTo(cleanPatch(originalPatch));
    assertThat(cleanPatch(resultPatch)).isEqualTo(cleanPatch(originalPatch));
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);
    PushOneCommit.Result destChange = createChange("refs/for/" + DESTINATION_BRANCH);
    PushOneCommit.Result baseCommit =
        createChange(testRepo, "branch", "Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT, "");
    assertThat(cleanPatch(resultPatch)).isEqualTo(cleanPatch(originalPatch));
  public void applyGerritBasedPatchUsingRestWithEncodedPatch_success() throws Exception {
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, DESTINATION_BRANCH), head);
    PushOneCommit.Result destChange = createChange("refs/for/" + DESTINATION_BRANCH);
    createBranchWithRevision(BranchNameKey.create(project, "branch"), head);
    PushOneCommit.Result baseCommit =
        createChange(testRepo, "branch", "Add file", ADDED_FILE_NAME, ADDED_FILE_CONTENT, "");
    baseCommit.assertOkStatus();
    RestResponse patchResp =
        userRestSession.get("/changes/" + baseCommit.getChangeId() + "/revisions/current/patch");
    patchResp.assertOK();
    String originalEncodedPatch = patchResp.getEntityContent();
    String originalDecodedPatch = new String(Base64.decode(patchResp.getEntityContent()), UTF_8);
    ApplyPatchPatchSetInput in = buildInput(originalEncodedPatch);
    RestResponse resp =
        adminRestSession.post("/changes/" + destChange.getChangeId() + "/patch:apply", in);
    resp.assertOK();
    BinaryResult resultPatch = gApi.changes().id(destChange.getChangeId()).current().patch();
    assertThat(cleanPatch(resultPatch)).isEqualTo(cleanPatch(originalDecodedPatch));
  @Test
  public void applyPatchWithExplicitBase_overrideParentId() throws Exception {
    PushOneCommit.Result inputParent = createChange("Input parent", "file1", "content");
    PushOneCommit.Result parent = createChange("Parent Change", "file2", "content");
    parent.assertOkStatus();
    PushOneCommit.Result dest = createChange("Destination Change", "file3", "content");
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.base = inputParent.getCommit().name();

    gApi.changes().id(dest.getChangeId()).applyPatch(in);

    ChangeInfo result = get(dest.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT);
    assertThat(result.revisions.get(result.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(inputParent.getCommit().name());

    BinaryResult resultPatch = gApi.changes().id(dest.getChangeId()).current().patch();
    assertThat(cleanPatch(resultPatch)).isEqualTo(ADDED_FILE_DIFF.trim());
  }

  @Test
  public void applyPatchWithNoExplicitBase_overwritesLatestPatch() throws Exception {
    PushOneCommit.Result dest = createChange("Destination Change", "ps1.txt", "ps1 content");
    RevCommit originalParentCommit = dest.getCommit().getParent(0);
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);

    gApi.changes().id(dest.getChangeId()).applyPatch(in);

    ChangeInfo result = get(dest.getChangeId(), CURRENT_REVISION, CURRENT_COMMIT, CURRENT_FILES);
    assertThat(result.revisions.get(result.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(originalParentCommit.name());
    assertThat(result.revisions.get(result.currentRevision).files.keySet())
        .containsExactly(ADDED_FILE_NAME);
    assertDiffForNewFile(
        fetchDiffForFile(result, ADDED_FILE_NAME),
        result.currentRevision,
        ADDED_FILE_NAME,
        ADDED_FILE_CONTENT);
  }

  @Test
  public void commitMessage_providedMessage() throws Exception {
    final String msg = "custom message";
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);
    in.commitMessage = msg;

    ChangeInfo result = applyPatch(in);

    ChangeInfo info = get(result.changeId, CURRENT_REVISION, CURRENT_COMMIT);
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .isEqualTo(msg + "\n\nChange-Id: " + result.changeId + "\n");
  }

  @Test
  public void commitMessage_defaultMessageAndPatchHeader() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput("Patch header\n" + ADDED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    ChangeInfo info = get(result.changeId, CURRENT_REVISION, CURRENT_COMMIT);
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .isEqualTo("Default commit message\n\nChange-Id: " + result.changeId + "\n");
  }

  @Test
  public void commitMessage_defaultMessageAndNoPatchHeader() throws Exception {
    initDestBranch();
    ApplyPatchPatchSetInput in = buildInput(ADDED_FILE_DIFF);

    ChangeInfo result = applyPatch(in);

    ChangeInfo info = get(result.changeId, CURRENT_REVISION, CURRENT_COMMIT);
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .isEqualTo("Default commit message\n\nChange-Id: " + result.changeId + "\n");
  }

    input.responseFormatOptions = ImmutableList.of(ListChangesOption.CURRENT_REVISION);
        .create(new ChangeInput(project.get(), DESTINATION_BRANCH, "Default commit message"))