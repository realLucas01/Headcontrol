const DEFAULTS = {
  enabled: true,
  showOverlay: true,
  sensitivity: 1.6,
  deadzone: 0.12
};

const state = {
  settings: loadSettings(),
  // Dummy "tracking" values in [-1..1]
  yaw: 0,
  pitch: 0,
  lean: 0
};

// Elements
const hud = document.getElementById("hud");
const badgeEnabled = document.getElementById("badgeEnabled");
const badgeOverlay = document.getElementById("badgeOverlay");
const barYaw = document.getElementById("barYaw");
const barPitch = document.getElementById("barPitch");
const barLean = document.getElementById("barLean");

const modalBackdrop = document.getElementById("modalBackdrop");
const btnSettings = document.getElementById("btnSettings");
const btnClose = document.getElementById("btnClose");
const btnApply = document.getElementById("btnApply");
const btnResetAll = document.getElementById("btnResetAll");
const btnToggleEnabled = document.getElementById("btnToggleEnabled");
const btnCalibrate = document.getElementById("btnCalibrate");
const btnVoiceInventory = document.getElementById("btnVoiceInventory");
const toastArea = document.getElementById("toastArea");

const optEnabled = document.getElementById("optEnabled");
const optOverlay = document.getElementById("optOverlay");
const optSensitivity = document.getElementById("optSensitivity");
const optDeadzone = document.getElementById("optDeadzone");
const valSensitivity = document.getElementById("valSensitivity");
const valDeadzone = document.getElementById("valDeadzone");

// Init
syncControlsFromSettings();
render();

btnSettings.addEventListener("click", () => openModal());
btnClose.addEventListener("click", () => closeModal());
modalBackdrop.addEventListener("click", (e) => { if (e.target === modalBackdrop) closeModal(); });

btnApply.addEventListener("click", () => {
  state.settings.enabled = optEnabled.checked;
  state.settings.showOverlay = optOverlay.checked;
  state.settings.sensitivity = Number(optSensitivity.value);
  state.settings.deadzone = Number(optDeadzone.value);
  saveSettings(state.settings);
  toast("Settings applied", "ok");
  render();
  closeModal();
});

btnResetAll.addEventListener("click", () => {
  state.settings = { ...DEFAULTS };
  saveSettings(state.settings);
  syncControlsFromSettings();
  toast("Settings reset", "warn");
  render();
});

btnToggleEnabled.addEventListener("click", () => {
  state.settings.enabled = !state.settings.enabled;
  saveSettings(state.settings);
  syncControlsFromSettings();
  render();
});

btnCalibrate.addEventListener("click", () => {
  state.yaw = 0; state.pitch = 0; state.lean = 0;
  toast("Calibrated (values reset)", "ok");
  render();
});

btnVoiceInventory.addEventListener("click", () => {
  // Pure UI feedback: later this would open Minecraft inventory.
  toast("Voice command: Inventory opened (simulated)", "ok");
});

// Live display for sliders
optSensitivity.addEventListener("input", () => (valSensitivity.textContent = optSensitivity.value));
optDeadzone.addEventListener("input", () => (valDeadzone.textContent = optDeadzone.value));

// Keyboard simulation
window.addEventListener("keydown", (e) => {
  // R resets dummy tracking values
  if (e.key.toLowerCase() === "r") {
    state.yaw = 0; state.pitch = 0; state.lean = 0;
    toast("Reset tracking values (R)", "warn");
    render();
    return;
  }

  // Only simulate if enabled (matches intended behavior)
  if (!state.settings.enabled) return;

  const step = 0.12 * state.settings.sensitivity;

  // yaw/pitch (arrow keys)
  if (e.key === "ArrowLeft") state.yaw -= step;
  if (e.key === "ArrowRight") state.yaw += step;
  if (e.key === "ArrowUp") state.pitch -= step;
  if (e.key === "ArrowDown") state.pitch += step;

  // lean (WASD)
  if (e.key.toLowerCase() === "w") state.lean += step;
  if (e.key.toLowerCase() === "s") state.lean -= step;
  if (e.key.toLowerCase() === "a") state.lean -= step;
  if (e.key.toLowerCase() === "d") state.lean += step;

  clampAll();
  applyDeadzone();
  render();
});

function render() {
  // HUD visibility
  hud.style.display = state.settings.showOverlay ? "block" : "none";

  // Badges
  badgeEnabled.textContent = `HeadControl: ${state.settings.enabled ? "ON" : "OFF"}`;
  badgeEnabled.style.borderColor = state.settings.enabled ? "rgba(78,229,154,.45)" : "rgba(255,107,122,.45)";
  badgeEnabled.style.background = state.settings.enabled ? "rgba(78,229,154,.18)" : "rgba(255,107,122,.14)";

  badgeOverlay.textContent = state.settings.showOverlay ? "Overlay" : "Overlay hidden";
  badgeOverlay.style.opacity = state.settings.showOverlay ? "0.9" : "0.55";

  // Bars (map [-1..1] to [0..100] with center at 50)
  setBar(barYaw, state.yaw);
  setBar(barPitch, state.pitch);
  setBar(barLean, state.lean);
}

function setBar(el, v) {
  const pct = 50 + (v * 50);
  el.style.width = `${Math.max(0, Math.min(100, pct))}%`;
}

function clampAll() {
  state.yaw = clamp(state.yaw, -1, 1);
  state.pitch = clamp(state.pitch, -1, 1);
  state.lean = clamp(state.lean, -1, 1);
}

function applyDeadzone() {
  const dz = state.settings.deadzone;
  state.yaw = Math.abs(state.yaw) < dz ? 0 : state.yaw;
  state.pitch = Math.abs(state.pitch) < dz ? 0 : state.pitch;
  state.lean = Math.abs(state.lean) < dz ? 0 : state.lean;
}

function clamp(x, a, b) { return Math.max(a, Math.min(b, x)); }

function openModal() {
  syncControlsFromSettings();
  modalBackdrop.hidden = false;
}

function closeModal() {
  modalBackdrop.hidden = true;
  document.body.focus();
}

function syncControlsFromSettings() {
  optEnabled.checked = state.settings.enabled;
  optOverlay.checked = state.settings.showOverlay;
  optSensitivity.value = String(state.settings.sensitivity);
  optDeadzone.value = String(state.settings.deadzone);
  valSensitivity.textContent = optSensitivity.value;
  valDeadzone.textContent = optDeadzone.value;
}

function toast(msg, type = "") {
  const el = document.createElement("div");
  el.className = `toast ${type}`.trim();
  el.textContent = msg;
  toastArea.prepend(el);
  setTimeout(() => el.remove(), 2800);
}

function loadSettings() {
  try {
    const raw = localStorage.getItem("headcontrol_settings_v1");
    if (!raw) return { ...DEFAULTS };
    const parsed = JSON.parse(raw);
    return { ...DEFAULTS, ...parsed };
  } catch {
    return { ...DEFAULTS };
  }
}

function saveSettings(s) {
  localStorage.setItem("headcontrol_settings_v1", JSON.stringify(s));
}
