package io.github.manojhunasimarad.webshell;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shows one bundled web app full screen. The files come from the app itself (see AssetServer),
 * so the app works offline; links to other sites open in the phone's browser.
 */
public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private static final int REQ_SAVE = 2;
    private static final int REQ_CAMERA = 3;

    private WebView web;
    private String downloadHook;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;
    private byte[] pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean serving = AssetServer.ensureStarted(getAssets(), BuildConfig.PORT);
        downloadHook = readRaw(R.raw.download_hook);

        int background = getColor(R.color.app_background);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(background);
        web = new WebView(this);
        web.setBackgroundColor(background);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        applyInsets(root);
        configureWebView();
        registerBack();

        if (!serving) {
            web.loadDataWithBaseURL(null,
                    "<p style='font:16px sans-serif;padding:24px'>The app could not start its local server. "
                            + "Close it fully and open it again.</p>",
                    "text/html", "utf-8", null);
            return;
        }
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl("http://127.0.0.1:" + BuildConfig.PORT + "/");
        }
    }

    @SuppressWarnings("deprecation")
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString() + " WebAppsShell/" + BuildConfig.VERSION_NAME);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return openOutside(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (downloadHook != null) view.evaluateJavascript(downloadHook, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    toast("No file picker is available");
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handlePermission(request));
            }
        });

        // Downloads the page hook did not catch: let the page read the file, then save it.
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            if (isOwn(uri) || scheme.equals("blob") || scheme.equals("data")) {
                String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                web.evaluateJavascript("window.__androidSaveUrl && window.__androidSaveUrl("
                        + JSONObject.quote(url) + "," + JSONObject.quote(name) + ")", null);
            } else {
                openOutside(uri);
            }
        });
    }

    private boolean isOwn(Uri uri) {
        return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                && uri.getPort() == BuildConfig.PORT;
    }

    /** Returns true when the link was handed to another app. */
    private boolean openOutside(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        if (isOwn(uri) || scheme.equals("about") || scheme.equals("blob") || scheme.equals("data")
                || scheme.equals("javascript")) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (Exception e) {
            toast("No app can open this link");
        }
        return true;
    }

    private void applyInsets(View root) {
        if (Build.VERSION.SDK_INT < 30) return;
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(isNight() ? 0 : mask, mask);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
    }

    private boolean isNight() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void registerBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        }
    }

    private void goBack() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            finish();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goBack(); // Android 12 and older; newer versions use the callback above.
    }

    private void handlePermission(PermissionRequest request) {
        boolean wantsCamera = false;
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
        }
        if (!wantsCamera) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            return;
        }
        if (pendingPermission != null) pendingPermission.deny();
        pendingPermission = request;
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_CAMERA || pendingPermission == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            pendingPermission.deny();
        }
        pendingPermission = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        if (requestCode == REQ_SAVE) {
            byte[] bytes = pendingSave;
            pendingSave = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && bytes != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                    if (os == null) throw new IOException("no stream");
                    os.write(bytes);
                    toast("Saved");
                } catch (IOException e) {
                    toast("Could not save the file");
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String readRaw(int id) {
        try (InputStream is = getResources().openRawResource(id)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) > 0) buffer.write(chunk, 0, n);
            return buffer.toString("UTF-8");
        } catch (IOException e) {
            return null;
        }
    }

    /** Called from the page (download_hook.js) to save an exported file where the user chooses. */
    private final class Bridge {
        @JavascriptInterface
        public void save(String name, String dataUrl) {
            if (dataUrl == null || !dataUrl.startsWith("data:")) return;
            int comma = dataUrl.indexOf(',');
            if (comma < 0) return;
            String meta = dataUrl.substring(5, comma);
            boolean isBase64 = meta.endsWith(";base64");
            String mime = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
            if (mime.isEmpty()) mime = "application/octet-stream";
            final byte[] bytes;
            try {
                bytes = isBase64
                        ? Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                        : Uri.decode(dataUrl.substring(comma + 1)).getBytes(StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                toast("Could not read the file");
                return;
            }
            final String type = mime;
            final String fileName = (name == null || name.trim().isEmpty()) ? "download" : name.trim();
            runOnUiThread(() -> {
                pendingSave = bytes;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(type)
                        .putExtra(Intent.EXTRA_TITLE, fileName);
                try {
                    startActivityForResult(intent, REQ_SAVE);
                } catch (ActivityNotFoundException e) {
                    pendingSave = null;
                    MainActivity.this.toast("No app can save files");
                }
            });
        }

        @JavascriptInterface
        public void toast(String message) {
            runOnUiThread(() -> MainActivity.this.toast(message));
        }
    }
}
