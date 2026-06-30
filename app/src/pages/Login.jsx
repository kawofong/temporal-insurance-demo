/* Login page for Ziggy Insurance portal */
/* Displays retro-styled login with "Login as Jake" button */

import { useNavigate } from "react-router-dom";
import "./Login.css";

function Login() {
  const navigate = useNavigate();

  const handleLogin = () => {
    navigate("/portal");
  };

  return (
    <div className="login-page">
      <div className="login-container">
        <div className="login-stars">★ ✦ ★ ✦ ★</div>
        <span className="login-pixel-art">🛡️</span>
        <h1 className="login-title">Ziggy Insurance</h1>
        <p className="login-subtitle">Policyholder Portal</p>
        <hr className="login-divider" />
        <button className="login-button" onClick={handleLogin}>
          Login as Jake
        </button>
        <p className="login-footer">
          Press START to continue
        </p>
      </div>
    </div>
  );
}

export default Login;
