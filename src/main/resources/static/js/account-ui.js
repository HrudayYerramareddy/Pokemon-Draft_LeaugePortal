/* Password management: self-service changes and commissioner resets. */
async function renderAccount() {
  const manager = state.user.role === "MANAGER";
  $("#page-account").innerHTML = `
    <h1>Account Settings</h1>
    <div class="card">
      <h2>Change My Password</h2>
      <p class="muted">Signed in as ${esc(state.user.username)}. Use 8–72 characters.</p>
      <div class="toolbar">
        <label>Current password<input id="account-current" type="password" autocomplete="current-password"></label>
        <label>New password<input id="account-new" type="password" minlength="8" maxlength="72" autocomplete="new-password"></label>
        <label>Confirm new password<input id="account-confirm" type="password" minlength="8" maxlength="72" autocomplete="new-password"></label>
        <button class="primary" id="account-change">Change Password</button>
      </div>
    </div>
    ${manager ? '<div class="card"><h2>Reset Coach Password</h2><p class="muted">Commissioner only. Resets a selected coach password to <strong>password</strong>. Ask them to change it after signing in.</p><div class="toolbar"><label>Coach<select id="reset-user"><option value="">Select coach</option></select></label><button class="danger" id="reset-password">Reset to password</button></div></div>' : ""}`;
  $("#account-change").addEventListener("click", async () => {
    const currentPassword = $("#account-current").value;
    const newPassword = $("#account-new").value;
    const confirmation = $("#account-confirm").value;
    if (newPassword !== confirmation) return toast("New passwords do not match", true);
    if (newPassword.length < 8 || newPassword.length > 72) return toast("Use 8–72 characters", true);
    try {
      await api("/api/auth/change-password", {method: "POST", body: {currentPassword, newPassword}});
      $("#account-current").value = "";
      $("#account-new").value = "";
      $("#account-confirm").value = "";
      toast("Password changed");
    } catch (e) { toast(e.message, true); }
  });
  if (manager) {
    const users = await api("/api/auth/manager/users");
    $("#reset-user").innerHTML += users.map(u => `<option value="${u.id}">${esc(u.username)}</option>`).join("");
    $("#reset-password").addEventListener("click", async () => {
      const id = Number($("#reset-user").value);
      if (!id) return toast("Select a coach", true);
      const name = users.find(u => u.id === id)?.username || "this coach";
      if (!confirm(`Reset ${name}'s password to "password"?`)) return;
      try {
        await api(`/api/auth/manager/users/${id}/reset-password`, {method: "POST"});
        toast(`Password reset for ${name}`);
      } catch (e) { toast(e.message, true); }
    });
  }
}
