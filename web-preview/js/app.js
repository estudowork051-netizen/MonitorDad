/**
 * MonitorDad • by HoD — Interactive Web Viewer Logic (v1.4.0)
 * Demonstração visual de ParentApp e KidsApp sem comunicação ou permissões reais.
 */

const AppState = {
  // Screen time state (in minutes) -> 2h 45m used of 4h 00m = 1h 15m remaining
  usedMinutes: 165,
  totalLimitMinutes: 240,
  extraMinutes: 0,
  kidsLowTimeMode: false,

  // Current app in foreground (v1.4.0)
  currentAppActive: true,
  currentApp: {
    name: 'YouTube Kids',
    package: 'com.google.android.apps.youtube.kids',
    icon: '▶️',
    sessionMinutes: 18,
    todayMinutes: 70
  },

  // Device status & Synchronization
  isPaused: false,
  isOnline: true,
  battery: 82,
  syncStatus: 'online',
  lastSyncFormatted: 'há 2 minutos',
  hasKidsUsagePermission: true,

  apps: [
    {
      id: 'app-yt',
      name: 'YouTube Kids',
      package: 'com.google.android.apps.youtube.kids',
      category: 'videos',
      used: 70,
      usedYesterday: 55,
      limit: 90,
      blocked: false,
      icon: '▶️',
      lastUsed: 'Hoje às 14:10',
      sessions: [
        { start: '13:52', end: '14:10', duration: 18 },
        { start: '10:15', end: '11:07', duration: 52 }
      ]
    },
    {
      id: 'app-duo',
      name: 'Duolingo ABC',
      package: 'com.duolingo',
      category: 'education',
      used: 35,
      usedYesterday: 30,
      limit: 60,
      blocked: false,
      icon: '🦉',
      lastUsed: 'Hoje às 11:30',
      sessions: [
        { start: '10:55', end: '11:30', duration: 35 }
      ]
    },
    {
      id: 'app-mc',
      name: 'Minecraft',
      package: 'com.mojang.minecraftpe',
      category: 'games',
      used: 30,
      usedYesterday: 45,
      limit: 45,
      blocked: false,
      icon: '⛏️',
      lastUsed: 'Hoje às 12:45',
      sessions: [
        { start: '12:15', end: '12:45', duration: 30 }
      ]
    },
    {
      id: 'app-wa',
      name: 'WhatsApp Família',
      package: 'com.whatsapp',
      category: 'communication',
      used: 12,
      usedYesterday: 8,
      limit: 30,
      blocked: false,
      icon: '💬',
      lastUsed: 'Hoje às 14:02',
      sessions: [
        { start: '13:50', end: '14:02', duration: 12 }
      ]
    },
    {
      id: 'app-khan',
      name: 'Khan Academy Kids',
      package: 'com.khanacademy.kids',
      category: 'education',
      used: 18,
      usedYesterday: 25,
      limit: 60,
      blocked: false,
      icon: '📚',
      lastUsed: 'Hoje às 09:40',
      sessions: [
        { start: '09:22', end: '09:40', duration: 18 }
      ]
    },
    {
      id: 'app-roblox',
      name: 'Roblox',
      package: 'com.roblox.client',
      category: 'games',
      used: 0,
      usedYesterday: 20,
      limit: 40,
      blocked: true,
      icon: '🎮',
      lastUsed: 'Ontem às 16:30',
      sessions: []
    }
  ],

  selectedAppForDetail: null,

  pendingRequests: [
    { id: 'req-1', title: 'Tempo Adicional (+30 min)', app: 'YouTube Kids', time: 'há 8 min', minutes: 30 }
  ],

  enteredPin: '',
  correctPin: '1234'
};

// Canvas wave animation controller (Neon Green -> Electric Cyan)
class WaveAnimation {
  constructor(canvasId) {
    this.canvas = document.getElementById(canvasId);
    if (!this.canvas) return;
    this.ctx = this.canvas.getContext('2d');
    this.phase = 0;
    this.speed = 0.04;
    this.isRunning = true;

    this.resize();
    window.addEventListener('resize', () => this.resize());
    this.animate();
  }

  resize() {
    if (!this.canvas) return;
    const rect = this.canvas.getBoundingClientRect();
    this.canvas.width = rect.width || 176;
    this.canvas.height = rect.height || 176;
  }

  animate() {
    if (!this.isRunning) return;
    this.draw();
    this.phase += this.speed;
    requestAnimationFrame(() => this.animate());
  }

  draw() {
    if (!this.ctx || !this.canvas) return;
    const width = this.canvas.width;
    const height = this.canvas.height;
    this.ctx.clearRect(0, 0, width, height);

    const effectiveLimit = AppState.totalLimitMinutes + AppState.extraMinutes;
    const fraction = Math.min(Math.max(AppState.usedMinutes / (effectiveLimit || 1), 0.06), 0.94);
    const waterLevel = height * (1 - fraction);
    const waveHeight = 8;

    this.ctx.beginPath();
    this.ctx.moveTo(0, height);
    this.ctx.lineTo(0, waterLevel);

    for (let x = 0; x <= width; x += 3) {
      const angle = (x / width) * Math.PI * 2 + this.phase;
      const y = waterLevel + Math.sin(angle) * waveHeight;
      this.ctx.lineTo(x, y);
    }

    this.ctx.lineTo(width, height);
    this.ctx.closePath();

    const gradient = this.ctx.createLinearGradient(0, waterLevel - waveHeight, 0, height);
    gradient.addColorStop(0, 'rgba(0, 229, 255, 0.72)');
    gradient.addColorStop(1, 'rgba(158, 255, 0, 0.88)');

    this.ctx.fillStyle = gradient;
    this.ctx.fill();
  }
}

const App = {
  waveAnim: null,
  currentCategoryFilter: 'all',

  init() {
    window.Navigation.init();
    this.waveAnim = new WaveAnimation('wave-canvas');

    this.renderCurrentAppCard();
    this.renderScreenTime();
    this.renderUsageSummary();
    this.renderAppsList('all');
    this.renderPendingRequests();
    this.bindActionButtons();
    this.bindModals();
    this.bindKidsInteractions();
    this.bindFilterChips();

    const splashBtn = document.getElementById('btn-replay-splash');
    if (splashBtn) {
      splashBtn.addEventListener('click', () => this.replaySplash(3200));
    }
  },

  replaySplash(durationMs = 3200) {
    const overlay = document.getElementById('splash-overlay');
    if (!overlay) return;
    const subtitle = document.getElementById('splash-subtitle-text');
    if (subtitle) {
      const isKids = window.Navigation && window.Navigation.currentApp === 'kids';
      subtitle.textContent = isKids
        ? 'MonitorDad Kids • Ambiente Seguro'
        : 'MonitorDad Parent • Controle Familiar';
    }
    overlay.classList.add('active');
    clearTimeout(this._splashTimeout);
    this._splashTimeout = setTimeout(() => this.hideSplash(), durationMs);
  },

  hideSplash() {
    const overlay = document.getElementById('splash-overlay');
    if (!overlay) return;
    overlay.classList.remove('active');
    clearTimeout(this._splashTimeout);
  },

  showToast(message) {
    const toast = document.getElementById('demo-toast');
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('visible');
    clearTimeout(this._toastTimeout);
    this._toastTimeout = setTimeout(() => {
      toast.classList.remove('visible');
    }, 3000);
  },

  playChimeSound() {
    try {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      if (!AudioContext) return;
      const ctx = new AudioContext();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(587.33, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.15);
      gain.gain.setValueAtTime(0.25, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.5);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.5);
    } catch (e) {
      // Audio policy safe
    }
  },

  formatHm(minutes) {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return `${String(h).padStart(2, '0')}h ${String(m).padStart(2, '0')}min`;
  },

  formatCompactHm(minutes) {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m} min`;
  },

  renderCurrentAppCard() {
    const activeBox = document.getElementById('current-app-active-box');
    const waitingBox = document.getElementById('current-app-waiting-box');
    const badge = document.getElementById('current-app-badge');

    if (!activeBox || !waitingBox || !badge) return;

    if (AppState.currentAppActive) {
      activeBox.style.display = 'flex';
      waitingBox.style.display = 'none';
      badge.className = 'badge badge-cyan';
      badge.textContent = '● EM USO';
    } else {
      activeBox.style.display = 'none';
      waitingBox.style.display = 'flex';
      badge.className = 'badge badge-alert';
      badge.textContent = 'AGUARDANDO';
    }
  },

  renderScreenTime() {
    const effectiveLimit = AppState.totalLimitMinutes + AppState.extraMinutes;
    const used = AppState.kidsLowTimeMode ? effectiveLimit - 10 : AppState.usedMinutes;
    const remaining = Math.max(effectiveLimit - used, 0);

    const usedShort = this.formatCompactHm(used);
    const limitShort = this.formatCompactHm(effectiveLimit);
    const remShort = this.formatCompactHm(remaining);

    const elUsed = document.getElementById('screen-time-used-val');
    const elLimit = document.getElementById('screen-time-limit-val');
    const elRemaining = document.getElementById('screen-time-remaining-val');
    const mUsed = document.getElementById('metric-used-today');
    const mLimit = document.getElementById('metric-daily-limit');
    const mRem = document.getElementById('metric-remaining');

    if (elUsed) elUsed.textContent = this.formatCompactHm(AppState.usedMinutes);
    if (elLimit) elLimit.textContent = `Limite: ${limitShort}`;
    if (elRemaining) elRemaining.textContent = `Restam ${this.formatCompactHm(Math.max(effectiveLimit - AppState.usedMinutes, 0))} de uso hoje`;
    if (mUsed) mUsed.textContent = this.formatCompactHm(AppState.usedMinutes);
    if (mLimit) mLimit.textContent = limitShort;
    if (mRem) mRem.textContent = this.formatCompactHm(Math.max(effectiveLimit - AppState.usedMinutes, 0));

    // Kids Hero Card
    const elKidsRem = document.getElementById('kids-remaining-time-val');
    const kidsCard = document.getElementById('kids-hero-card');
    const kidsBadge = document.getElementById('kids-time-badge');
    const kidsBar = document.getElementById('kids-time-progress-bar');
    const kidsLabel = document.getElementById('kids-time-status-label');
    const kidsSubtitle = document.getElementById('kids-time-subtitle');

    const kidsRemaining = AppState.kidsLowTimeMode ? 10 : Math.max(effectiveLimit - AppState.usedMinutes, 0);
    const pctRemaining = Math.min(Math.max((kidsRemaining / (effectiveLimit || 1)) * 100, 4), 100);

    if (elKidsRem) elKidsRem.textContent = this.formatHm(kidsRemaining);
    if (kidsBar) {
      kidsBar.style.width = `${pctRemaining}%`;
      kidsBar.className = AppState.kidsLowTimeMode ? 'progress-bar-fill blocked' : 'progress-bar-fill';
    }
    if (kidsCard && kidsBadge && kidsLabel && kidsSubtitle) {
      if (AppState.kidsLowTimeMode) {
        kidsCard.classList.add('low-time');
        kidsBadge.className = 'badge badge-block';
        kidsBadge.textContent = 'QUASE NO FIM';
        kidsLabel.style.color = 'var(--color-alert-red)';
        kidsSubtitle.textContent = `Faltam apenas 10 min • Limite diário: ${limitShort}`;
      } else {
        kidsCard.classList.remove('low-time');
        kidsBadge.className = 'badge badge-positive';
        kidsBadge.textContent = 'LIBERADO';
        kidsLabel.style.color = 'var(--color-neon-green)';
        kidsSubtitle.textContent = `Contagem contínua em tempo real • Limite diário: ${limitShort}`;
      }
    }
  },

  renderUsageSummary() {
    const totalToday = AppState.apps.reduce((sum, a) => sum + (a.used || 0), 0);
    const totalYesterday = AppState.apps.reduce((sum, a) => sum + (a.usedYesterday || 0), 0);
    const usedApps = AppState.apps.filter(a => a.used > 0);
    const mostUsed = [...usedApps].sort((a, b) => b.used - a.used)[0];

    const diff = totalToday - totalYesterday;
    const diffText = diff > 0
      ? `+${diff} min vs ontem`
      : (diff < 0 ? `${diff} min vs ontem` : 'Mesmo tempo de ontem');

    const summaryValEl = document.getElementById('summary-total-today-val');
    const summaryDiffEl = document.getElementById('summary-diff-yesterday-val');
    const summaryCountEl = document.getElementById('summary-apps-count-val');
    const summaryTopEl = document.getElementById('summary-top-app-val');
    const syncBadgeEl = document.getElementById('summary-sync-badge');
    const syncStatusText = document.getElementById('summary-sync-status-text');

    if (summaryValEl) summaryValEl.textContent = this.formatCompactHm(totalToday);
    if (summaryDiffEl) summaryDiffEl.textContent = diffText;
    if (summaryCountEl) summaryCountEl.textContent = `${usedApps.length} apps hoje`;
    if (summaryTopEl && mostUsed) {
      summaryTopEl.textContent = `${mostUsed.name} (${this.formatCompactHm(mostUsed.used)})`;
    }

    if (syncBadgeEl && syncStatusText) {
      syncBadgeEl.className = 'badge badge-positive';
      syncBadgeEl.textContent = 'ONLINE';
      syncStatusText.textContent = `Sincronizado ${AppState.lastSyncFormatted}`;
    }
  },

  bindActionButtons() {
    const btnToggleCurrent = document.getElementById('btn-toggle-current-app-state');
    if (btnToggleCurrent) {
      btnToggleCurrent.addEventListener('click', () => {
        AppState.currentAppActive = !AppState.currentAppActive;
        this.renderCurrentAppCard();
        this.showToast(
          AppState.currentAppActive
            ? 'Exibindo app em uso agora (Demo)'
            : 'Exibindo estado aguardando atividade (Demo)'
        );
      });
    }

    document.querySelectorAll('[data-jump-tab]').forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.getAttribute('data-jump-tab');
        if (window.Navigation) window.Navigation.switchTab(target);
      });
    });

    const btnAdd15 = document.getElementById('btn-add-15m');
    if (btnAdd15) {
      btnAdd15.addEventListener('click', () => {
        AppState.extraMinutes += 15;
        this.renderScreenTime();
        this.showToast('+15 minutos adicionados (Demonstração)');
      });
    }

    const btnPause = document.getElementById('btn-pause-device');
    const statusText = document.getElementById('device-status-text');
    const statusBadge = document.getElementById('device-status-badge');

    if (btnPause) {
      btnPause.addEventListener('click', () => {
        AppState.isPaused = !AppState.isPaused;
        if (AppState.isPaused) {
          btnPause.innerHTML = '▶️ Liberar';
          btnPause.className = 'doh-btn doh-btn-primary';
          if (statusText) {
            statusText.textContent = 'Dispositivo Pausado';
            statusText.style.color = 'var(--color-alert-red)';
          }
          if (statusBadge) {
            statusBadge.textContent = 'PAUSADO';
            statusBadge.className = 'badge badge-block';
          }
          this.showToast('Dispositivo pausado (Demonstração)');
        } else {
          btnPause.innerHTML = '⏸️ Pausar';
          btnPause.className = 'doh-btn doh-btn-outline';
          if (statusText) {
            statusText.textContent = 'Protegido & Liberado';
            statusText.style.color = 'var(--color-neon-green)';
          }
          if (statusBadge) {
            statusBadge.textContent = 'SEGURO';
            statusBadge.className = 'badge badge-positive';
          }
          this.showToast('Dispositivo liberado (Demonstração)');
        }
      });
    }

    const btnSyncNow = document.getElementById('btn-sync-now');
    if (btnSyncNow) {
      btnSyncNow.addEventListener('click', () => {
        btnSyncNow.innerHTML = '🔄 Sincronizando...';
        setTimeout(() => {
          btnSyncNow.innerHTML = '🔄 Sincronizar';
          AppState.lastSyncFormatted = 'agora mesmo';
          this.renderUsageSummary();
          this.showToast('Telemetria sincronizada (Demonstração)');
        }, 400);
      });
    }

    const btnRefreshGps = document.getElementById('btn-refresh-gps');
    if (btnRefreshGps) {
      btnRefreshGps.addEventListener('click', () => {
        btnRefreshGps.innerHTML = '🔄 Buscando...';
        setTimeout(() => {
          btnRefreshGps.innerHTML = '🔄 Atualizar';
          const timeEl = document.getElementById('gps-last-updated');
          if (timeEl) {
            timeEl.textContent = 'Última atualização: Agora mesmo • Precisão (~15m)';
          }
          this.showToast('Localização atualizada (Demonstração)');
        }, 400);
      });
    }
  },

  renderAppsList(category) {
    this.currentCategoryFilter = category;
    const listContainer = document.getElementById('apps-list-container');
    if (!listContainer) return;

    const filtered = category === 'all'
      ? AppState.apps
      : AppState.apps.filter(a => a.category === category);

    listContainer.innerHTML = filtered.map(app => {
      const pct = Math.min((app.used / (app.limit || 1)) * 100, 100);
      const usedFormatted = this.formatCompactHm(app.used);
      const limitFormatted = this.formatCompactHm(app.limit);

      return `
        <div class="app-item-card" data-app-id="${app.id}">
          <div class="app-icon-wrap" onclick="window.App.openAppDetail('${app.id}')" style="cursor: pointer;">${app.icon}</div>
          <div class="app-info-body" onclick="window.App.openAppDetail('${app.id}')" style="cursor: pointer;">
            <div class="app-info-top">
              <span class="app-name-label">${app.name}</span>
              <span class="app-usage-time" style="color: ${app.blocked ? 'var(--color-alert-red)' : 'var(--color-neon-green)'}">
                ${app.blocked ? 'Bloqueado' : usedFormatted}
              </span>
            </div>
            <div class="progress-bar-bg">
              <div class="progress-bar-fill ${app.blocked ? 'blocked' : ''}" style="width: ${app.blocked ? '100%' : pct + '%'}"></div>
            </div>
            <div class="app-info-footer">
              <span>Último uso: ${app.lastUsed}</span>
              <span>Limite: ${limitFormatted}</span>
            </div>
          </div>
          <label class="switch-container">
            <input type="checkbox" class="switch-input app-toggle-switch" data-app-id="${app.id}" ${!app.blocked ? 'checked' : ''}>
            <span class="switch-track"><span class="switch-thumb"></span></span>
          </label>
        </div>
      `;
    }).join('');

    document.querySelectorAll('.app-toggle-switch').forEach(sw => {
      sw.addEventListener('change', (e) => {
        const id = e.target.getAttribute('data-app-id');
        const target = AppState.apps.find(a => a.id === id);
        if (target) {
          target.blocked = !e.target.checked;
          this.renderAppsList(this.currentCategoryFilter);
          this.renderOverviewTopApps();
          this.renderKidsAppsGrid();
          this.showToast(`${target.name} ${target.blocked ? 'bloqueado' : 'liberado'} (Demo)`);
        }
      });
    });

    this.renderOverviewTopApps();
  },

  renderOverviewTopApps() {
    const overviewTopList = document.getElementById('overview-top-apps');
    if (!overviewTopList) return;

    const top4 = [...AppState.apps].sort((a, b) => b.used - a.used).slice(0, 4);
    overviewTopList.innerHTML = top4.map(app => {
      const pct = Math.min((app.used / (app.limit || 1)) * 100, 100);
      const usedFormatted = this.formatCompactHm(app.used);

      return `
        <div class="app-item-card" onclick="window.App.openAppDetail('${app.id}')" style="cursor: pointer;">
          <div class="app-icon-wrap">${app.icon}</div>
          <div class="app-info-body">
            <div class="app-info-top">
              <span class="app-name-label">${app.name}</span>
              <span class="app-usage-time" style="color: ${app.blocked ? 'var(--color-alert-red)' : 'var(--color-neon-green)'}">
                ${app.blocked ? 'Bloqueado' : usedFormatted}
              </span>
            </div>
            <div class="progress-bar-bg">
              <div class="progress-bar-fill ${app.blocked ? 'blocked' : ''}" style="width: ${app.blocked ? '100%' : pct + '%'}"></div>
            </div>
          </div>
        </div>
      `;
    }).join('');
  },

  openAppDetail(appId) {
    const app = AppState.apps.find(a => a.id === appId);
    if (!app) return;
    AppState.selectedAppForDetail = app;

    document.getElementById('detail-modal-icon').textContent = app.icon;
    document.getElementById('detail-modal-title').textContent = app.name;
    document.getElementById('detail-modal-pkg').textContent = app.package;
    document.getElementById('detail-modal-category').textContent = app.category.toUpperCase();
    document.getElementById('detail-modal-today').textContent = this.formatCompactHm(app.used);
    document.getElementById('detail-modal-yesterday').textContent = this.formatCompactHm(app.usedYesterday);
    document.getElementById('detail-modal-lastused').textContent = app.lastUsed;

    const modalSlider = document.getElementById('detail-modal-slider');
    const modalSliderVal = document.getElementById('detail-modal-slider-val');
    if (modalSlider) {
      modalSlider.value = app.limit || 60;
      if (modalSliderVal) modalSliderVal.textContent = `${app.limit || 60} minutos`;
    }

    const modalSessions = document.getElementById('detail-modal-sessions');
    if (modalSessions) {
      if (app.sessions && app.sessions.length > 0) {
        modalSessions.innerHTML = app.sessions.map(s => `
          <div class="session-history-item">
            <div>
              <strong>${s.start} às ${s.end}</strong>
              <div style="font-size: 10px; color: var(--color-text-secondary);">Sessão registrada</div>
            </div>
            <span style="font-weight: 700; color: var(--color-neon-green);">${s.duration} min</span>
          </div>
        `).join('');
      } else {
        modalSessions.innerHTML = `
          <div style="font-size: 11px; color: var(--color-text-secondary); padding: 8px; text-align: center;">
            Nenhuma sessão registrada hoje.
          </div>
        `;
      }
    }

    this.openModal('modal-app-details');
  },

  renderPendingRequests() {
    const container = document.getElementById('pending-requests-container');
    const countBadge = document.getElementById('pending-req-count-badge');
    if (!container) return;

    if (countBadge) {
      countBadge.textContent = AppState.pendingRequests.length > 0
        ? `${AppState.pendingRequests.length} NOVA`
        : '0';
    }

    if (AppState.pendingRequests.length === 0) {
      container.innerHTML = `
        <div class="doh-card" style="text-align: center; color: var(--color-text-secondary); font-size: 13px; padding: 16px;">
          Nenhuma solicitação pendente no momento.
        </div>
      `;
      return;
    }

    container.innerHTML = AppState.pendingRequests.map(req => `
      <div class="doh-card" style="display: flex; align-items: center; justify-content: space-between; gap: 10px;">
        <div style="display: flex; align-items: center; gap: 12px;">
          <div style="width: 38px; height: 38px; border-radius: 50%; background: rgba(255,176,32,0.16); display: flex; align-items: center; justify-content: center; font-size: 18px;">
            ⏳
          </div>
          <div>
            <div style="font-size: 14px; font-weight: 700;">${req.title}</div>
            <div style="font-size: 11px; color: var(--color-text-secondary);">Lucas pediu +${req.minutes} min em ${req.app} (${req.time})</div>
          </div>
        </div>
        <button class="doh-btn doh-btn-primary" id="btn-review-req-${req.id}" style="padding: 6px 12px; font-size: 12px;">
          Revisar
        </button>
      </div>
    `).join('');

    AppState.pendingRequests.forEach(req => {
      const btn = document.getElementById(`btn-review-req-${req.id}`);
      if (btn) {
        btn.addEventListener('click', () => this.openModal('modal-review-request'));
      }
    });
  },

  bindFilterChips() {
    const chips = document.querySelectorAll('.filter-chip-btn');
    chips.forEach(chip => {
      chip.addEventListener('click', () => {
        chips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        this.renderAppsList(chip.getAttribute('data-cat'));
      });
    });
  },

  bindModals() {
    const btnOpenSound = document.getElementById('btn-open-sound-modal');
    if (btnOpenSound) btnOpenSound.addEventListener('click', () => this.openModal('modal-play-sound'));

    const btnTriggerSound = document.getElementById('btn-confirm-sound');
    if (btnTriggerSound) {
      btnTriggerSound.addEventListener('click', () => {
        this.playChimeSound();
        this.closeModal('modal-play-sound');
        this.showToast('Sinal sonoro disparado (Demonstração)');
      });
    }

    const btnApproveReq = document.getElementById('btn-approve-request');
    if (btnApproveReq) {
      btnApproveReq.addEventListener('click', () => {
        AppState.extraMinutes += 30;
        AppState.pendingRequests = [];
        this.renderScreenTime();
        this.renderPendingRequests();
        this.closeModal('modal-review-request');
        this.showToast('Solicitação aprovada (+30 min) — Demo');
      });
    }

    const btnRejectReq = document.getElementById('btn-reject-request');
    if (btnRejectReq) {
      btnRejectReq.addEventListener('click', () => {
        AppState.pendingRequests = [];
        this.renderPendingRequests();
        this.closeModal('modal-review-request');
        this.showToast('Solicitação recusada (Demo)');
      });
    }

    const detailSlider = document.getElementById('detail-modal-slider');
    const detailSliderVal = document.getElementById('detail-modal-slider-val');
    if (detailSlider && detailSliderVal) {
      detailSlider.addEventListener('input', (e) => {
        detailSliderVal.textContent = `${e.target.value} minutos`;
      });
    }

    const btnSaveLimit = document.getElementById('btn-save-app-limit');
    if (btnSaveLimit) {
      btnSaveLimit.addEventListener('click', () => {
        if (AppState.selectedAppForDetail && detailSlider) {
          AppState.selectedAppForDetail.limit = parseInt(detailSlider.value, 10);
          this.renderAppsList(this.currentCategoryFilter);
          this.showToast(`Limite salvo (${detailSlider.value} min) — Demo`);
        }
      });
    }

    const btnOpenPerm = document.getElementById('btn-kids-perm-info');
    if (btnOpenPerm) btnOpenPerm.addEventListener('click', () => this.openModal('modal-permission-info'));

    const btnTogglePermDemo = document.getElementById('btn-toggle-perm-demo');
    if (btnTogglePermDemo) {
      btnTogglePermDemo.addEventListener('click', () => {
        AppState.hasKidsUsagePermission = !AppState.hasKidsUsagePermission;
        this.updateKidsPermissionUI();
        this.closeModal('modal-permission-info');
        this.showToast(`Permissão ${AppState.hasKidsUsagePermission ? 'Ativa' : 'Pendente'} (Demo)`);
      });
    }

    document.querySelectorAll('[data-close-modal]').forEach(el => {
      el.addEventListener('click', () => this.closeModal(el.getAttribute('data-close-modal')));
    });
  },

  openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.add('open');
  },

  closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('open');
  },

  bindKidsInteractions() {
    this.renderKidsAppsGrid();
    this.updateKidsPermissionUI();

    const btnLowTime = document.getElementById('btn-kids-toggle-low-time');
    if (btnLowTime) {
      btnLowTime.addEventListener('click', () => {
        AppState.kidsLowTimeMode = !AppState.kidsLowTimeMode;
        this.renderScreenTime();
        this.showToast(
          AppState.kidsLowTimeMode
            ? 'Simulando tempo restante baixo (10 min)'
            : 'Tempo restante normal restaurado (Demo)'
        );
      });
    }

    const btnShowRest = document.getElementById('btn-kids-show-rest');
    const overlayRest = document.getElementById('kids-rest-overlay');
    if (btnShowRest && overlayRest) {
      btnShowRest.addEventListener('click', () => { overlayRest.style.display = 'flex'; });
    }

    const btnCloseRest = document.getElementById('btn-kids-close-rest');
    if (btnCloseRest && overlayRest) {
      btnCloseRest.addEventListener('click', () => { overlayRest.style.display = 'none'; });
    }

    const btnShowLock = document.getElementById('btn-kids-show-lock');
    const overlayLock = document.getElementById('kids-lock-overlay');
    if (btnShowLock && overlayLock) {
      btnShowLock.addEventListener('click', () => { overlayLock.style.display = 'flex'; });
    }

    const btnCloseLock = document.getElementById('btn-kids-close-lock');
    if (btnCloseLock && overlayLock) {
      btnCloseLock.addEventListener('click', () => { overlayLock.style.display = 'none'; });
    }

    const btnKidsReqTime = document.getElementById('btn-kids-request-time');
    if (btnKidsReqTime) {
      btnKidsReqTime.addEventListener('click', () => {
        this.showToast('Solicitação de mais tempo enviada aos pais (Demonstração)');
      });
    }

    document.querySelectorAll('[data-open-pin]').forEach(btn => {
      btn.addEventListener('click', () => {
        AppState.enteredPin = '';
        this.updatePinDots();
        this.openModal('modal-pin-auth');
      });
    });

    document.querySelectorAll('.keypad-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const val = btn.getAttribute('data-key');
        if (val === 'C') {
          AppState.enteredPin = '';
        } else if (val === 'DEL') {
          AppState.enteredPin = AppState.enteredPin.slice(0, -1);
        } else if (AppState.enteredPin.length < 4) {
          AppState.enteredPin += val;
          if (AppState.enteredPin.length === 4) {
            if (AppState.enteredPin === AppState.correctPin) {
              this.closeModal('modal-pin-auth');
              if (overlayRest) overlayRest.style.display = 'none';
              if (overlayLock) overlayLock.style.display = 'none';
              this.showToast('PIN correto! Acesso dos pais liberado (Demo)');
            } else {
              this.showToast('PIN incorreto! Use 1234 (Demo)');
              AppState.enteredPin = '';
            }
          }
        }
        this.updatePinDots();
      });
    });
  },

  updateKidsPermissionUI() {
    const badge = document.getElementById('kids-perm-badge');
    const desc = document.getElementById('kids-perm-desc');
    if (badge && desc) {
      if (AppState.hasKidsUsagePermission) {
        badge.className = 'badge badge-positive';
        badge.textContent = 'ATIVO';
        desc.textContent = 'Monitoramento ativo (Demo)';
      } else {
        badge.className = 'badge badge-alert';
        badge.textContent = 'PENDENTE';
        desc.textContent = 'Aguardando permissão (Demo)';
      }
    }
  },

  updatePinDots() {
    document.querySelectorAll('.pin-dot').forEach((dot, idx) => {
      if (idx < AppState.enteredPin.length) dot.classList.add('filled');
      else dot.classList.remove('filled');
    });
  },

  renderKidsAppsGrid() {
    const grid = document.getElementById('kids-apps-grid');
    if (!grid) return;

    grid.innerHTML = AppState.apps.map(app => `
      <div class="kids-app-card ${app.blocked ? 'is-blocked' : ''}"
           onclick="window.App.showToast('${app.blocked ? `${app.name} está bloqueado (Demo)` : `Abrindo ${app.name} (Demo)`}')">
        <div style="font-size: 30px; margin-bottom: 6px;">${app.icon}</div>
        <div style="font-size: 13px; font-weight: 700; margin-bottom: 6px;">${app.name}</div>
        <span class="badge ${app.blocked ? 'badge-block' : 'badge-positive'}" style="font-size: 9px;">
          ${app.blocked ? 'BLOQUEADO' : 'LIBERADO'}
        </span>
      </div>
    `).join('');
  }
};

window.AppState = AppState;
window.App = App;

document.addEventListener('DOMContentLoaded', () => {
  App.init();
});
