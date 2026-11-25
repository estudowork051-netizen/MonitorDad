/**
 * MonitorDad • by HoD — Navigation Controller
 * Handles tab transitions, view frame scaling, and Parent/Kids app switching.
 */

const Navigation = {
  currentTab: 'overview',
  currentApp: 'parent',
  currentFrame: 'phone',

  init() {
    this.bindTabButtons();
    this.bindAppSwitcher();
    this.bindFrameSwitcher();
    const params = new URLSearchParams(window.location.search);
    if (params.get('app') === 'kids') {
      this.switchApp('kids');
    }
  },

  bindTabButtons() {
    const tabButtons = document.querySelectorAll('.nav-tab-btn');
    tabButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const targetTab = btn.getAttribute('data-tab');
        this.switchTab(targetTab);
      });
    });
  },

  switchTab(tabId) {
    this.currentTab = tabId;

    // Update bottom nav buttons
    document.querySelectorAll('.nav-tab-btn').forEach(btn => {
      if (btn.getAttribute('data-tab') === tabId) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    // Update screens
    document.querySelectorAll('.tab-screen-content').forEach(screen => {
      if (screen.getAttribute('id') === `tab-${tabId}`) {
        screen.style.display = 'block';
      } else {
        screen.style.display = 'none';
      }
    });

    // Scroll container back to top
    const scrollArea = document.querySelector('.screen-scroll-area');
    if (scrollArea) {
      scrollArea.scrollTop = 0;
    }
  },

  bindAppSwitcher() {
    const appButtons = document.querySelectorAll('[data-switch-app]');
    appButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const app = btn.getAttribute('data-switch-app');
        this.switchApp(app);
      });
    });
  },

  switchApp(app) {
    this.currentApp = app;

    // Update switcher pill buttons
    document.querySelectorAll('[data-switch-app]').forEach(btn => {
      if (btn.getAttribute('data-switch-app') === app) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    const parentRoot = document.getElementById('parent-app-root');
    const kidsRoot = document.getElementById('kids-app-root');
    const bottomNav = document.getElementById('parent-bottom-nav');

    if (app === 'parent') {
      if (parentRoot) parentRoot.style.display = 'block';
      if (kidsRoot) kidsRoot.style.display = 'none';
      if (bottomNav) bottomNav.style.display = 'flex';
      this.switchTab(this.currentTab);
    } else {
      if (parentRoot) parentRoot.style.display = 'none';
      if (kidsRoot) kidsRoot.style.display = 'block';
      if (bottomNav) bottomNav.style.display = 'none';
    }
  },

  bindFrameSwitcher() {
    const frameButtons = document.querySelectorAll('[data-switch-frame]');
    const deviceFrame = document.querySelector('.device-frame');

    frameButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const frame = btn.getAttribute('data-switch-frame');
        this.currentFrame = frame;

        frameButtons.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');

        if (deviceFrame) {
          deviceFrame.classList.remove('view-phone', 'view-tablet', 'view-desktop');
          deviceFrame.classList.add(`view-${frame}`);
        }
      });
    });
  }
};

window.Navigation = Navigation;
