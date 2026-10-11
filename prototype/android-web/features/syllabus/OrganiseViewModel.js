// Port of feature/syllabus/OrganiseViewModel.kt
// viewModelScope.launch → fire-and-forget async; LocalDate.now() → today(); UUID → randomUUID().
import { MutableStateFlow } from '../../platform/flow.js';
import { deepEquals } from '../../domain/kt.js';
import { today as localToday, nowMs as clockNowMs, plusDays } from '../../domain/time.js';
import { PyqPriorityTable } from '../../domain/ChapterPriority.js';
import { findNode, totalMinutes } from '../../domain/ExamPack.js';
import { syllabusMetrics } from '../../domain/Metrics.js';
import { PlanningOrder } from '../../domain/RollingPlan.js';
import { StudyActivityType } from '../../domain/StudyPlan.js';
import { TargetNodeState } from '../../domain/TargetSyllabus.js';
import { cleanChapterTitle, withChapterTitles } from '../../domain/ChapterTitles.js';
import { CustomSyllabusChapter } from '../../data/ExamPackRepository.js';
import { randomUUID, runCatching } from '../../data/common.js';
import { OrganiseUiState, OrganiseChapterRow, OrganiseDaySummary, OrganisePlanRow } from './OrganiseUiState.js';

const launch = (fn) => { Promise.resolve().then(fn).catch((e) => console.error(e)); };
const excludedMap = (ids) => new Map([...ids].map((id) => [id, TargetNodeState.EXCLUDED]));

export class OrganiseViewModel {
  constructor(
    examPackRepository, attemptRepository, targetSyllabusRepository, planRepository,
    studyPreferenceRepository, rollingPlanRepository, topicProgressRepository,
  ) {
    this.examPackRepository = examPackRepository;
    this.attemptRepository = attemptRepository;
    this.targetSyllabusRepository = targetSyllabusRepository;
    this.planRepository = planRepository;
    this.studyPreferenceRepository = studyPreferenceRepository;
    this.rollingPlanRepository = rollingPlanRepository;
    this.topicProgressRepository = topicProgressRepository;

    this._state = new MutableStateFlow(OrganiseUiState());
    this.state = this._state.asStateFlow();
    this.uiState = this.state;

    this.pack = null;
    this.basePack = null;
    this.chapterTitles = new Map();
    this.originalExcluded = new Set();
    this.draftExcluded = new Set();
    this.originalCustom = [];
    this.draftCustom = [];
    this.originalOrder = PlanningOrder.DEFAULT;
    this.draftOrder = PlanningOrder.DEFAULT;
    this.originalRows = [];
    this.progress = [];
    this.originalRemainingMinutes = 0;
    this.priorityTable = PyqPriorityTable.EMPTY;
    this.calibrationFactor = 1.0;

    this.refresh();
  }

  refresh() {
    launch(async () => {
      const attempt = await this.attemptRepository.activeAttempt();
      if (!attempt) { this._state.value = OrganiseUiState({ loading: false }); return; }
      this.basePack = await this.examPackRepository.examPackFor(attempt.examId);
      this.chapterTitles = await this.examPackRepository.chapterTitles(attempt.id);
      this.pack = this.basePack ? withChapterTitles(this.basePack, this.chapterTitles) : null;
      this.originalCustom = await this.examPackRepository.customChapters(attempt.examId);
      this.draftCustom = this.originalCustom;
      this.originalExcluded = new Set((await this.targetSyllabusRepository.overridesFor(attempt.id))
        .filter((it) => it.state === TargetNodeState.EXCLUDED).map((it) => it.nodeId));
      this.draftExcluded = this.originalExcluded;
      this.originalOrder = (await this.studyPreferenceRepository.forAttempt(attempt.id))?.planningOrder ?? PlanningOrder.DEFAULT;
      this.draftOrder = this.originalOrder;
      this.originalRows = this.rowsFor(this.pack);
      this.progress = await this.topicProgressRepository.allOnce(attempt.id);
      this.priorityTable = await this.examPackRepository.pyqPriorities();
      this.calibrationFactor = (await this.rollingPlanRepository.calibration()).factor;
      this.originalRemainingMinutes = this.remainingMinutesFor(this.pack, this.originalExcluded);
      await this.refreshPreview();
    });
  }

  selectSubject(subjectId) { this._state.update((it) => it.copy({ selectedSubjectId: subjectId })); }

  setOrder(order) {
    this.draftOrder = order;
    this.refreshPreviewAsync();
  }

  toggleIncluded(nodeId) {
    const next = new Set(this.draftExcluded);
    if (next.has(nodeId)) next.delete(nodeId); else next.add(nodeId);
    this.draftExcluded = next;
    this.refreshPreviewAsync();
  }

  addChapter(title, hours, subjectName) {
    if (title.trim() === '' || hours < 0.5) return;
    const s = this._state.value;
    const selected = s.selectedSubjectId.trim() === '' ? 'custom' : s.selectedSubjectId;
    this.draftCustom = [...this.draftCustom, CustomSyllabusChapter({
      id: `custom_${randomUUID()}`,
      examId: s.examId,
      subjectId: selected,
      subjectName: s.subjects.find((it) => it[0] === selected)?.[1] ?? subjectName,
      title: title.trim(),
      estimatedMinutes: Math.trunc(hours * 60),
    })];
    this.refreshDraftPackAndPreview();
  }

  removeChapter(id) {
    this.draftCustom = this.draftCustom.filter((it) => it.id !== id);
    const next = new Set(this.draftExcluded); next.delete(id);
    this.draftExcluded = next;
    this.refreshDraftPackAndPreview();
  }

  /** Renames take effect at once, like on Syllabus: a name is not a plan change to preview. */
  renameChapter(chapterId, title) {
    launch(async () => {
      const attempt = await this.attemptRepository.activeAttempt();
      if (!attempt) return;
      const original = this.basePack ? findNode(this.basePack, chapterId)?.title : null;
      if (original == null) return;
      await this.examPackRepository.renameChapter(attempt.id, chapterId, cleanChapterTitle(title, original));
      this.chapterTitles = await this.examPackRepository.chapterTitles(attempt.id);
      this.pack = this.basePack ? withChapterTitles(this.basePack, this.chapterTitles) : null;
      this.originalRows = this.originalRows.map((row) => row.copy({ title: this.chapterTitles.get(row.id) ?? row.originalTitle }));
      await this.refreshPreview();
      await this.rollingPlanRepository.replenish();
    });
  }

  discard() {
    this.draftCustom = this.originalCustom;
    this.draftExcluded = this.originalExcluded;
    this.draftOrder = this.originalOrder;
    this.refreshDraftPackAndPreview();
  }

  apply() {
    launch(async () => {
      const attempt = await this.attemptRepository.activeAttempt();
      if (!attempt) return;
      const previousPreferences = await this.studyPreferenceRepository.forAttempt(attempt.id);
      if (!previousPreferences) return;
      const today = localToday();
      const previousPlan = await this.planRepository.planBetween(attempt.id, today, attempt.targetCompletionDate);
      this._state.update((it) => it.copy({ saving: true, saveFailed: false }));

      const result = await runCatching(async () => {
        await this.examPackRepository.saveCustomChapters(attempt.examId, this.draftCustom);
        await this.targetSyllabusRepository.replaceStates(attempt.id, excludedMap(this.draftExcluded), clockNowMs());
        await this.studyPreferenceRepository.upsert(previousPreferences.copy({ planningOrder: this.draftOrder }));
        const r = await this.rollingPlanRepository.replenish({ fromDate: today });
        if (r == null) throw new Error('IllegalStateException: Required value was null.');
      });
      if (result.ok) {
        this.originalCustom = this.draftCustom;
        this.originalExcluded = this.draftExcluded;
        this.originalOrder = this.draftOrder;
        if (!this.pack) return;
        this.originalRows = this.rowsFor(this.pack);
        this.originalRemainingMinutes = this.remainingMinutesFor(this.pack, this.originalExcluded);
        this._state.update((it) => it.copy({ saving: false, saved: true }));
      } else {
        await runCatching(() => this.examPackRepository.saveCustomChapters(attempt.examId, this.originalCustom));
        await runCatching(() => this.targetSyllabusRepository.replaceStates(attempt.id, excludedMap(this.originalExcluded), clockNowMs()));
        await runCatching(() => this.studyPreferenceRepository.upsert(previousPreferences));
        await runCatching(() => this.planRepository.replaceFutureAuto(attempt.id, today, previousPlan));
        this._state.update((it) => it.copy({ saving: false, saveFailed: true }));
      }
    });
  }

  consumeSaved() { this._state.update((it) => it.copy({ saved: false })); }

  refreshDraftPackAndPreview() {
    launch(async () => {
      this.basePack = await this.examPackRepository.packWithCustom(this._state.value.examId, this.draftCustom);
      this.pack = this.basePack ? withChapterTitles(this.basePack, this.chapterTitles) : null;
      await this.refreshPreview();
    });
  }

  refreshPreviewAsync() { launch(() => this.refreshPreview()); }

  async refreshPreview() {
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return;
    const currentPack = this.pack;
    if (!currentPack) return;
    const preview = await this.rollingPlanRepository.preview(currentPack, excludedMap(this.draftExcluded), this.draftOrder);
    const today = localToday();
    const blocks = preview?.blocks ?? [];
    const week = [0, 1, 2, 3, 4, 5, 6].map((offset) => {
      const date = plusDays(today, offset);
      const day = blocks.filter((it) => it.scheduledDate === date);
      return OrganiseDaySummary({ date, plannedMinutes: day.reduce((a, b) => a + b.plannedMinutes, 0), blockCount: day.length });
    });
    const todayRows = blocks
      .filter((it) => it.scheduledDate === today)
      .slice().sort((a, b) => a.startMinuteOfDay - b.startMinuteOfDay)
      .map((block) => OrganisePlanRow({
        id: block.id,
        title: block.customTitle ?? (block.nodeId != null ? findNode(currentPack, block.nodeId)?.title : null) ?? '',
        startMinuteOfDay: block.startMinuteOfDay,
        durationMinutes: block.plannedMinutes,
        isRevision: block.activityType === StudyActivityType.REVISION,
      }));
    this.rebuild(attempt.examId, week, todayRows);
  }

  /** Uncovered work in the chapters that are in play, through the same metric every other screen uses. */
  remainingMinutesFor(currentPack, excluded) {
    return syllabusMetrics(currentPack, excludedMap(excluded), this.progress, this.calibrationFactor).remainingMinutes;
  }

  rowsFor(currentPack) {
    const customIds = new Set(this.draftCustom.map((it) => it.id));
    return currentPack.subjects.flatMap((subject) => subject.nodes.map((node) => OrganiseChapterRow({
      id: node.id,
      subjectId: subject.id,
      subjectName: subject.name,
      title: node.title,
      originalTitle: this.chapterTitles.has(node.id) ? ((this.basePack ? findNode(this.basePack, node.id)?.title : null) ?? node.title) : node.title,
      renamed: this.chapterTitles.has(node.id),
      estimatedMinutes: totalMinutes(node),
      excluded: this.draftExcluded.has(node.id),
      custom: customIds.has(node.id),
      priority: this.priorityTable.forChapter(node.id),
    })));
  }

  rebuild(examId, week, todaySessions) {
    const currentPack = this.pack;
    if (!currentPack) return;
    let subjects = currentPack.subjects.map((it) => [it.id, it.name]);
    if (subjects.length === 0) subjects = [['custom', 'My syllabus']];
    const prevSelected = this._state.value.selectedSubjectId;
    const selected = subjects.some((it) => it[0] === prevSelected) ? prevSelected : subjects[0][0];
    const rows = this.rowsFor(currentPack);
    const originalById = new Map(this.originalRows.map((it) => [it.id, it]));
    const ids = [...new Set([...rows.map((it) => it.id), ...this.originalRows.map((it) => it.id)])];
    const changedChapters = ids.filter((id) => {
      const before = originalById.get(id);
      const after = rows.find((it) => it.id === id);
      return before?.excluded !== after?.excluded || before?.estimatedMinutes !== after?.estimatedMinutes;
    }).length;
    const prev = this._state.value;
    this._state.value = OrganiseUiState({
      loading: false,
      saving: prev.saving,
      saveFailed: prev.saveFailed,
      examId,
      selectedSubjectId: selected,
      subjects,
      chapters: rows,
      week,
      todaySessions,
      beforeIncludedCount: this.originalRows.filter((it) => !it.excluded).length,
      remainingMinutes: this.remainingMinutesFor(currentPack, this.draftExcluded),
      beforeRemainingMinutes: this.originalRemainingMinutes,
      changedChapterCount: changedChapters,
      planningOrder: this.draftOrder,
      orderChanged: this.draftOrder !== this.originalOrder,
      dirty: !deepEquals(this.draftExcluded, this.originalExcluded) || !deepEquals(this.draftCustom, this.originalCustom) || this.draftOrder !== this.originalOrder,
      pyqFirstYear: this.priorityTable.firstYear,
      pyqLastYear: this.priorityTable.lastYear,
    });
  }
}
