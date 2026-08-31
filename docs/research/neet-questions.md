# What we need to know before building further

Questions, not answers. Everything numeric in the app today is derived from a
900-hour guess divided by chapter count, and every assumption about a student's
week came from one interview. This is the list of what has to be found out, and
what each answer would actually change.

Mark each question when answered, with the source. Anything we cannot source,
we label an estimate in the product — the way `PACK.meta.methodology` already
does — or we do not show it.

---

## 1. The exam itself

- What is the real NEET UG date each year, and when is it announced? Our default
  target date is currently `today + 118`, which is wrong for everybody.
- Paper: 180 questions, 720 marks, 200 minutes, +4 / −1 — confirm for the
  current cycle, including any section-choice rules.
- What changed in the rationalised syllabus, chapter by chapter? Which NCERT
  sections are out of scope now? Students get this wrong and no tracker says.
- **Chapter-wise question frequency across the last 10 papers.** This is the
  single most valuable dataset for us. It replaces our invented flat weighting
  and is the basis of any honest "high-yield" ordering.
- How many marks does a chapter's typical question carry in practice — is
  weightage stable year to year, or does it swing?
- What score maps to what rank, roughly, and how does that shift with the number
  of candidates? Needed before we ever say "on track" about an outcome.
- How do state quota, category and domicile change what a student is aiming at?
  A 600 is a different verdict for different people.

## 2. The coaching landscape

- Allen, Aakash, PW, Resonance, Narayana, Sri Chaitanya, local institutes — what
  share of NEET aspirants attend each, and how many attend none?
- **What order does each teach in?** Their module sequence, per subject. If we
  can ship "pick your institute → your sequence is right", nobody has to sort a
  hundred chapters by hand.
- How many modules per subject, and how long does each take in class hours?
- What is the class schedule — days per week, hours per day, and does it change
  between the foundation phase and the revision phase?
- What is the test cycle? Weekly, fortnightly, monthly majors? What are they
  called, when do results come, and what do students do with the result?
- What material comes with it — modules, DPPs, sheets, test series — and how
  many questions a day is a student expected to do?
- Do institutes give their own planner or app? What does it do, and why do
  students still keep a paper diary alongside it?
- What does coaching cost: classroom in Kota, classroom in a home city, online
  only, and the free tiers? How does that split by family income?
- What does the hostel/mess/travel add on top in Kota?

## 3. What a student's week actually looks like

- Classroom student in Kota: hour by hour, a normal weekday and a Sunday.
- Same for a student attending coaching in their home city while at school.
- Same for an online-only PW student.
- Same for a pure self-study aspirant.
- When does self-study actually happen — before class, after class, late night?
  How many hours honestly, not aspirationally?
- What gets done in that time: DPPs, module questions, NCERT reading, revision,
  video lectures? In what proportion?
- How much of it happens away from a phone, and what would we therefore never
  see unless we ask?
- What breaks a week — illness, travel, festivals, school exams, board practicals?
  How often, and what does recovery look like?
- Who else is involved — parents, hostel wardens, friends studying together?
  Does anyone else need to see progress?

## 4. Repeaters — the segment we may be built for

- How many NEET aspirants are droppers? First-time repeater vs second vs third?
- **How does a repeater's year differ from a first-timer's?** They have covered
  everything at least once — is their year revision-shaped rather than
  learn-shaped from day one?
- Does a repeater join coaching again, and if so which programme? Is there a
  dedicated dropper batch with a different sequence and pace?
- How do they decide what to strengthen? By last year's scorecard? By subject?
  By a chapter list they already distrust?
- **What does "weak topic" mean to them operationally** — a chapter they scored
  badly in, one they avoid, or one they have never finished?
- How many times do they realistically go through the whole syllabus in a year,
  and what does pass two and pass three look like differently from pass one?
- What did they use last year, and what do they blame for the score they got?
- Is a repeater more or less likely to pay for an app? They have already spent a
  year and a lakh; do they buy tools or distrust them?
- Would a repeater-specific mode be a product, or a feature? "You have covered
  this before — start from your weak chapters" is a different first run.

## 5. Granularity — how deep should the tree go

- Subject → chapter → NCERT section is what we have. Is section the right leaf,
  or do students think in **topics** that cut across sections?
- Do coaching modules map cleanly onto NCERT chapters, or do they merge and split
  them? If they split, whose unit is the leaf?
- Would a student ever tick something smaller than a chapter, honestly, every
  day for a year? Or is chapter the only unit anyone maintains?
- What granularity does a DPP or a test result come at — chapter, topic, or a
  question tagged both ways?
- Is a finer tree worth it if the cost is more ticking? What is the ticking
  budget per day before it becomes work?

## 6. Revision — the thing we are betting on

- How do students revise now, without an app? Notes, formula sheets, re-reading
  NCERT, redoing DPPs, flashcards?
- Do they already revise on any schedule, or only before a test?
- Is chapter-level revision the right unit, or do they revise a whole subject or
  a whole module at a time?
- What triggers a re-visit in real life — a bad test score, a teacher saying so,
  a gut feeling, or a date?
- Would a student trust an app telling them what to revise today, over their own
  sense of what is weak?
- How does revision compete with new material in their head — do they consciously
  budget it, or does it get squeezed out?

## 7. Practice, questions and scores

- Where do questions come from: institute DPPs, NCERT exemplar, previous years,
  PW/Allen apps, printed books?
- Would a student type a score into an app after a DPP? How many seconds do we
  get before that becomes friction?
- What granularity of score is available and worth capturing — per DPP, per
  chapter, per subject, per test?
- Could we ever ship questions ourselves, or is the practice layer permanently
  someone else's? What does that mean for being the "third app"?
- What does a scorecard from a coaching test actually contain, and can a student
  copy it in under a minute?

## 8. Money

- What will a NEET aspirant pay for a study app, and who pays — the student or a
  parent?
- TrackIt anchors at ₹99/month, ₹399/year, ₹699 lifetime with 500K installs.
  Is lifetime the expected shape in this market?
- What do students already pay for besides coaching — test series, notes, apps?
- Does a free tier have to exist, and what is the honest boundary between free
  and paid for a planner?
- Is there a parent-facing angle worth money, and does the student resent it?

## 9. Distribution and trust

- Where do aspirants find apps — Play Store search, YouTube, Telegram groups,
  Instagram, seniors, coaching teachers?
- Who do they trust for study advice, and can that channel be reached without
  paying for it?
- What makes an app get uninstalled in week one, in their own words?
- What does a student expect from an app that claims to know the syllabus? What
  would make them decide it is wrong and stop trusting the numbers?

## 10. Constraints we have not tested

- What phones is this running on? RAM, Android version, screen size at the low
  end of the market.
- Is data always available? Should any of this work offline, and is offline a
  feature we can charge for or a baseline?
- Is English enough, or does Hindi matter for a large share of the market? What
  about chapter names — do students use English names in a Hindi-medium batch?
- Do students want notifications at all, or is that the fastest route to an
  uninstall in a house where the phone is already contentious?
- Is a phone even allowed? Kota hostels and some batches restrict phones — does
  our whole premise survive that, and is there a desktop or web need?

---

## How to answer these

In rough order of cost:

1. **Desk research** — exam pattern, dates, rationalised syllabus, coaching
   prices, chapter-wise weightage. Most of section 1 and parts of 2 and 8.
2. **Store and forum mining** — reviews of TrackIt and the coaching apps,
   r/NEET, Telegram groups, YouTube comments. Sections 9 and parts of 3.
3. **Talking to actual aspirants** — everything in sections 3, 4, 5, 6, 7. Ten
   conversations would answer more than a month of building. Aim for a spread:
   Kota classroom, home-city classroom, online-only, self-study, and at least
   three repeaters.

## What each answer changes

| Answer | What it unblocks |
|---|---|
| Chapter-wise PYQ frequency | Real weighting; kills the flat 8h / 6-marks fiction; makes "high-yield first" a real ordering |
| Coaching module sequences | Sequence correct on day one, without the daily class log or manual reordering |
| Real hours per chapter | Every projection, finish date and "misses the exam" warning we show |
| Repeater year shape | Whether there is a second product here, or a different first run |
| Right leaf granularity | Whether the 292 subtopics are an asset or a liability |
| Score capture reality | Whether the test-driven loop can exist at all |
| Price expectation | Free/paid boundary, and whether lifetime has to be offered |
| Phone and language constraints | Whether the whole premise holds for the low end of the market |
