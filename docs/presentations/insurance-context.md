---
marp: true
theme: default
paginate: true
style: |
  /* ── Reset & base ─────────────────────────────── */
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
  * { box-sizing: border-box; margin: 0; padding: 0; }
  section {
    font-family: 'Inter', Arial, sans-serif;
    font-size: 16px;
    color: #1e293b;
    background: #ffffff;
    padding: 52px 60px 44px;
    width: 1280px;
    height: 720px;
    overflow: hidden;
  }

  /* ── Typography ───────────────────────────────── */
  h1 { font-size: 2em; font-weight: 700; color: #0f172a; line-height: 1.2; margin-bottom: 10px; }
  h2 { font-size: 1.3em; font-weight: 600; color: #1e40af; margin-bottom: 8px; }
  h3 { font-size: 1em; font-weight: 600; color: #334155; margin-bottom: 4px; }
  p  { font-size: 0.9em; color: #475569; line-height: 1.5; }
  ul { padding-left: 1.2em; }
  li { font-size: 0.85em; color: #475569; margin-bottom: 3px; line-height: 1.4; }
  strong { color: #0f172a; }
  em { color: #64748b; font-style: italic; }

  /* ── Title slide ──────────────────────────────── */
  section.title {
    background: #0f172a;
    color: #f1f5f9;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
  section.title h1 { color: #f1f5f9; font-size: 2.6em; margin-bottom: 16px; }
  section.title p  { color: #94a3b8; font-size: 1.05em; }
  section.title .tag {
    display: inline-block;
    background: #0891b2;
    color: white;
    font-size: 0.75em;
    font-weight: 600;
    letter-spacing: 0.1em;
    padding: 4px 12px;
    border-radius: 4px;
    margin-bottom: 20px;
    text-transform: uppercase;
  }
  section.title .dots { margin-top: 28px; display: flex; gap: 24px; align-items: center; }
  section.title .dot-auto   { color: #2dd4bf; font-size: 0.9em; }
  section.title .dot-prop   { color: #a78bfa; font-size: 0.9em; }
  section.title .dot-comm   { color: #fbbf24; font-size: 0.9em; }

  /* ── Section divider slides ───────────────────── */
  section.divider-auto {
    background: #0f766e;
    color: white;
    display: flex; flex-direction: column; justify-content: center;
  }
  section.divider-auto h1 { color: white; font-size: 2.4em; }
  section.divider-auto p  { color: #99f6e4; font-size: 1em; margin-top: 8px; }

  section.divider-prop {
    background: #6d28d9;
    color: white;
    display: flex; flex-direction: column; justify-content: center;
  }
  section.divider-prop h1 { color: white; font-size: 2.4em; }
  section.divider-prop p  { color: #ddd6fe; font-size: 1em; margin-top: 8px; }

  section.divider-comm {
    background: #92400e;
    color: white;
    display: flex; flex-direction: column; justify-content: center;
  }
  section.divider-comm h1 { color: white; font-size: 2.4em; }
  section.divider-comm p  { color: #fde68a; font-size: 1em; margin-top: 8px; }

  section.divider-compare {
    background: #1e3a5f;
    color: white;
    display: flex; flex-direction: column; justify-content: center;
  }
  section.divider-compare h1 { color: white; font-size: 2.4em; }
  section.divider-compare p  { color: #bfdbfe; font-size: 1em; margin-top: 8px; }

  section.divider-platform {
    background: #0f172a;
    color: white;
    display: flex; flex-direction: column; justify-content: center;
  }
  section.divider-platform h1 { color: white; font-size: 2.4em; }
  section.divider-platform p  { color: #94a3b8; font-size: 1em; margin-top: 8px; }

  /* ── Header band (colored top bar) ───────────────*/
  section.auto   { border-top: 6px solid #0f766e; }
  section.prop   { border-top: 6px solid #7c3aed; }
  section.comm   { border-top: 6px solid #b45309; }
  section.compare{ border-top: 6px solid #1e40af; }
  section.platform{ border-top: 6px solid #0891b2; }

  /* ── Slide header label ───────────────────────── */
  .slide-label {
    font-size: 0.7em; font-weight: 600; letter-spacing: 0.08em;
    text-transform: uppercase; margin-bottom: 6px;
  }
  .label-auto  { color: #0f766e; }
  .label-prop  { color: #7c3aed; }
  .label-comm  { color: #b45309; }
  .label-compare { color: #1e40af; }
  .label-platform { color: #0891b2; }

  /* ── Journey timeline ─────────────────────────── */
  .timeline {
    display: flex;
    align-items: flex-start;
    gap: 0;
    margin-top: 18px;
    width: 100%;
  }
  .tl-step {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    position: relative;
  }
  .tl-step:not(:last-child)::after {
    content: '';
    position: absolute;
    top: 18px;
    right: -50%;
    width: 100%;
    height: 2px;
    background: #cbd5e1;
    z-index: 0;
  }
  .tl-num {
    width: 36px; height: 36px;
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    font-size: 0.85em; font-weight: 700;
    color: white;
    position: relative; z-index: 1;
    flex-shrink: 0;
  }
  .tl-label {
    font-size: 0.72em; font-weight: 600; color: #1e293b;
    text-align: center; margin-top: 8px; line-height: 1.3;
  }
  .tl-sub {
    font-size: 0.65em; color: #64748b;
    text-align: center; margin-top: 3px; line-height: 1.3;
    padding: 0 4px;
  }
  .tl-duration {
    font-size: 0.62em; font-weight: 600;
    text-align: center; margin-top: 5px;
    padding: 2px 6px; border-radius: 10px;
  }

  /* ── Card grid ────────────────────────────────── */
  .card-grid {
    display: grid;
    gap: 14px;
    margin-top: 16px;
  }
  .card-grid-2 { grid-template-columns: 1fr 1fr; }
  .card-grid-3 { grid-template-columns: 1fr 1fr 1fr; }

  .card {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 14px 16px;
  }
  .card h3 { font-size: 0.85em; margin-bottom: 6px; }
  .card ul { padding-left: 1em; }
  .card li { font-size: 0.78em; }

  .card-auto  { border-left: 4px solid #0f766e; }
  .card-prop  { border-left: 4px solid #7c3aed; }
  .card-comm  { border-left: 4px solid #b45309; }
  .card-blue  { border-left: 4px solid #1e40af; }
  .card-teal  { border-left: 4px solid #0891b2; }

  /* ── Stat blocks ──────────────────────────────── */
  .stat-row {
    display: flex;
    gap: 16px;
    margin-top: 16px;
  }
  .stat-box {
    flex: 1;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 14px 16px;
    text-align: center;
  }
  .stat-val {
    font-size: 1.8em; font-weight: 700; color: #0f172a; line-height: 1;
  }
  .stat-label { font-size: 0.72em; color: #64748b; margin-top: 4px; }

  /* ── Callout box ──────────────────────────────── */
  .callout {
    background: #f0f9ff;
    border: 1px solid #bae6fd;
    border-radius: 8px;
    padding: 12px 16px;
    margin-top: 16px;
  }
  .callout p { font-size: 0.82em; color: #0c4a6e; }
  .callout-warn {
    background: #fffbeb;
    border-color: #fde68a;
  }
  .callout-warn p { color: #78350f; }
  .callout-dark {
    background: #0f172a;
    border-color: #334155;
  }
  .callout-dark p { color: #cbd5e1; }

  /* ── Table ────────────────────────────────────── */
  table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 16px;
    font-size: 0.82em;
  }
  th {
    background: #1e3a5f;
    color: white;
    padding: 8px 12px;
    text-align: left;
    font-weight: 600;
    font-size: 0.85em;
  }
  td {
    padding: 7px 12px;
    border-bottom: 1px solid #e2e8f0;
    color: #334155;
    vertical-align: top;
  }
  tr:nth-child(even) td { background: #f8fafc; }
  td:first-child { font-weight: 600; color: #1e293b; }
  td.auto  { color: #0f766e; font-weight: 500; }
  td.prop  { color: #7c3aed; font-weight: 500; }
  td.comm  { color: #b45309; font-weight: 500; }

  /* ── Spectrum bar ─────────────────────────────── */
  .spectrum {
    display: flex;
    gap: 0;
    margin-top: 20px;
    border-radius: 6px;
    overflow: hidden;
    height: 40px;
  }
  .spectrum-auto {
    flex: 1; background: #0f766e;
    display: flex; align-items: center; justify-content: center;
    color: white; font-size: 0.78em; font-weight: 600;
  }
  .spectrum-prop {
    flex: 1.5; background: #7c3aed;
    display: flex; align-items: center; justify-content: center;
    color: white; font-size: 0.78em; font-weight: 600;
  }
  .spectrum-comm {
    flex: 2; background: #92400e;
    display: flex; align-items: center; justify-content: center;
    color: white; font-size: 0.78em; font-weight: 600;
  }

  /* ── Two-col layout ───────────────────────────── */
  .two-col {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
    margin-top: 16px;
  }
  .three-col {
    display: grid;
    grid-template-columns: 1fr 1fr 1fr;
    gap: 16px;
    margin-top: 16px;
  }

  /* ── Roadmap phases ───────────────────────────── */
  .phase-row {
    display: flex;
    gap: 16px;
    margin-top: 18px;
  }
  .phase {
    flex: 1;
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid #e2e8f0;
  }
  .phase-header {
    padding: 10px 14px;
    color: white;
    font-weight: 600;
    font-size: 0.85em;
  }
  .phase-body {
    padding: 12px 14px;
    background: #f8fafc;
  }
  .phase-body li { font-size: 0.78em; margin-bottom: 4px; }
  .phase-tag {
    display: inline-block;
    padding: 2px 8px; border-radius: 10px;
    font-size: 0.7em; font-weight: 600;
    margin-bottom: 8px;
  }

  /* ── Takeaway rows ────────────────────────────── */
  .takeaway {
    display: flex;
    align-items: flex-start;
    gap: 14px;
    margin-bottom: 12px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 12px 16px;
  }
  .takeaway-num {
    width: 28px; height: 28px; border-radius: 50%;
    background: #0891b2;
    color: white; font-size: 0.8em; font-weight: 700;
    display: flex; align-items: center; justify-content: center;
    flex-shrink: 0;
  }
  .takeaway p { font-size: 0.83em; color: #334155; }

  /* ── Paginate ─────────────────────────────────── */
  section::after {
    font-size: 0.65em;
    color: #94a3b8;
    bottom: 16px;
    right: 24px;
  }
---

<!-- _class: title -->

<div class="tag">Insurance Platform</div>

# Property & Casualty<br>Insurance User Journeys

*From First Quote to Unified Platform — Auto · Property · Commercial*

<div class="dots">
  <span class="dot-auto">● Auto Insurance</span>
  <span class="dot-prop">● Property Insurance</span>
  <span class="dot-comm">● Commercial Insurance</span>
</div>

---

<!-- _class: title -->

## Agenda

1. **Auto Insurance** — Core user journey & timeline
2. **Property Insurance** — Core user journey & timeline
3. **Commercial Insurance** — Core user journey & timeline
4. **Comparison** — Similarities & key differences across all three lines
5. **The Opportunity** — Case for a Unified Insurance Orchestration Platform
6. **Adoption Roadmap** — Beachhead to enterprise

---

<!-- _class: divider-auto -->

# 🚗 Auto Insurance

*The highest-volume P&C line — ideal beachhead for technology pilots*

---

<!-- _class: auto -->

<div class="slide-label label-auto">Auto Insurance — Core User Journey</div>

## Six stages from first quote to renewal

<div class="timeline">

  <div class="tl-step">
    <div class="tl-num" style="background:#0f766e">1</div>
    <div class="tl-label">Quote &<br>Purchase</div>
    <div class="tl-sub">Research · underwriting · bind · digital ID card</div>
    <div class="tl-duration" style="background:#ccfbf1;color:#0f766e">Hours–Days</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#0f766e">2</div>
    <div class="tl-label">Policy<br>Management</div>
    <div class="tl-sub">Add vehicles · drivers · telematics enrollment</div>
    <div class="tl-duration" style="background:#ccfbf1;color:#0f766e">Ongoing</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#dc2626">3</div>
    <div class="tl-label">File a Claim<br>(FNOL)</div>
    <div class="tl-sub">Report · adjuster assigned · damage assessed</div>
    <div class="tl-duration" style="background:#fee2e2;color:#dc2626">Hours–Days</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#dc2626">4</div>
    <div class="tl-label">Claim<br>Settlement</div>
    <div class="tl-sub">Repair auth · payment to shop · total loss if needed</div>
    <div class="tl-duration" style="background:#fee2e2;color:#dc2626">3–14 days</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#475569">5</div>
    <div class="tl-label">Roadside<br>Assistance</div>
    <div class="tl-sub">Tow · jump-start · lockout · real-time dispatch</div>
    <div class="tl-duration" style="background:#f1f5f9;color:#475569">Minutes–Hours</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#475569">6</div>
    <div class="tl-label">Renewal &<br>Retention</div>
    <div class="tl-sub">Rate review · discounts · bundle cross-sell</div>
    <div class="tl-duration" style="background:#f1f5f9;color:#475569">Annual</div>
  </div>

</div>

<div class="stat-row" style="margin-top:18px">
  <div class="stat-box"><div class="stat-val">3–14</div><div class="stat-label">days · typical claim</div></div>
  <div class="stat-box"><div class="stat-val">2–4</div><div class="stat-label">parties per claim</div></div>
  <div class="stat-box"><div class="stat-val">High</div><div class="stat-label">volume · automation fit</div></div>
  <div class="stat-box"><div class="stat-val">Low</div><div class="stat-label">financial docs required</div></div>
</div>

---

<!-- _class: auto -->

<div class="slide-label label-auto">Auto Insurance — Journey Detail</div>

## What happens inside each stage

<div class="card-grid card-grid-3" style="margin-top:14px">

  <div class="card card-auto">
    <h3>🛒 Quote & Purchase</h3>
    <ul>
      <li>Vehicle & driver data collected</li>
      <li>DMV, credit, prior claims checked</li>
      <li>Coverage customized & bound</li>
      <li>Digital ID card issued instantly</li>
    </ul>
  </div>

  <div class="card card-auto">
    <h3>📋 Policy Management</h3>
    <ul>
      <li>Add / remove drivers or vehicles</li>
      <li>Enroll in telematics (Drive Safe & Save)</li>
      <li>Autopay & billing management</li>
      <li>Annual renewal processing</li>
    </ul>
  </div>

  <div class="card" style="border-left:4px solid #dc2626">
    <h3>🚨 FNOL → Settlement</h3>
    <ul>
      <li>Incident reported via app or phone</li>
      <li>Adjuster assigned within hours</li>
      <li>Virtual or on-site damage assessment</li>
      <li>Payment to shop or customer directly</li>
    </ul>
  </div>

  <div class="card" style="border-left:4px solid #475569">
    <h3>🛞 Roadside Assistance</h3>
    <ul>
      <li>GPS-based dispatch triggered</li>
      <li>Third-party provider coordinated</li>
      <li>Real-time status to customer</li>
      <li>Follow-up claim if damage found</li>
    </ul>
  </div>

  <div class="card" style="border-left:4px solid #475569">
    <h3>🔄 Renewal</h3>
    <ul>
      <li>30-day advance notice sent</li>
      <li>Rate adjusts based on telematics data</li>
      <li>Bundle discounts applied</li>
      <li>Churn risk: competitor rate shopping</li>
    </ul>
  </div>

  <div class="callout" style="margin-top:0">
    <p><strong>Key orchestration need:</strong><br>
    FNOL triggers 5+ sequential system integrations — adjuster routing, shop authorization, rental coordination, payment. A dropped step = delayed settlement.</p>
  </div>

</div>

---

<!-- _class: divider-prop -->

# 🏠 Property Insurance

*Higher stakes, longer timelines, mortgage lender in every structural claim*

---

<!-- _class: prop -->

<div class="slide-label label-prop">Property Insurance — Core User Journey</div>

## Six stages — with a critical new complexity: lender & ALE

<div class="timeline">

  <div class="tl-step">
    <div class="tl-num" style="background:#7c3aed">1</div>
    <div class="tl-label">Quote &<br>Purchase</div>
    <div class="tl-sub">Property risk scoring · mortgage lender coordination · escrow bind</div>
    <div class="tl-duration" style="background:#ede9fe;color:#7c3aed">Days–Weeks</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#7c3aed">2</div>
    <div class="tl-label">Policy<br>Management</div>
    <div class="tl-sub">Inflation guard · endorsements · escrow-based premium · life events</div>
    <div class="tl-duration" style="background:#ede9fe;color:#7c3aed">Ongoing</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#dc2626">3</div>
    <div class="tl-label">File a Claim<br>(FNOL)</div>
    <div class="tl-sub">Emergency mitigation · field adjuster + drone · peril coverage check</div>
    <div class="tl-duration" style="background:#fee2e2;color:#dc2626">Days</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#dc2626">4</div>
    <div class="tl-label">Repair &<br>ALE Payments</div>
    <div class="tl-sub">Contractor coordination · lender co-payee · monthly ALE loop</div>
    <div class="tl-duration" style="background:#fee2e2;color:#dc2626">Weeks–Months</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#991b1b">5</div>
    <div class="tl-label">CAT Event<br>Response</div>
    <div class="tl-sub">Mass FNOL surge · triage · 1,000s of concurrent claims · 2–3yr resolution</div>
    <div class="tl-duration" style="background:#fee2e2;color:#991b1b">Months–Years</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#475569">6</div>
    <div class="tl-label">Renewal &<br>Non-Renewal</div>
    <div class="tl-sub">Post-CAT rate increase · non-renewal in high-risk zones · force-placed risk</div>
    <div class="tl-duration" style="background:#f1f5f9;color:#475569">Annual</div>
  </div>

</div>

<div class="stat-row" style="margin-top:18px">
  <div class="stat-box"><div class="stat-val">Weeks–12mo+</div><div class="stat-label">typical claim duration</div></div>
  <div class="stat-box"><div class="stat-val">5–10</div><div class="stat-label">parties per claim</div></div>
  <div class="stat-box"><div class="stat-val">Co-payee</div><div class="stat-label">lender signs structural checks</div></div>
  <div class="stat-box"><div class="stat-val">$50K–$1M+</div><div class="stat-label">structural claim range</div></div>
</div>

---

<!-- _class: prop -->

<div class="slide-label label-prop">Property Insurance — CAT Event Timeline</div>

## From first alert to final resolution

<div class="timeline" style="margin-top:22px">

  <div class="tl-step">
    <div class="tl-num" style="background:#991b1b">⚡</div>
    <div class="tl-label">Event<br>Declared</div>
    <div class="tl-sub">Hour 0</div>
    <div class="tl-duration" style="background:#fee2e2;color:#991b1b">Immediate</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#c2410c">📱</div>
    <div class="tl-label">Mass FNOL<br>Surge</div>
    <div class="tl-sub">Hours 0–48 · thousands of claims</div>
    <div class="tl-duration" style="background:#ffedd5;color:#c2410c">Hours</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#7c3aed">🛸</div>
    <div class="tl-label">Aerial Triage<br>& Prioritize</div>
    <div class="tl-sub">Days 1–7 · drone + satellite imagery · total loss first</div>
    <div class="tl-duration" style="background:#ede9fe;color:#7c3aed">Days</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#7c3aed">🏗️</div>
    <div class="tl-label">Repair +<br>ALE Payments</div>
    <div class="tl-sub">Months 1–12 · monthly ALE loop · contractor coordination</div>
    <div class="tl-duration" style="background:#ede9fe;color:#7c3aed">Months</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#475569">📋</div>
    <div class="tl-label">Supplemental<br>Claims</div>
    <div class="tl-sub">Months 3–18 · additional damage found during repair</div>
    <div class="tl-duration" style="background:#f1f5f9;color:#475569">Months</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#334155">✅</div>
    <div class="tl-label">Full<br>Resolution</div>
    <div class="tl-sub">Months 12–36 · permits · code upgrades · final settlement</div>
    <div class="tl-duration" style="background:#f1f5f9;color:#334155">Years</div>
  </div>

</div>

<div class="callout callout-warn" style="margin-top:20px">
  <p><strong>Key orchestration need:</strong> CAT events generate thousands of simultaneous long-running workflows — each with triage, ALE payment loops, lender co-payment routing, and supplemental claim cycles running over months to years. A failure at any step means a displaced family waits longer.</p>
</div>

---

<!-- _class: divider-comm -->

# 🏢 Commercial Insurance

*The most complex P&C line — multi-policy, multi-party, years-long workflows*

---

<!-- _class: comm -->

<div class="slide-label label-comm">Commercial Insurance — Core User Journey</div>

## Eight stages — including unique commercial-only complexity

<div class="timeline">

  <div class="tl-step">
    <div class="tl-num" style="background:#b45309">1</div>
    <div class="tl-label">Risk Assessment<br>& Quote</div>
    <div class="tl-sub">NAICS classification · multi-product quoting · BOP + auto + WC</div>
    <div class="tl-duration" style="background:#fef3c7;color:#b45309">Days–Weeks</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#b45309">2</div>
    <div class="tl-label">Policy &<br>COI Management</div>
    <div class="tl-sub">High-volume COI requests · add/remove employees · payroll reporting</div>
    <div class="tl-duration" style="background:#fef3c7;color:#b45309">Ongoing</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#dc2626">3</div>
    <div class="tl-label">Property + Biz<br>Interruption</div>
    <div class="tl-sub">Damage claim · lost revenue calc · monthly P&L submission</div>
    <div class="tl-duration" style="background:#fee2e2;color:#dc2626">Months–Years</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#dc2626">4</div>
    <div class="tl-label">Workers'<br>Comp Claim</div>
    <div class="tl-sub">Medical auth · disability payments · return-to-work · 50-state filings</div>
    <div class="tl-duration" style="background:#fee2e2;color:#dc2626">Months–3 yrs</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#991b1b">5</div>
    <div class="tl-label">General<br>Liability Claim</div>
    <div class="tl-sub">3rd-party injury · legal defense assigned · long-tail litigation</div>
    <div class="tl-duration" style="background:#fee2e2;color:#991b1b">Months–Years</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#475569">6</div>
    <div class="tl-label">Annual Audit<br>& Renewal</div>
    <div class="tl-sub">Payroll audit · EMR recalculation · multi-line sync renewal</div>
    <div class="tl-duration" style="background:#f1f5f9;color:#475569">Annual</div>
  </div>

</div>

<div class="stat-row" style="margin-top:18px">
  <div class="stat-box"><div class="stat-val">8–15+</div><div class="stat-label">parties per claim</div></div>
  <div class="stat-box"><div class="stat-val">50 states</div><div class="stat-label">workers' comp frameworks</div></div>
  <div class="stat-box"><div class="stat-val">12–36mo</div><div class="stat-label">WC / BI resolution</div></div>
  <div class="stat-box"><div class="stat-val">Annual audit</div><div class="stat-label">payroll & revenue</div></div>
</div>

---

<!-- _class: comm -->

<div class="slide-label label-comm">Commercial Insurance — Workers' Comp & BI Deep Dive</div>

## The two longest-running workflow types in all of P&C

<div class="two-col">

  <div>
    <h3 style="color:#b45309;margin-bottom:10px">Workers' Compensation — 6 Phases</h3>
    <div class="timeline" style="flex-direction:column;gap:6px">
      <div style="display:flex;gap:10px;align-items:center">
        <div class="tl-num" style="background:#b45309;width:26px;height:26px;font-size:0.75em;flex-shrink:0">1</div>
        <div><strong style="font-size:0.8em">Injury & Immediate</strong> <span style="font-size:0.75em;color:#64748b">— first aid, OSHA reporting</span></div>
      </div>
      <div style="display:flex;gap:10px;align-items:center">
        <div class="tl-num" style="background:#b45309;width:26px;height:26px;font-size:0.75em;flex-shrink:0">2</div>
        <div><strong style="font-size:0.8em">Medical Management</strong> <span style="font-size:0.75em;color:#64748b">— authorized treatment, IME, nurse case mgr</span></div>
      </div>
      <div style="display:flex;gap:10px;align-items:center">
        <div class="tl-num" style="background:#dc2626;width:26px;height:26px;font-size:0.75em;flex-shrink:0">3</div>
        <div><strong style="font-size:0.8em">Disability Payments</strong> <span style="font-size:0.75em;color:#64748b">— 66.67% wage replacement begins</span></div>
      </div>
      <div style="display:flex;gap:10px;align-items:center">
        <div class="tl-num" style="background:#dc2626;width:26px;height:26px;font-size:0.75em;flex-shrink:0">4</div>
        <div><strong style="font-size:0.8em">Return to Work</strong> <span style="font-size:0.75em;color:#64748b">— light duty, payments adjusted</span></div>
      </div>
      <div style="display:flex;gap:10px;align-items:center">
        <div class="tl-num" style="background:#475569;width:26px;height:26px;font-size:0.75em;flex-shrink:0">5</div>
        <div><strong style="font-size:0.8em">Permanent Disability</strong> <span style="font-size:0.75em;color:#64748b">— rating assigned, settlement structured</span></div>
      </div>
      <div style="display:flex;gap:10px;align-items:center">
        <div class="tl-num" style="background:#334155;width:26px;height:26px;font-size:0.75em;flex-shrink:0">6</div>
        <div><strong style="font-size:0.8em">EMR Impact</strong> <span style="font-size:0.75em;color:#64748b">— claims history raises 3-year premium modifier</span></div>
      </div>
    </div>
  </div>

  <div>
    <h3 style="color:#991b1b;margin-bottom:10px">Business Interruption — What Makes It Hard</h3>
    <ul style="margin-bottom:10px">
      <li><strong>Monthly revenue reconciliation</strong> — actual vs. historical baseline submitted every month of closure</li>
      <li><strong>Financial docs required</strong> — P&L statements, tax returns, payroll throughout</li>
      <li><strong>Extra expense sub-workflow</strong> — temp location, equipment lease, overtime wages</li>
      <li><strong>72-hour waiting period</strong> — timing critical for cash flow</li>
      <li><strong>Lender coordination</strong> — if property is mortgaged, co-sign approvals required</li>
      <li><strong>Duration:</strong> 12–36 months for major structural damage</li>
    </ul>
    <div class="callout callout-warn">
      <p>Both WC and BI require <strong>monthly recurring payment cycles, multi-party state filings, and human approvals for up to 3 years</strong>. No manual process sustains this reliably.</p>
    </div>
  </div>

</div>

---

<!-- _class: divider-compare -->

# 🔍 Comparison

*Similarities & differences across Auto, Property, and Commercial*

---

<!-- _class: compare -->

<div class="slide-label label-compare">Comparison — Shared Lifecycle</div>

## All three lines follow the same core structure

<div class="timeline" style="margin-top:24px">

  <div class="tl-step">
    <div class="tl-num" style="background:#0891b2">①</div>
    <div class="tl-label">Quote &<br>Underwrite</div>
    <div class="tl-sub">Risk data collection, scoring, pricing</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#0891b2">②</div>
    <div class="tl-label">Bind &<br>Onboard</div>
    <div class="tl-sub">Policy issued, third parties notified</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#0891b2">③</div>
    <div class="tl-label">Manage<br>Policy</div>
    <div class="tl-sub">Ongoing changes, payments, endorsements</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#dc2626">④</div>
    <div class="tl-label">File<br>Claim</div>
    <div class="tl-sub">FNOL → investigation → settlement</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#dc2626">⑤</div>
    <div class="tl-label">Settle<br>& Close</div>
    <div class="tl-sub">Payment, subrogation, satisfaction</div>
  </div>

  <div class="tl-step">
    <div class="tl-num" style="background:#0891b2">⑥</div>
    <div class="tl-label">Renew<br>& Evolve</div>
    <div class="tl-sub">Rate review, cross-sell, or churn</div>
  </div>

</div>

<div class="card-grid card-grid-3" style="margin-top:20px">
  <div class="card card-teal">
    <h3>FNOL is the Moment of Truth</h3>
    <p style="font-size:0.78em">Customer perception is set in the first 24–48 hours across all three lines. Speed and empathy are universal drivers.</p>
  </div>
  <div class="card card-teal">
    <h3>Multi-Party in Every Claim</h3>
    <p style="font-size:0.78em">No claim is two-party. Every line involves external vendors, data sources, regulatory filings, and human approvals at multiple steps.</p>
  </div>
  <div class="card card-teal">
    <h3>50-State Regulatory Burden</h3>
    <p style="font-size:0.78em">Rate approvals, coverage minimums, and claims-handling rules vary by state. Compliance is a constant across all three lines.</p>
  </div>
</div>

---

<!-- _class: compare -->

<div class="slide-label label-compare">Comparison — How the Three Lines Diverge</div>

## Same lifecycle, different scale and stakes

| Dimension | Auto | Property | Commercial |
|---|---|---|---|
| **Claim Duration** | 3–14 days | Weeks – 12+ months | Months – 3+ years |
| **Parties per Claim** | 2–4 | 5–10 | 8–15+ |
| **Consequential Loss** | Rental reimbursement (days) | ALE payments (months) | Business interruption (years) |
| **Financial Docs** | None | Receipts only | P&L, tax returns, payroll |
| **Regulatory Burden** | State minimums | Permits + NFIP | OSHA + 50 WC frameworks + DOT |
| **Annual Audit Cycle** | No | No | Yes — payroll & revenue |
| **Agent Role** | Transactional | Relational | Advisory / consultative |
| **Premium Volatility** | Low–Medium | Medium–High (CAT) | High (audit adjustments) |

<div class="spectrum" style="margin-top:16px">
  <div class="spectrum-auto">AUTO ← Simpler / Shorter</div>
  <div class="spectrum-prop">PROPERTY ← Medium Complexity</div>
  <div class="spectrum-comm">COMMERCIAL → More Complex / Longer / Higher Stakes</div>
</div>

---

<!-- _class: compare -->

<div class="slide-label label-compare">Comparison — Workflow Duration by Stage</div>

## Duration is the single biggest differentiator

| Journey Stage | Auto | Property | Commercial |
|---|---|---|---|
| **Quote & Purchase** | Hours–Days | Days–Weeks | Days–Weeks |
| **Policy Management** | Years (low-touch) | Years (moderate-touch) | Years (high-touch, COI volume) |
| **FNOL** | Hours–Days | Days | Days |
| **Claim Investigation** | 1–3 days | 1–2 weeks | 2–4 weeks |
| **Repair / Settlement** | 3–14 days | Weeks–6 months | Months–2 years |
| **Consequential Loss Payments** | Days (rental) | Weeks–Months (ALE) | Months–3 years (BI) |
| **Supplemental Claims** | Rare | Common (1–3 rounds) | Very common (multi-year) |
| **Annual Audit** | N/A | N/A | 3–6 months to close |

<div class="callout" style="margin-top:14px">
  <p><strong>The unifying insight:</strong> All three lines produce long-running, multi-step workflows where a failure at any step has financial or regulatory consequences. The difference is only the <em>magnitude</em> — from 14 days (auto) to 3 years (commercial workers' comp).</p>
</div>

---

<!-- _class: divider-platform -->

# 🔗 The Opportunity

*Building the same reliability infrastructure three times is a tax. A unified orchestration platform pays it once.*

---

<!-- _class: platform -->

<div class="slide-label label-platform">Platform Opportunity — The Common Workflow Problem</div>

## All three lines share the same six workflow patterns

<div class="two-col" style="margin-top:14px">

  <div>
    <div class="card card-teal" style="margin-bottom:10px">
      <h3>✓ Durable long-running execution</h3>
      <p style="font-size:0.78em">Workflows that span days, months, or years must survive crashes, restarts, and deployments without losing state.</p>
    </div>
    <div class="card card-teal" style="margin-bottom:10px">
      <h3>✓ Retry & timeout management</h3>
      <p style="font-size:0.78em">External calls to repair shops, lenders, medical providers, and state agencies fail. Reliable retry is non-negotiable.</p>
    </div>
    <div class="card card-teal">
      <h3>✓ Human-in-the-loop approvals</h3>
      <p style="font-size:0.78em">Adjuster sign-offs, lender co-signatures, return-to-work authorizations — all require pausing and waiting for humans.</p>
    </div>
  </div>

  <div>
    <div class="card card-blue" style="margin-bottom:10px">
      <h3>✓ Multi-party state coordination</h3>
      <p style="font-size:0.78em">Claims involve 2–15 external parties. State must be tracked and propagated across all of them reliably.</p>
    </div>
    <div class="card card-blue" style="margin-bottom:10px">
      <h3>✓ Event-driven triggers</h3>
      <p style="font-size:0.78em">CAT events, payment receipts, audit deadlines, renewal dates — all trigger downstream workflow chains.</p>
    </div>
    <div class="card card-blue">
      <h3>✓ Saga / compensation patterns</h3>
      <p style="font-size:0.78em">When a step fails mid-claim (e.g. contractor cancels), the workflow must compensate and re-route without data loss.</p>
    </div>
  </div>

</div>

<div class="callout callout-dark" style="margin-top:14px">
  <p>Building these six patterns separately for auto, property, and commercial means paying the reliability tax three times. A single orchestration platform deployed once delivers compounding returns across all lines.</p>
</div>

---

<!-- _class: platform -->

<div class="slide-label label-platform">Platform Opportunity — Where Each Line Fits</div>

## Auto is the beachhead. Commercial is the platform prize.

| Line | Workflow Duration | Cost of Failure | Temporal ROI | Best Entry Point |
|---|---|---|---|---|
| **Auto** | Days–weeks | Moderate ($5K–$50K) | Medium | ✅ FNOL → repair → payment |
| **Property** | Weeks–months | High ($50K–$500K) | High | ✅ ALE payment loop + CAT triage |
| **Commercial** | Months–years | Severe ($100K–$10M+) | Very High | ✅ Workers' comp lifecycle + BI monthly reconciliation |

<div class="three-col" style="margin-top:16px">

  <div class="card card-auto">
    <h3 style="color:#0f766e">Phase 1: Auto Claims</h3>
    <ul>
      <li>Highest volume → fastest ROI signal</li>
      <li>Predictable, repeatable workflow</li>
      <li>Measurable: cycle time reduction</li>
      <li>Low blast radius for pilot</li>
    </ul>
  </div>

  <div class="card card-prop">
    <h3 style="color:#7c3aed">Phase 2: Property Claims</h3>
    <ul>
      <li>Higher claim value per workflow</li>
      <li>ALE payment loops: key automation win</li>
      <li>CAT response: reliability under surge</li>
      <li>Lender coordination: eliminate manual routing</li>
    </ul>
  </div>

  <div class="card card-comm">
    <h3 style="color:#b45309">Phase 3: Commercial Lines</h3>
    <ul>
      <li>Workers' comp: long-tail durable workflows</li>
      <li>BI monthly reconciliation: automated loops</li>
      <li>COI management: high-volume, low-complexity</li>
      <li>Audit cycle: annual trigger orchestration</li>
    </ul>
  </div>

</div>

---

<!-- _class: platform -->

<div class="slide-label label-platform">Platform Adoption — Roadmap</div>

## Start simple. Prove the platform. Expand.

<div class="phase-row">

  <div class="phase">
    <div class="phase-header" style="background:#0f766e">Phase 1 — Beachhead: Auto Claims</div>
    <div class="phase-body">
      <div class="phase-tag" style="background:#ccfbf1;color:#0f766e">Months 1–6</div>
      <ul>
        <li>Target the FNOL → repair → payment workflow</li>
        <li>Replace fragile queues with durable execution</li>
        <li>Measure: average claim cycle time reduction</li>
        <li>Low risk: contained to one workflow type</li>
      </ul>
    </div>
  </div>

  <div class="phase">
    <div class="phase-header" style="background:#7c3aed">Phase 2 — Expand: Property Claims</div>
    <div class="phase-body">
      <div class="phase-tag" style="background:#ede9fe;color:#7c3aed">Months 6–18</div>
      <ul>
        <li>Automate ALE monthly payment loop</li>
        <li>CAT event: concurrent workflow fan-out</li>
        <li>Lender co-payment routing</li>
        <li>Measure: ALE processing time, CAT closure rate</li>
      </ul>
    </div>
  </div>

  <div class="phase">
    <div class="phase-header" style="background:#92400e">Phase 3 — Platform: Commercial Lines</div>
    <div class="phase-body">
      <div class="phase-tag" style="background:#fef3c7;color:#92400e">Months 12–36</div>
      <ul>
        <li>Workers' comp: 6-phase long-tail durable workflow</li>
        <li>BI: monthly revenue reconciliation loop</li>
        <li>COI: high-volume issuance automation</li>
        <li>Annual audit: year-end trigger orchestration</li>
      </ul>
    </div>
  </div>

</div>

<div class="callout" style="margin-top:14px">
  <p><strong>Each phase compounds the last.</strong> The platform investment made in Phase 1 (auto FNOL) is the same infrastructure that runs Phase 3 (workers' comp). You pay for it once; it serves all three lines.</p>
</div>

---

<!-- _class: title -->

## Key Takeaways

<div class="takeaway">
  <div class="takeaway-num">01</div>
  <p>All three P&C lines share the same lifecycle — <strong>Quote → Manage → Claim → Renew</strong> — but sit on a spectrum of increasing complexity and duration from Auto to Commercial.</p>
</div>
<div class="takeaway">
  <div class="takeaway-num">02</div>
  <p>The critical differentiator is <strong>claim duration</strong>: auto closes in days, property in months, commercial workers' comp or BI in years — requiring genuinely durable, long-running workflow execution.</p>
</div>
<div class="takeaway">
  <div class="takeaway-num">03</div>
  <p><strong>Multi-party coordination is universal.</strong> Every claim spans 2–15 external parties and systems — and a failure at any step has real financial or legal consequences for the customer.</p>
</div>
<div class="takeaway">
  <div class="takeaway-num">04</div>
  <p>Building reliable orchestration three times independently is wasteful. A <strong>unified workflow platform</strong> deployed once serves all lines and compounds in value as complexity increases.</p>
</div>

---

<!-- _class: title -->

## Next Step

<br>

### Identify the highest-volume workflow in your claims pipeline

*Start with auto FNOL — highest volume, fastest feedback loop, lowest risk. Use it to prove the platform, then expand to property and commercial.*

<br>

<div class="stat-row">
  <div class="stat-box" style="background:#0f1f0f;border-color:#1a3a1a">
    <div class="stat-val" style="color:#2dd4bf">Auto</div>
    <div class="stat-label" style="color:#94a3b8">Start here</div>
  </div>
  <div class="stat-box" style="background:#1a0f2e;border-color:#2d1a4a">
    <div class="stat-val" style="color:#a78bfa">Property</div>
    <div class="stat-label" style="color:#94a3b8">Scale to this</div>
  </div>
  <div class="stat-box" style="background:#1f1000;border-color:#3a1f00">
    <div class="stat-val" style="color:#fbbf24">Commercial</div>
    <div class="stat-label" style="color:#94a3b8">Enterprise platform</div>
  </div>
</div>