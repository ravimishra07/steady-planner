// Port of feature/syllabus/SyllabusViewModel.kt
//
// Coverage reads from TopicProgressRepository (the one source of truth for "what have I covered").
// Open/expanded rows are UI-only state kept in memory here — not persisted.
// viewModelScope.launch → fire-and-forget async; System.currentTimeMillis → nowMs().
import { MutableStateFlow } from '../../platform/flow.js';
import { nowMs as clockNowMs } from '../../domain/time.js';
import { ChapterStage } from '../../domain/ChapterStage.js';
import { chapterRevisionCount } from '../../domain/ChapterStage.js';
import { PyqPriorityTable } from '../../domain/ChapterPriority.js';
import { findNode, leafIds, totalMinutes } from '../../domain/ExamPack.js';
import { coverage, syllabusMetrics, targetStates } from '../../domain/Metrics.js';
import { SyllabusTickState } from '../../domain/Syllabus.js';
import { TargetNodeState, isNodeEffectivelyExcluded } from '../../domain/TargetSyllabus.js';
import { cleanChapterTitle, withChapterTitles } from '../../domain/ChapterTitles.js';
import { StageLabels } from '../../domain/ChapterStage.js';
import {
  SyllabusUiState, SyllabusTreeRow, StageChipUi, SyllabusChapterStat, SyllabusSubjectCard,
} from './SyllabusUiState.js';

const sumOf = (xs, f) => xs.reduce((a, x) => a + f(x), 0);
const launch = (fn) => { Promise.resolve().then(fn).catch((e) => console.error(e)); };
const toIntOrNull = (s) => (s != null && /^[+-]?\d+$/.test(s) ? parseInt(s, 10) : null);

export class SyllabusViewModel {
  constructor(
    examPackRepository, topicProgressRepository, attemptRepository, targetSyllabusRepository,
    rollingPlanRepository, chapterStageRepository, stageLabelStore, proRepository,
  ) {
    this.examPackRepository = examPackRepository;
    this.topicProgressRepository = topicProgressRepository;
    this.attemptRepository = attemptRepository;
    this.targetSyllabusRepository = targetSyllabusRepository;
    this.rollingPlanRepository = rollingPlanRepository;
    this.chapterStageRepository = chapterStageRepository;
    this.stageLabelStore = stageLabelStore;
    this.proRepository = proRepository;

    this._state = new MutableStateFlow(SyllabusUiState());
    this.state = this._state.asStateFlow();
    /** CONTRACT name: uiState */
    this.uiState = this.state;

    this.pack = null;
    /** The pack with bundled titles, for "Reset to original name". */
    this.basePack = null;
    this.chapterTitles = new Map();
    this.attemptId = null;
    this.progress = [];
    this.metrics = null;
    this.openNodeIds = new Set();
    this.targetOverrides = new Map();
    this.classGroupLeafIds = new Map();
    this.defaultExpansionSeeded = false;
    this.priorityTable = PyqPriorityTable.EMPTY;
    this.priorityLoaded = false;
    this.stageMarks = new Map();
    this.revisionStates = new Map();
    this.stageLabels = StageLabels();
    this.coaching = new Map();

    this.refresh();
    this._unsubPro = proRepository.unlocked.subscribe((unlocked) => {
      this._state.update((it) => it.copy({ proUnlocked: unlocked }));
    });
  }

  get coveredNodeIds() { return this.metrics?.coveredIds ?? new Set(); }

  onCleared() { this._unsubPro?.(); this._cleared = true; }

  refresh() {
    launch(async () => {
      const attempt = await this.attemptRepository.activeAttempt();
      this.attemptId = attempt?.id ?? null;
      this.basePack = attempt ? await this.examPackRepository.examPackFor(attempt.examId) : null;
      this.chapterTitles = attempt ? await this.examPackRepository.chapterTitles(attempt.id) : new Map();
      this.pack = this.basePack ? withChapterTitles(this.basePack, this.chapterTitles) : null;
      this.targetOverrides = attempt ? targetStates(await this.targetSyllabusRepository.overridesFor(attempt.id)) : new Map();
      await this.reloadProgress();
      if (!this.priorityLoaded) {
        // Off the first-paint path: the tree shows first, the yield labels fill in after.
        this.priorityTable = await this.examPackRepository.pyqPriorities();
        this.priorityLoaded = true;
        this.rebuild();
      }
    });
  }

  async reloadProgress() {
    const id = this.attemptId;
    if (id == null) { this.rebuild(); return; }
    this.progress = await this.topicProgressRepository.allOnce(id);
    const marks = await this.chapterStageRepository.marksFor(id);
    const grouped = new Map();
    for (const m of marks) {
      if (!grouped.has(m.chapterId)) grouped.set(m.chapterId, new Map());
      grouped.get(m.chapterId).set(m.stage, m);
    }
    this.stageMarks = grouped;
    this.revisionStates = await this.chapterStageRepository.revisionStatesFor(id);
    this.coaching = new Map((await this.chapterStageRepository.coachingFor(id)).map((c) => [c.chapterId, c]));
    this.stageLabels = await this.stageLabelStore.load();
    this.rebuild();
  }

  toggleExpand(key) {
    const next = new Set(this.openNodeIds);
    if (next.has(key)) next.delete(key); else next.add(key);
    this.openNodeIds = next;
    this.rebuild();
  }

  toggleTick(key) {
    const examPack = this.pack;
    if (!examPack || examPack.subjects.length === 0) {
      this._state.value = SyllabusUiState({ loading: false, isEmpty: true });
      return;
    }
    const id = this.attemptId;
    if (id == null) return;
    const leaves = this.classGroupLeafIds.get(key) ?? (() => { const n = findNode(examPack, key); return n ? leafIds(n) : null; })();
    if (leaves == null) return;
    const covered = this.coveredNodeIds;
    const allDone = leaves.length > 0 && leaves.every((it) => covered.has(it));
    launch(async () => {
      const nowMs = clockNowMs();
      await this.topicProgressRepository.setCovered(id, leaves, !allDone, nowMs);
      await this.rollingPlanRepository.replenish({ nowMs });
      await this.reloadProgress();
    });
  }

  /** Learned is the chapter's covered tick; Revised adds one; every other stage toggles. */
  tapStage(chapterId, stage) {
    if (stage === ChapterStage.LEARNED) { this.toggleTick(chapterId); return; }
    const id = this.attemptId;
    if (id == null) return;
    launch(async () => {
      const nowMs = clockNowMs();
      if (stage === ChapterStage.REVISED) {
        await this.chapterStageRepository.adjustCount(id, chapterId, stage, 1, nowMs);
      } else {
        await this.chapterStageRepository.toggle(id, chapterId, stage, nowMs);
      }
      await this.reloadProgress();
    });
  }

  /** Long-press on Revised takes back one hand-logged revision. */
  undoRevision(chapterId) {
    const id = this.attemptId;
    if (id == null) return;
    launch(async () => {
      await this.chapterStageRepository.adjustCount(id, chapterId, ChapterStage.REVISED, -1, clockNowMs());
      await this.reloadProgress();
    });
  }

  /** Renames a chapter everywhere. Null, blank or the original title resets it. */
  renameChapter(chapterId, title) {
    const id = this.attemptId;
    if (id == null) return;
    const original = this.basePack ? findNode(this.basePack, chapterId)?.title : null;
    if (original == null) return;
    launch(async () => {
      await this.examPackRepository.renameChapter(id, chapterId, cleanChapterTitle(title, original));
      this.chapterTitles = await this.examPackRepository.chapterTitles(id);
      this.pack = this.basePack ? withChapterTitles(this.basePack, this.chapterTitles) : null;
      this.rebuild();
      // Backlog blocks carry the chapter's name; replan so they pick up the new one.
      await this.rollingPlanRepository.replenish();
    });
  }

  /** Park or unpark a chapter: the same exclusion Organise writes, then the plan refills. */
  toggleParked(chapterId) {
    const id = this.attemptId;
    if (id == null) return;
    launch(async () => {
      const nowMs = clockNowMs();
      if (this.targetOverrides.get(chapterId) === TargetNodeState.EXCLUDED) {
        await this.targetSyllabusRepository.clearOverride(id, chapterId);
      } else {
        await this.targetSyllabusRepository.setState(id, chapterId, TargetNodeState.EXCLUDED, nowMs);
      }
      this.targetOverrides = targetStates(await this.targetSyllabusRepository.overridesFor(id));
      await this.rollingPlanRepository.replenish({ nowMs });
      this.rebuild();
    });
  }

  /** Saves a chapter's coaching lecture/DPP counts; the backlog share of the plan follows. */
  saveCoaching(counts) {
    const id = this.attemptId;
    if (id == null) return;
    launch(async () => {
      await this.chapterStageRepository.saveCoaching(id, counts);
      await this.rollingPlanRepository.replenish();
      await this.reloadProgress();
    });
  }

  saveStageLabels(labels) {
    launch(async () => {
      // Renaming and hiding are free; adding the custom stage is Pro. One added while Pro stays editable.
      const allowed = this.stageLabels.customLabel == null && !(await this.proRepository.unlockedOnce())
        ? labels.copy({ customLabel: null }) : labels;
      await this.stageLabelStore.save(allowed);
      this.stageLabels = await this.stageLabelStore.load();
      this.rebuild();
    });
  }

  stageChipsFor(node, learned) {
    const marks = this.stageMarks.get(node.id) ?? new Map();
    return this.stageLabels.visibleStages().map((stage) => {
      let count;
      if (stage === ChapterStage.LEARNED) count = learned ? 1 : 0;
      else if (stage === ChapterStage.REVISED) count = chapterRevisionCount(node, this.revisionStates, marks.get(stage)?.count ?? 0);
      else count = marks.has(stage) ? 1 : 0;
      return StageChipUi({ stage, customLabel: this.stageLabels.labelFor(stage), count });
    });
  }

  rebuild() {
    const examPack = this.pack;
    if (!examPack) return;
    const canonical = syllabusMetrics(examPack, this.targetOverrides, this.progress);
    this.metrics = canonical;
    const covered = this.coveredNodeIds;
    if (!this.defaultExpansionSeeded) {
      const subject = examPack.subjects[0];
      if (subject) {
        const open = new Set(this.openNodeIds);
        open.add(subjectCardKey(subject.id));
        const classNumber = subject.nodes.map(classNumberFor).find((n) => n != null);
        if (classNumber != null) {
          open.add(classGroupKey(subject.id, classNumber));
          const firstChapter = subject.nodes.find((node) => classNumberFor(node) === classNumber && node.children.length > 0);
          if (firstChapter) open.add(firstChapter.id);
        }
        this.openNodeIds = open;
      }
      this.defaultExpansionSeeded = true;
    }
    const rebuiltClassGroups = new Map();
    const subjects = examPack.subjects.map((subject) => {
      const cardKey = subjectCardKey(subject.id);
      // groupBy + toSortedMap
      const groups = new Map();
      for (const node of subject.nodes) {
        const n = classNumberFor(node);
        if (n == null) continue;
        if (!groups.has(n)) groups.set(n, []);
        groups.get(n).push(node);
      }
      const classGroups = new Map([...groups.entries()].sort((a, b) => a[0] - b[0]));
      const ungroupedNodes = subject.nodes.filter((it) => classNumberFor(it) == null);
      const rows = [
        ...this.buildClassRows(subject.id, subject.name, classGroups, rebuiltClassGroups),
        ...ungroupedNodes.flatMap((node, index) => this.buildRows(node, [], index === ungroupedNodes.length - 1, subject.id, subject.name)),
      ];

      const allLeaves = new Set(leafIds(subject));
      const includedLeaves = [...canonical.targetLeafIds].filter((id) => allLeaves.has(id));
      const nextLeafId = includedLeaves.find((it) => !covered.has(it)) ?? null;
      const nextLeaf = nextLeafId != null ? findNode(examPack, nextLeafId) : null;
      const percent = canonical.bySubject.get(subject.id)?.percent ?? 0;
      const excludedCount = subject.nodes.filter((node) => isNodeEffectivelyExcluded(node.id, [], this.targetOverrides)).length;
      const complete = (node) => leafIds(node).every((it) => covered.has(it));

      return SyllabusSubjectCard({
        key: cardKey,
        name: subject.name,
        subjectId: subject.id,
        percent,
        expanded: this.openNodeIds.has(cardKey),
        rows,
        firstTopicKey: nextLeafId,
        firstTopicTitle: nextLeaf?.title ?? '',
        doneChapterCount: subject.nodes.filter(complete).length,
        totalChapterCount: subject.nodes.length,
        totalEstimatedMinutes: sumOf(subject.nodes, (it) => totalMinutes(it)),
        questions: subject.questions ?? 0,
        chapterStats: subject.nodes.map((node) => SyllabusChapterStat({ classNumber: classNumberFor(node), complete: complete(node) })),
        excludedCount,
      });
    });
    this.classGroupLeafIds = rebuiltClassGroups;

    this._state.update(() => SyllabusUiState({
      loading: false,
      subjects,
      allCount: subjects.length,
      dueCount: subjects.filter((it) => it.percent < 100).length,
      completedPercent: canonical.overall.percent,
      pyqFirstYear: this.priorityTable.firstYear,
      pyqLastYear: this.priorityTable.lastYear,
      stageLabels: this.stageLabels,
    }));
  }

  buildClassRows(subjectId, subjectName, classGroups, classGroupLeaves) {
    const covered = this.coveredNodeIds;
    const entries = [...classGroups.entries()];
    return entries.flatMap(([classNumber, chapters], groupIndex) => {
      const key = classGroupKey(subjectId, classNumber);
      const leaves = chapters.flatMap((c) => leafIds(c));
      classGroupLeaves.set(key, leaves);
      const doneCount = leaves.filter((it) => covered.has(it)).length;
      const tickState = leaves.length === 0 || doneCount === 0 ? SyllabusTickState.NONE
        : doneCount === leaves.length ? SyllabusTickState.ALL : SyllabusTickState.PARTIAL;
      const header = SyllabusTreeRow({
        key,
        name: '',
        estimatedMinutes: sumOf(chapters, (c) => totalMinutes(c)),
        depth: 0,
        hasChildren: chapters.length > 0,
        expanded: this.openNodeIds.has(key),
        tickState,
        percent: coverage(new Set(leaves), covered).percent,
        doneLeafCount: doneCount,
        totalLeafCount: leaves.length,
        ancestorContinues: [],
        isLastChild: groupIndex === entries.length - 1,
        subjectId,
        sectionName: subjectName,
        topicPath: subjectName,
        classNumber,
        isClassGroup: true,
      });
      if (!this.openNodeIds.has(key)) return [header];
      return [header, ...chapters.flatMap((chapter, chapterIndex) =>
        this.buildRows(chapter, [false], chapterIndex === chapters.length - 1, subjectId, subjectName))];
    });
  }

  buildRows(node, ancestorContinues, isLast, subjectId, sectionName, ancestorIds = []) {
    const covered = this.coveredNodeIds;
    const leaves = leafIds(node);
    const doneCount = leaves.filter((it) => covered.has(it)).length;
    const percent = coverage(new Set(leaves), covered).percent;
    let tickState;
    if (leaves.length === 0) tickState = SyllabusTickState.NONE;
    else if (doneCount === 0) tickState = SyllabusTickState.NONE;
    else if (doneCount === leaves.length) tickState = SyllabusTickState.ALL;
    else tickState = SyllabusTickState.PARTIAL;
    const top = ancestorIds.length === 0;
    const row = SyllabusTreeRow({
      key: node.id,
      name: node.title,
      estimatedMinutes: totalMinutes(node),
      depth: ancestorContinues.length,
      hasChildren: node.children.length > 0,
      expanded: this.openNodeIds.has(node.id),
      tickState,
      percent,
      doneLeafCount: doneCount,
      totalLeafCount: leaves.length,
      ancestorContinues,
      isLastChild: isLast,
      subjectId,
      sectionName,
      topicPath: sectionName,
      classNumber: toIntOrNull(node.id.split('.')[2]),
      excluded: isNodeEffectivelyExcluded(node.id, ancestorIds, this.targetOverrides),
      priority: top && this.priorityLoaded ? this.priorityTable.forChapter(node.id) : null,
      isChapter: top,
      coaching: top ? (this.coaching.get(node.id) ?? null) : null,
      renamed: top && this.chapterTitles.has(node.id),
      originalName: top && this.chapterTitles.has(node.id)
        ? ((this.basePack ? findNode(this.basePack, node.id)?.title : null) ?? '')
        : node.title,
      stageChips: top ? this.stageChipsFor(node, tickState === SyllabusTickState.ALL) : [],
    });
    if (node.children.length === 0 || !this.openNodeIds.has(node.id)) return [row];
    const childAncestors = [...ancestorContinues, !isLast];
    const childRows = node.children.flatMap((child, index) =>
      this.buildRows(child, childAncestors, index === node.children.length - 1, subjectId, sectionName, [...ancestorIds, node.id]));
    return [row, ...childRows];
  }
}

function subjectCardKey(subjectId) { return `subject_${subjectId}`; }
function classGroupKey(subjectId, classNumber) { return `class_${subjectId}_${classNumber}`; }
function classNumberFor(node) { return toIntOrNull(node.id.split('.')[2]); }
