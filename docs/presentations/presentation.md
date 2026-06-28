---
marp: true
theme: default
paginate: true
backgroundColor: #ffffff
style: |
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');

  section {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
    font-size: 1.1rem;
    padding: 48px 64px;
    color: #111827;
  }

  /* ── Title slide ─────────────────────────────────── */
  section.title {
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
  section.title h1 {
    color: #f8fafc !important;
    font-size: 2.6rem !important;
    font-weight: 700;
    line-height: 1.2;
    border: none !important;
    margin-bottom: 0.4em;
  }
  section.title p {
    color: #94a3b8 !important;
    font-size: 1.1rem;
    margin: 0;
  }
  section.title .prereq {
    margin-top: 2.5rem;
    background: #1e293b;
    border-left: 3px solid #3b82f6;
    padding: 0.8em 1.2em;
    border-radius: 0 6px 6px 0;
    font-size: 0.95rem;
    color: #cbd5e1 !important;
  }

  /* ── Section divider ─────────────────────────────── */
  section.divider {
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
  section.divider h1 {
    color: #f8fafc !important;
    font-size: 2rem !important;
    border: none !important;
    margin-bottom: 0.3em;
  }
  section.divider p {
    color: #93c5fd !important;
    font-size: 1rem;
  }
  section.divider header, section.divider footer,
  section.title header, section.title footer { display: none; }

  /* ── Headings ─────────────────────────────────────── */
  h1 { font-size: 1.6rem; font-weight: 700; color: #111827;
       border-bottom: 2px solid #e5e7eb; padding-bottom: 0.3em; margin-bottom: 0.8em; }
  h2 { font-size: 1.15rem; font-weight: 600; color: #374151; margin-top: 1em; }

  /* ── Badges ───────────────────────────────────────── */
  .badge {
    display: inline-block;
    font-size: 0.7rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    padding: 3px 10px;
    border-radius: 4px;
    margin-bottom: 0.6em;
  }
  .concept { background: #dbeafe; color: #1d4ed8; }
  .action  { background: #dcfce7; color: #15803d; }

  /* ── Callout boxes ────────────────────────────────── */
  .note {
    background: #fffbeb;
    border-left: 4px solid #f59e0b;
    padding: 0.6em 1em;
    margin-top: 0.8em;
    border-radius: 0 6px 6px 0;
    font-size: 0.9rem;
  }
  .ok {
    background: #f0fdf4;
    border-left: 4px solid #22c55e;
    padding: 0.6em 1em;
    margin-top: 0.6em;
    border-radius: 0 6px 6px 0;
    font-size: 0.9rem;
  }
  .danger {
    background: #fef2f2;
    border-left: 4px solid #ef4444;
    padding: 0.6em 1em;
    margin-top: 0.6em;
    border-radius: 0 6px 6px 0;
    font-size: 0.9rem;
  }

  /* ── Code ─────────────────────────────────────────── */
  pre {
    background: #0f172a !important;
    color: #e2e8f0 !important;
    border-radius: 8px;
    font-size: 0.82rem;
    line-height: 1.6;
    margin: 0.6em 0;
  }
  code { font-family: 'JetBrains Mono', 'Fira Code', monospace; }
  :not(pre) > code {
    background: #f1f5f9;
    color: #0f172a;
    padding: 1px 5px;
    border-radius: 3px;
    font-size: 0.88em;
  }

  /* ── Tables ───────────────────────────────────────── */
  table { width: 100%; border-collapse: collapse; font-size: 0.95rem; }
  th { background: #f1f5f9; font-weight: 600; text-align: left;
       padding: 0.5em 0.8em; border-bottom: 2px solid #e5e7eb; }
  td { padding: 0.45em 0.8em; border-bottom: 1px solid #f1f5f9; }

  /* ── Pagination ───────────────────────────────────── */
  section::after { color: #94a3b8; font-size: 0.8rem; }
---

<!-- _class: title -->
<!-- _backgroundColor: #0f172a -->
<!-- _color: #f8fafc -->

# Temporal Insurance Demo

A short subtitle describing the presentation.

<div class="prereq">
<strong>Who this is for:</strong> Describe the intended audience and any prerequisites here.
</div>

---

<!-- _class: divider -->
<!-- _paginate: false -->
<!-- _backgroundColor: #1e3a5f -->
<!-- _color: #f8fafc -->

# Section Divider

Use dividers to introduce a new section.

---

<span class="badge concept">concept</span>

# Content Slide

Standard content slide. The badge above marks the slide type — use `concept`
for ideas and `action` for hands-on steps.

| Column | Description |
|---|---|
| `ItemA` | First example row. |
| `ItemB` | Second example row. |

<div class="note">
A <code>.note</code> callout for tips or things to keep in mind.
</div>

---

<span class="badge action">action</span>

# Callouts &amp; Code

```python
# Example code block
print("hello, temporal")
```

<div class="ok">
An <code>.ok</code> callout for success states or recommended approaches.
</div>

<div class="danger">
A <code>.danger</code> callout for warnings or anti-patterns.
</div>
