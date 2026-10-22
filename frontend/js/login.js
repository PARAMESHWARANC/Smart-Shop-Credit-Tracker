const API_BASE = "http://localhost:8080/api";

async function getCsrfToken() {
  const response = await fetch(`${API_BASE}/auth/csrf`, { credentials: "include" });
  if (!response.ok) throw new Error("Could not connect to the application.");
  return (await response.json()).token;
}

document.getElementById("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const alert = document.getElementById("login-alert");
  const button = event.currentTarget.querySelector("button[type=submit]");
  button.disabled = true;
  button.textContent = "Signing in…";
  try {
    const token = await getCsrfToken();
    const response = await fetch(`${API_BASE}/auth/login`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": token },
      body: JSON.stringify({
        username: document.getElementById("username").value.trim(),
        password: document.getElementById("password").value
      })
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || body.error || "Sign in failed. Check your username and password.");
    }
    window.location.href = "index.html";
  } catch (error) {
    alert.textContent = error.message;
    alert.className = "alert alert-danger";
  } finally {
    button.disabled = false;
    button.textContent = "Sign in";
  }
});
