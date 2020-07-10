/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Tags identifying ChangeMessages that move change into WIP state.
const WIP_TAGS = [
  'autogenerated:gerrit:newWipPatchSet',
  'autogenerated:gerrit:setWorkInProgress',
];

// Tags identifying ChangeMessages that move change out of WIP state.
const READY_TAGS = [
  'autogenerated:gerrit:setReadyForReview',
];

export const SPECIAL_PATCH_SET_NUM = {
  EDIT: 'edit',
  PARENT: 'PARENT',
};

/**
 * As patchNum can be either a string (e.g. 'edit', 'PARENT') OR a number,
 * this function checks for patchNum equality.
 *
 * @param {string|number} a
 * @param {string|number|undefined} b Undefined sometimes because
 *    computeLatestPatchNum can return undefined.
 * @return {boolean}
 */
export function patchNumEquals(a, b) {
  return a + '' === b + '';
}

/**
 * Whether the given patch is a numbered parent of a merge (i.e. a negative
 * number).
 *
 * @param  {string|number} n
 * @return {boolean}
 */
export function isMergeParent(n) {
  return (n + '')[0] === '-';
}

/**
 * Given an object of revisions, get a particular revision based on patch
 * num.
 *
 * @param {Object} revisions The object of revisions given by the API
 * @param {number|string} patchNum The number index of the revision
 * @return {Object} The correspondent revision obj from {revisions}
 */
export function getRevisionByPatchNum(revisions, patchNum) {
  for (const rev of Object.values(revisions || {})) {
    if (patchNumEquals(rev._number, patchNum)) {
      return rev;
    }
  }
}

/**
 * Find change edit base revision if change edit exists.
 *
 * @param {!Array<!Object>} revisions The revisions array.
 * @return {Object} change edit parent revision or null if change edit
 *     doesn't exist.
 */
export function findEditParentRevision(revisions) {
  const editInfo =
      revisions.find(info => info._number === SPECIAL_PATCH_SET_NUM.EDIT);

  if (!editInfo) { return null; }

  return revisions.find(info => info._number === editInfo.basePatchNum) ||
      null;
}

/**
 * Find change edit base patch set number if change edit exists.
 *
 * @param {!Array<!Object>} revisions The revisions array.
 * @return {number} Change edit patch set number or -1.
 */
export function findEditParentPatchNum(revisions) {
  const revisionInfo = findEditParentRevision(revisions);
  return revisionInfo ? revisionInfo._number : -1;
}

/**
 * Sort given revisions array according to the patch set number, in
 * descending order.
 * The sort algorithm is change edit aware. Change edit has patch set number
 * equals 'edit', but must appear after the patch set it was based on.
 * Example: change edit is based on patch set 2, and another patch set was
 * uploaded after change edit creation, the sorted order should be:
 * 3, edit, 2, 1.
 *
 * @param {!Array<!Object>} revisions The revisions array
 * @return {!Array<!Object>} The sorted {revisions} array
 */
export function sortRevisions(revisions) {
  const editParent = findEditParentPatchNum(revisions);
  // Map a normal patchNum to 2 * (patchNum - 1) + 1... I.e. 1 -> 1,
  // 2 -> 3, 3 -> 5, etc.
  // Map an edit to the patchNum of parent*2... I.e. edit on 2 -> 4.
  const num = r => (r._number === SPECIAL_PATCH_SET_NUM.EDIT ?
    2 * editParent :
    2 * (r._number - 1) + 1);
  return revisions.sort((a, b) => num(b) - num(a));
}

/**
 * Construct a chronological list of patch sets derived from change details.
 * Each element of this list is an object with the following properties:
 *
 *   * num {number} The number identifying the patch set
 *   * desc {!string} Optional patch set description
 *   * wip {boolean} If true, this patch set was never subject to review.
 *   * sha {string} hash of the commit
 *
 * The wip property is determined by the change's current work_in_progress
 * property and its log of change messages.
 *
 * @param {!Object} change The change details
 * @return {!Array<!Object>} Sorted list of patch set objects, as described
 *     above
 */
export function computeAllPatchSets(change) {
  if (!change) { return []; }
  let patchNums = [];
  if (change.revisions && Object.keys(change.revisions).length) {
    const revisions = Object.keys(change.revisions)
        .map(sha => Object.assign({sha}, change.revisions[sha]));
    patchNums = sortRevisions(revisions)
        .map(e => {
          // TODO(kaspern): Mark which patchset an edit was made on, if an
          // edit exists -- perhaps with a temporary description.
          return {
            num: e._number,
            desc: e.description,
            sha: e.sha,
          };
        });
  }
  return _computeWipForPatchSets(change, patchNums);
}

/**
 * Populate the wip properties of the given list of patch sets.
 *
 * @param {!Object} change The change details
 * @param {!Array<!Object>} patchNums Sorted list of patch set objects, as
 *     generated by computeAllPatchSets
 * @return {!Array<!Object>} The given list of patch set objects, with the
 *     wip property set on each of them
 */
function _computeWipForPatchSets(change, patchNums) {
  if (!change.messages || !change.messages.length) {
    return patchNums;
  }
  const psWip = {};
  let wip = change.work_in_progress;
  for (let i = 0; i < change.messages.length; i++) {
    const msg = change.messages[i];
    if (WIP_TAGS.includes(msg.tag)) {
      wip = true;
    } else if (READY_TAGS.includes(msg.tag)) {
      wip = false;
    }
    if (psWip[msg._revision_number] !== false) {
      psWip[msg._revision_number] = wip;
    }
  }

  for (let i = 0; i < patchNums.length; i++) {
    patchNums[i].wip = psWip[patchNums[i].num];
  }
  return patchNums;
}

export const _testOnly_computeWipForPatchSets = _computeWipForPatchSets;

/** @return {number|undefined} */
export function computeLatestPatchNum(allPatchSets) {
  if (!allPatchSets || !allPatchSets.length) { return undefined; }
  if (allPatchSets[0].num === SPECIAL_PATCH_SET_NUM.EDIT) {
    return allPatchSets[1].num;
  }
  return allPatchSets[0].num;
}

/** @return {boolean} */
export function hasEditBasedOnCurrentPatchSet(allPatchSets) {
  if (!allPatchSets || allPatchSets.length < 2) { return false; }
  return allPatchSets[0].num === SPECIAL_PATCH_SET_NUM.EDIT;
}

/** @return {boolean} */
export function hasEditPatchsetLoaded(patchRangeRecord) {
  const patchRange = patchRangeRecord.base;
  if (!patchRange) { return false; }
  return patchRange.patchNum === SPECIAL_PATCH_SET_NUM.EDIT ||
      patchRange.basePatchNum === SPECIAL_PATCH_SET_NUM.EDIT;
}

/**
 * Check whether there is no newer patch than the latest patch that was
 * available when this change was loaded.
 *
 * @return {Promise<!Object>} A promise that yields true if the latest patch
 *     has been loaded, and false if a newer patch has been uploaded in the
 *     meantime. The promise is rejected on network error.
 */
export function fetchChangeUpdates(change, restAPI) {
  const knownLatest = computeLatestPatchNum(computeAllPatchSets(change));
  return restAPI.getChangeDetail(change._number)
      .then(detail => {
        if (!detail) {
          const error = new Error('Unable to check for latest patchset.');
          return Promise.reject(error);
        }
        const actualLatest = computeLatestPatchNum(computeAllPatchSets(detail));
        return {
          isLatest: actualLatest <= knownLatest,
          newStatus: change.status !== detail.status ? detail.status : null,
          newMessages: change.messages.length < detail.messages.length,
        };
      });
}

/**
 * @param {number|string} patchNum
 * @param {!Array<!Object>} revisions A sorted array of revisions.
 *
 * @return {number} The index of the revision with the given patchNum.
 */
export function findSortedIndex(patchNum, revisions) {
  revisions = revisions || [];
  const findNum = rev => rev._number + '' === patchNum + '';
  return revisions.findIndex(findNum);
}

/**
 * Convert parent indexes from patch range expressions to numbers.
 * For example, in a patch range expression `"-3"` becomes `3`.
 *
 * @param {number|string} rangeBase
 * @return {number}
 */
export function getParentIndex(rangeBase) {
  return -parseInt(rangeBase + '', 10);
}
