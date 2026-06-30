/* Policyholder portal page */
/* Displays insurance policies and recent claims in retro 16-bit style */

import { useNavigate } from "react-router-dom";
import { policyholder, policies, recentClaims } from "../data/mockData";
import "./Portal.css";

function getStatusClass(status) {
  const map = {
    "Approved": "approved",
    "Paid": "paid",
    "In Review": "in-review",
    "Denied": "denied",
  };
  return map[status] || "in-review";
}

function PolicyCard({ policy }) {
  const details = [];

  if (policy.vehicle) details.push({ label: "Vehicle", value: policy.vehicle });
  if (policy.address) details.push({ label: "Address", value: policy.address });
  if (policy.businessName) details.push({ label: "Business", value: policy.businessName });
  if (policy.coverageType) details.push({ label: "Type", value: policy.coverageType });
  details.push({ label: "Coverage", value: policy.coverage });
  details.push({ label: "Deductible", value: policy.deductible });
  details.push({ label: "Premium", value: policy.premium });

  return (
    <div className="policy-card">
      <div className="policy-card-header">
        <div className="policy-card-type">
          <span className="policy-card-icon">{policy.icon}</span>
          <div>
            <div className="policy-card-label">{policy.type}</div>
            <div className="policy-card-id">{policy.id}</div>
          </div>
        </div>
        <span className="policy-card-status">{policy.status}</span>
      </div>
      <div className="policy-card-details">
        {details.map((d) => (
          <div className="policy-detail" key={d.label}>
            <span className="policy-detail-label">{d.label}</span>
            <span className="policy-detail-value">{d.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Portal() {
  const navigate = useNavigate();

  const handleLogout = () => {
    navigate("/login");
  };

  return (
    <div className="portal-page">
      <header className="portal-header">
        <div className="portal-header-left">
          <h1>Welcome, {policyholder.name}</h1>
          <p>Member ID: {policyholder.memberId}</p>
        </div>
        <button className="portal-logout" onClick={handleLogout}>
          Logout
        </button>
      </header>

      <section>
        <h2 className="section-title">My Policies</h2>
        <div className="policies-grid">
          {policies.map((policy) => (
            <PolicyCard key={policy.id} policy={policy} />
          ))}
        </div>
      </section>

      <section className="claims-section">
        <h2 className="section-title">Recent Claims</h2>
        <div className="claims-table-wrapper">
          <table className="claims-table">
            <thead>
              <tr>
                <th>Claim ID</th>
                <th>Type</th>
                <th>Date</th>
                <th>Description</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {recentClaims.map((claim) => (
                <tr key={claim.id}>
                  <td>{claim.id}</td>
                  <td>{claim.type}</td>
                  <td>{claim.date}</td>
                  <td>{claim.description}</td>
                  <td>{claim.amount}</td>
                  <td>
                    <span className={`claim-status claim-status--${getStatusClass(claim.status)}`}>
                      {claim.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <footer className="portal-footer">
        Ziggy Insurance ★ Policyholder Portal ★ v1.0
      </footer>
    </div>
  );
}

export default Portal;
