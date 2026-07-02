/* Adjuster admin simulator */
/* Floating action button that opens a large left-tabbed modal to simulate the
   field adjuster and claims adjuster applications acting on claims. */

import { useState } from "react";
import "./AdminPanel.css";

const TABS = [
  { id: "field-adjuster", label: "Field Adjuster", icon: "🔧" },
  { id: "adjuster", label: "Claims Adjuster", icon: "✅" },
];

function FieldAdjusterPanel() {
  return (
    <div className="admin-tab-panel">
      <h3>🔧 Field Adjuster</h3>
      <p>
        Stand-in for the field adjuster application. Here an adjuster dispatched to the
        scene looks up a claim and submits their damage assessment back to the workflow.
      </p>
      <div className="admin-placeholder">Damage assessment form coming soon.</div>
    </div>
  );
}

function AdjusterPanel() {
  return (
    <div className="admin-tab-panel">
      <h3>✅ Claims Adjuster</h3>
      <p>
        Stand-in for the claims adjuster console. Here an adjuster reviews an assessed
        claim and approves the payout to move it toward closure.
      </p>
      <div className="admin-placeholder">Approval queue coming soon.</div>
    </div>
  );
}

function AdminPanel() {
  const [isOpen, setIsOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("field-adjuster");

  return (
    <>
      <button
        className="admin-fab"
        type="button"
        onClick={() => setIsOpen(true)}
        aria-label="Open adjuster admin panel"
        title="Adjuster admin panel"
      >
        🛠️
      </button>

      {isOpen && (
        <div className="admin-modal-backdrop" role="presentation" onClick={() => setIsOpen(false)}>
          <section
            className="admin-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="admin-modal-header">
              <h2 id="admin-modal-title">🛠️ Adjuster Admin Panel</h2>
              <button
                className="admin-modal-close"
                type="button"
                onClick={() => setIsOpen(false)}
                aria-label="Close admin panel"
              >
                ×
              </button>
            </div>

            <div className="admin-modal-body">
              <nav className="admin-tabs" aria-label="Adjuster interfaces">
                {TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    className={`admin-tab${activeTab === tab.id ? " admin-tab--active" : ""}`}
                    onClick={() => setActiveTab(tab.id)}
                    aria-pressed={activeTab === tab.id}
                  >
                    <span className="admin-tab-icon">{tab.icon}</span>
                    {tab.label}
                  </button>
                ))}
              </nav>

              <div className="admin-tab-content">
                {activeTab === "field-adjuster" ? <FieldAdjusterPanel /> : <AdjusterPanel />}
              </div>
            </div>
          </section>
        </div>
      )}
    </>
  );
}

export default AdminPanel;
