/* Root application component with routing */
/* Routes between Login and Policyholder Portal pages */

import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/Login";
import Portal from "./pages/Portal";
import PolicyDetails from "./pages/PolicyDetails";
import ClaimDetails from "./pages/ClaimDetails";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/portal" element={<Portal />} />
        <Route path="/portal/policies/:policyType/:policyId" element={<PolicyDetails />} />
        <Route path="/portal/claims/:claimId" element={<ClaimDetails />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
