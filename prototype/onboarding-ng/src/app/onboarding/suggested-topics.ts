/**
 * Topics coaching classes teach inside each NCERT chapter, offered as
 * suggestions when a student edits a chapter's topic list.
 *
 * The NCERT section headings in class11-subtopics.ts are what the book prints.
 * These are what a class actually calls the units it teaches — the book's
 * "7.3 Moment of inertia" is three separate weeks of "standard bodies",
 * "parallel and perpendicular axes" and "rolling". Students track the second
 * list, so the app has to offer it.
 *
 * `src` names who teaches it. It orders the suggestions — a topic every class
 * covers is offered before one only a single class splits out.
 *
 * SEEDED BY HAND, PHYSICS ONLY. Chemistry and Biology come from the same data
 * job as the sequence work; this file exists to settle the interaction first.
 */

export type TopicSource = 'ncert' | 'allen' | 'aakash' | 'pw';

export interface Suggestion {
  name: string;
  src: TopicSource[];
}

/** Keyed the same way as CLASS11_SUBTOPICS: `subject::Chapter name`. */
export const SUGGESTED_TOPICS: Record<string, Suggestion[]> = {
  'physics::Units and Measurements': [
    { name: 'Dimensional analysis', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Significant figures and rounding', src: ['ncert', 'allen', 'pw'] },
    { name: 'Error analysis — combination of errors', src: ['allen', 'aakash', 'pw'] },
    { name: 'Vernier calliper and screw gauge', src: ['allen', 'aakash'] },
    { name: 'Basic maths for physics', src: ['allen', 'aakash', 'pw'] },
    { name: 'Differentiation and integration toolkit', src: ['allen', 'pw'] },
  ],
  'physics::Motion in a Straight Line': [
    { name: 'Distance, displacement, average vs instantaneous', src: ['ncert', 'allen', 'pw'] },
    { name: 'Equations of motion', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Motion under gravity', src: ['allen', 'aakash', 'pw'] },
    { name: 'Graphical analysis — x-t, v-t, a-t', src: ['allen', 'aakash', 'pw'] },
    { name: 'Relative motion in one dimension', src: ['ncert', 'allen', 'pw'] },
    { name: 'Variable acceleration by calculus', src: ['allen', 'pw'] },
  ],
  'physics::Motion in a Plane': [
    { name: 'Vectors — addition, resolution, products', src: ['allen', 'aakash', 'pw'] },
    { name: 'Projectile motion from ground', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Projectile from a height and on an incline', src: ['allen', 'aakash', 'pw'] },
    { name: 'Relative motion in two dimensions — river and rain', src: ['allen', 'aakash', 'pw'] },
    { name: 'Uniform circular motion', src: ['ncert', 'allen', 'pw'] },
  ],
  'physics::Laws of Motion': [
    { name: "Newton's laws and free body diagrams", src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Constraint relations — pulleys and wedges', src: ['allen', 'aakash', 'pw'] },
    { name: 'Friction — static, kinetic, angle of repose', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Circular motion dynamics — banking', src: ['ncert', 'allen', 'pw'] },
    { name: 'Pseudo force and non-inertial frames', src: ['allen', 'aakash', 'pw'] },
    { name: 'Spring force and connected bodies', src: ['allen', 'pw'] },
  ],
  'physics::Work, Energy and Power': [
    { name: 'Work by constant and variable force', src: ['ncert', 'allen', 'pw'] },
    { name: 'Work–energy theorem', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Conservative forces and potential energy', src: ['ncert', 'allen', 'pw'] },
    { name: 'Vertical circular motion', src: ['allen', 'aakash', 'pw'] },
    { name: 'Collisions — elastic, inelastic, oblique', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Power and efficiency', src: ['ncert', 'allen'] },
  ],
  'physics::System of Particles and Rotational Motion': [
    { name: 'Centre of mass — discrete and continuous', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Moment of inertia of standard bodies', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Parallel and perpendicular axes theorems', src: ['ncert', 'allen', 'pw'] },
    { name: 'Torque and angular momentum', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Conservation of angular momentum', src: ['ncert', 'allen', 'pw'] },
    { name: 'Rolling without slipping', src: ['allen', 'aakash', 'pw'] },
    { name: 'Equilibrium of rigid bodies', src: ['ncert', 'aakash'] },
  ],
  'physics::Gravitation': [
    { name: "Newton's law of gravitation and g", src: ['ncert', 'allen', 'pw'] },
    { name: 'Variation of g with height, depth, rotation', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Gravitational potential and potential energy', src: ['ncert', 'allen', 'pw'] },
    { name: 'Escape velocity and orbital velocity', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Satellites — energy, time period, geostationary', src: ['ncert', 'allen', 'pw'] },
    { name: "Kepler's laws", src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Mechanical Properties of Solids': [
    { name: 'Stress, strain and Hooke’s law', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Elastic moduli — Y, B, G', src: ['ncert', 'allen', 'pw'] },
    { name: 'Stress–strain curve', src: ['ncert', 'allen', 'aakash'] },
    { name: 'Elastic potential energy', src: ['ncert', 'allen', 'pw'] },
    { name: "Poisson's ratio", src: ['allen', 'pw'] },
  ],
  'physics::Mechanical Properties of Fluids': [
    { name: 'Pressure, gauge pressure, Pascal’s law', src: ['ncert', 'allen', 'pw'] },
    { name: 'Buoyancy and floatation', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Surface tension, capillarity, excess pressure', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Viscosity and terminal velocity', src: ['ncert', 'allen', 'pw'] },
    { name: "Bernoulli's theorem and applications", src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Equation of continuity', src: ['ncert', 'allen'] },
  ],
  'physics::Thermal Properties of Matter': [
    { name: 'Thermal expansion — linear, areal, cubical', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Calorimetry and latent heat', src: ['ncert', 'allen', 'pw'] },
    { name: 'Conduction — rods in series and parallel', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: "Newton's law of cooling", src: ['ncert', 'allen', 'pw'] },
    { name: 'Radiation — Stefan, Wien, blackbody', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Thermodynamics': [
    { name: 'Zeroth law and thermal equilibrium', src: ['ncert', 'allen'] },
    { name: 'First law of thermodynamics', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Thermodynamic processes and P-V work', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Molar specific heats, Cp − Cv', src: ['ncert', 'allen', 'pw'] },
    { name: 'Adiabatic and isothermal relations', src: ['allen', 'aakash', 'pw'] },
    { name: 'Heat engines, refrigerators, Carnot cycle', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Kinetic Theory': [
    { name: 'Ideal gas equation and gas laws', src: ['ncert', 'allen', 'pw'] },
    { name: 'Kinetic interpretation of pressure', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'RMS, average and most probable speeds', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Degrees of freedom and equipartition', src: ['ncert', 'allen', 'pw'] },
    { name: 'Mean free path', src: ['ncert', 'allen'] },
  ],
  'physics::Oscillations': [
    { name: 'SHM — displacement, velocity, acceleration', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Energy in SHM', src: ['ncert', 'allen', 'pw'] },
    { name: 'Spring–mass systems, series and parallel', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Simple pendulum', src: ['ncert', 'allen', 'pw'] },
    { name: 'Damped and forced oscillations, resonance', src: ['ncert', 'aakash'] },
  ],
  'physics::Waves': [
    { name: 'Progressive wave equation', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Speed of a wave on a string and in a gas', src: ['ncert', 'allen', 'pw'] },
    { name: 'Superposition, standing waves, harmonics', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Organ pipes — open and closed', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Beats', src: ['ncert', 'allen', 'pw'] },
  ],
  'physics::Electric Charges and Fields': [
    { name: "Coulomb's law and superposition", src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Electric field of point charges and dipoles', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Dipole in a uniform field — torque and energy', src: ['ncert', 'allen', 'pw'] },
    { name: 'Electric flux and Gauss’s law', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Field of sheet, shell and cylinder', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Electrostatic Potential and Capacitance': [
    { name: 'Potential due to charge, dipole, system', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Equipotential surfaces', src: ['ncert', 'allen', 'pw'] },
    { name: 'Conductors, shielding, earthing', src: ['ncert', 'allen', 'pw'] },
    { name: 'Capacitors in series and parallel', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Dielectrics and polarisation', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Energy stored, redistribution of charge', src: ['allen', 'aakash', 'pw'] },
  ],
  'physics::Current Electricity': [
    { name: 'Drift velocity, mobility, resistivity', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: "Ohm's law and V-I characteristics", src: ['ncert', 'allen', 'pw'] },
    { name: 'Series and parallel combinations', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'EMF, internal resistance, terminal voltage', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: "Kirchhoff's laws", src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Wheatstone bridge and meter bridge', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Heating effect and electrical power', src: ['ncert', 'allen', 'pw'] },
  ],
  'physics::Moving Charges and Magnetism': [
    { name: 'Biot–Savart law', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Field of a loop, solenoid, toroid', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: "Ampere's circuital law", src: ['ncert', 'allen', 'pw'] },
    { name: 'Force on a moving charge, cyclotron', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Force between parallel currents', src: ['ncert', 'allen', 'pw'] },
    { name: 'Torque on a loop, moving coil galvanometer', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Magnetism and Matter': [
    { name: 'Bar magnet as an equivalent solenoid', src: ['ncert', 'allen', 'pw'] },
    { name: 'Magnetic dipole moment and torque', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Dia-, para- and ferromagnetism', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Hysteresis', src: ['ncert', 'allen'] },
  ],
  'physics::Electromagnetic Induction': [
    { name: "Faraday's and Lenz's laws", src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Motional EMF', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Self and mutual inductance', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Energy stored in an inductor', src: ['ncert', 'allen', 'pw'] },
    { name: 'Eddy currents', src: ['ncert', 'allen'] },
  ],
  'physics::Alternating Current': [
    { name: 'RMS and peak values, phasors', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'AC through R, L and C', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'LCR series circuit and impedance', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Resonance and Q factor', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Power factor and wattless current', src: ['ncert', 'allen', 'pw'] },
    { name: 'Transformers', src: ['ncert', 'allen', 'pw'] },
  ],
  'physics::Electromagnetic Waves': [
    { name: 'Displacement current', src: ['ncert', 'allen', 'pw'] },
    { name: 'Properties of electromagnetic waves', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Electromagnetic spectrum and uses', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Ray Optics and Optical Instruments': [
    { name: 'Reflection at plane and spherical mirrors', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Refraction, TIR, critical angle', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Refraction at a spherical surface, lens maker', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Combination of lenses, power', src: ['ncert', 'allen', 'pw'] },
    { name: 'Prism, dispersion, deviation', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Microscope and telescope', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Defects of vision', src: ['allen', 'pw'] },
  ],
  'physics::Wave Optics': [
    { name: "Huygens' principle", src: ['ncert', 'allen', 'pw'] },
    { name: "Young's double slit — fringe width", src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Intensity distribution and path difference', src: ['allen', 'aakash', 'pw'] },
    { name: 'Single slit diffraction', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Polarisation and Brewster’s law', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Dual Nature of Radiation and Matter': [
    { name: 'Photoelectric effect and its laws', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: "Einstein's photoelectric equation, stopping potential", src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Photon — energy, momentum, intensity', src: ['ncert', 'allen', 'pw'] },
    { name: 'de Broglie wavelength', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Atoms': [
    { name: 'Rutherford scattering, distance of closest approach', src: ['ncert', 'allen', 'pw'] },
    { name: 'Bohr model — radius, velocity, energy', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Hydrogen spectral series', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Nuclei': [
    { name: 'Nuclear size, density, composition', src: ['ncert', 'allen', 'pw'] },
    { name: 'Mass defect and binding energy per nucleon', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Fission and fusion', src: ['ncert', 'allen', 'aakash', 'pw'] },
  ],
  'physics::Semiconductor Electronics': [
    { name: 'Energy bands — conductor, semiconductor, insulator', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Intrinsic and extrinsic semiconductors', src: ['ncert', 'allen', 'pw'] },
    { name: 'p-n junction and biasing', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Rectifiers — half and full wave', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Zener diode, LED, photodiode, solar cell', src: ['ncert', 'allen', 'aakash', 'pw'] },
    { name: 'Logic gates', src: ['ncert', 'allen', 'pw'] },
  ],
};

/** How the app writes each source in the suggestion sheet. */
export const SOURCE_LABELS: Record<TopicSource, string> = {
  ncert: 'NCERT',
  allen: 'Allen',
  aakash: 'Aakash',
  pw: 'PW',
};

/** Names collapse to this before comparing, so a rename is not a duplicate. */
export function topicKey(name: string): string {
  return name
    .toLowerCase()
    // Leading NCERT section number: "7.3 Moment of inertia" -> "moment of inertia".
    .replace(/^\d+(\.\d+)*\s*/, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

/**
 * Suggestions for a chapter that the student's list does not already hold,
 * most widely taught first.
 */
export function suggestionsFor(key: string, have: readonly string[]): Suggestion[] {
  const taken = new Set(have.map(topicKey));
  return (SUGGESTED_TOPICS[key] ?? [])
    .filter((s) => !taken.has(topicKey(s.name)))
    .sort((a, b) => b.src.length - a.src.length);
}
