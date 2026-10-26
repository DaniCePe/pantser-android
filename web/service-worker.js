/* Pantser PWA service worker. Offline-first: caches the app shell on
   install so the app opens and generates PDFs with no connection.
   v1.7.0 adds the settings-gated Item labels page (bundled logo and
   Tinos fonts included so item sheets work offline too). */
const CACHE = 'pantser-v12';
const ASSETS = [
  './',
  './index.html',
  './css/styles.css',
  './js/app.js',
  './js/strings.js',
  './js/strings-web.js',
  './js/pdf-lib.min.js',
  './js/fontkit.umd.min.js',
  './assets/logo_readable.jpg',
  './assets/fonts/Tinos-Regular.ttf',
  './assets/fonts/Tinos-Bold.ttf',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png'
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true }).then((hit) => {
      if (hit) return hit;
      return fetch(e.request).then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
        return res;
      }).catch(() => {
        if (e.request.mode === 'navigate') return caches.match('./index.html');
        throw new Error('offline');
      });
    })
  );
});
