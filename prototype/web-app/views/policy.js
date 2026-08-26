const BACK =
  '<svg width="18" height="18" viewBox="0 0 20 20" fill="none"><path d="M12.5 4.5L7 10l5.5 5.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>';

export const APP_VERSION = '0.1.0';

const DOCS = {
  privacy: {
    title: 'Privacy',
    body: [
      ['What we collect', 'Nothing. There is no account, no analytics and no server. The app never sends your data anywhere.'],
      ['What is stored', 'Your exam, date, available hours, study place, ticked topics and focus sessions are saved in this browser’s local storage on this device.'],
      ['Who can see it', 'Only you, on this device. We cannot see it, and it is not synced or backed up.'],
      ['Deleting it', 'Settings → Clear plan, or Account → Sign out. Clearing your browser data for this site removes it too. Deletion is immediate and permanent.'],
      ['Third parties', 'The app loads no third-party scripts, fonts or trackers.'],
    ],
  },
  terms: {
    title: 'Terms of use',
    body: [
      ['What this is', 'A study planner. It estimates how many hours a syllabus needs and compares that with the hours you say you have.'],
      ['What it is not', 'It does not contain study material — no notes, questions or solutions. You bring your own books.'],
      ['Accuracy', 'Hour estimates are approximations built from published syllabi and common coaching breakdowns. They are a planning aid, not a guarantee of any result.'],
      ['Your responsibility', 'Confirm exam dates and syllabus scope against the official notification before relying on them.'],
      ['Availability', 'This is a prototype. Data lives only on your device and can be lost if you clear your browser.'],
    ],
  },
  about: {
    title: 'About Steadyline',
    body: [
      ['Version', 'Prototype ' + APP_VERSION + ' · offline web app'],
      ['The idea', 'Most planners ask what you want to do. This one starts from the arithmetic: the syllabus needs a number of hours, your calendar supplies another, and the gap between them is the only thing that matters.'],
      ['Syllabus source', 'SSC CGL scope follows the official notification (Paras 13.9 and 13.10). Topic breakdowns and hour weights are compiled from widely used preparation books and are indicative, not official.'],
      ['Status', 'Screens are wired and usable. The scheduler that generates the daily plan is still fixed demo data.'],
    ],
  },
};

export function mountPolicy(root, { navigate }, which) {
  const doc = DOCS[which] || DOCS.about;
  root.className = 'view view-policy';
  root.innerHTML = `
    <div class="navbar">
      <button type="button" class="glass-btn back-chev" aria-label="Back">${BACK}</button>
      <span class="nav-title">${doc.title}</span>
      <span class="nav-spacer"></span>
    </div>
    <div class="view-scroll settings-scroll">
      <div class="card prose">
        ${doc.body.map(([h, p]) => `<section><h2>${h}</h2><p>${p}</p></section>`).join('')}
      </div>
      <p class="foot-note">Last updated with prototype ${APP_VERSION}</p>
    </div>`;

  root.querySelector('.back-chev').addEventListener('click', () => navigate('more'));
  return () => {};
}
