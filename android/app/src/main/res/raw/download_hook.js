// Injected into every page by MainActivity. Android's web view cannot save "download" links on its own,
// so this catches them, reads the file in the page and hands it to the app, which shows a "Save as" screen.
(function () {
  if (window.__androidHook || !window.AndroidBridge) return;
  window.__androidHook = true;

  // Remember every Blob behind an object URL, so the file can still be read if the page revokes the URL right away.
  var blobs = {};
  var create = URL.createObjectURL;
  URL.createObjectURL = function (obj) {
    var url = create.apply(URL, arguments);
    try { if (obj instanceof Blob) blobs[url] = obj; } catch (e) {}
    return url;
  };

  function send(name, blob) {
    var reader = new FileReader();
    reader.onload = function () { AndroidBridge.save(name, reader.result); };
    reader.onerror = function () { AndroidBridge.toast('Could not read the file'); };
    reader.readAsDataURL(blob);
  }

  function nameFor(a) {
    var n = a.getAttribute('download');
    if (n) return n;
    try { return decodeURIComponent(new URL(a.href).pathname.split('/').pop()) || 'download'; }
    catch (e) { return 'download'; }
  }

  function saveUrl(href, name) {
    if (blobs[href]) { send(name, blobs[href]); return; }
    fetch(href)
      .then(function (r) { return r.blob(); })
      .then(function (b) { send(name, b); })
      .catch(function () { AndroidBridge.toast('Download failed'); });
  }
  window.__androidSaveUrl = saveUrl;

  // Links the user taps.
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (!a || !a.href) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    saveUrl(a.href, nameFor(a));
  }, true);

  // Links the page clicks from code, the usual way to export a file.
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (this.hasAttribute('download') && this.href) { saveUrl(this.href, nameFor(this)); return; }
    return click.apply(this, arguments);
  };
})();
