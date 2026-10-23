package online.teamdream.tap_shell;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends ComponentActivity {

    private static final String HOME = "https://tap.teamdream.online/";
    private static final String HOST = "tap.teamdream.online";
    private static final String PREFS = "tap_shell";
    private static final String KEY_FULLSCREEN = "fullscreen";

    private FrameLayout root;
    private WebView web;
    private ImageButton fsButton;
    private ImageButton reloadButton;
    private LinearLayout buttons;

    /** Режим «без системных панелей», который включает кнопка. По умолчанию включён. */
    private boolean fullscreen;

    /** Скрыты ли сейчас системные панели (полный экран или видео во весь экран). */
    private boolean barsHidden;

    private ValueCallback<Uri[]> filePathCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private final ActivityResultLauncher<Intent> fileChooser = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;
                Uri[] uris = WebChromeClient.FileChooserParams.parseResult(
                        result.getResultCode(), result.getData());
                filePathCallback.onReceiveValue(uris);
                filePathCallback = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        web = findViewById(R.id.web);
        fsButton = findViewById(R.id.fs_button);
        reloadButton = findViewById(R.id.reload_button);
        buttons = findViewById(R.id.buttons);

        // Отступы под панели и вырез камеры считаем сами: в полном экране страница
        // занимает весь экран, включая полосу у камеры.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets bars = barsHidden ? Insets.NONE : insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            // Кнопки опускаем ниже камеры, чтобы она их не закрывала.
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) buttons.getLayoutParams();
            int top = (barsHidden ? cutout.top : 0) + dp(4);
            if (lp.topMargin != top) {
                lp.topMargin = top;
                buttons.setLayoutParams(lp);
            }
            return WindowInsetsCompat.CONSUMED;
        });

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        fullscreen = prefs.getBoolean(KEY_FULLSCREEN, true);

        setupWebView();

        fsButton.setOnClickListener(v -> {
            fullscreen = !fullscreen;
            prefs.edit().putBoolean(KEY_FULLSCREEN, fullscreen).apply();
            applyFullscreen();
        });
        applyFullscreen();

        reloadButton.setOnClickListener(v -> web.reload());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView();
                } else if (web.canGoBack()) {
                    web.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState != null && web.restoreState(savedInstanceState) != null) {
            return;
        }
        web.loadUrl(HOME);
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);

        web.addJavascriptInterface(new Bridge(), "TapShell");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // true = не загружать. Пускаем только tap.teamdream.online по https.
                return !isAllowed(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                recreate();
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    fileChooser.launch(params.createIntent());
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fsButton.setVisibility(View.GONE);
                reloadButton.setVisibility(View.GONE);
                applyBars(true);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (url.startsWith("blob:")) {
                downloadBlob(url, name);
            } else if (url.startsWith("data:")) {
                saveDataUrl(url, name);
            } else if (isAllowed(Uri.parse(url))) {
                enqueueDownload(url, userAgent, mimeType, name);
            }
        });
    }

    private static boolean isAllowed(Uri uri) {
        return "https".equals(uri.getScheme()) && HOST.equalsIgnoreCase(uri.getHost());
    }

    // ---- Полный экран ----

    private void applyFullscreen() {
        applyBars(fullscreen || customView != null);
        fsButton.setImageResource(fullscreen
                ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
    }

    @SuppressWarnings("deprecation")
    private void applyBars(boolean hide) {
        barsHidden = hide;
        ViewCompat.requestApplyInsets(root);
        // Старый флаг тоже ставим: на части прошивок он надёжнее скрывает верхнюю панель.
        if (hide) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (hide) {
            c.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            c.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        fsButton.setVisibility(View.VISIBLE);
        reloadButton.setVisibility(View.VISIBLE);
        applyFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyBars(fullscreen || customView != null);
    }

    // ---- Загрузка и скачивание файлов ----

    private void enqueueDownload(String url, String userAgent, String mimeType, String name) {
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null && !mimeType.isEmpty()) r.setMimeType(mimeType);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) r.addRequestHeader("Cookie", cookie);
            if (userAgent != null) r.addRequestHeader("User-Agent", userAgent);
            r.setTitle(name);
            r.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            toast("Загрузка: " + name);
        } catch (Exception e) {
            toast("Не удалось скачать файл");
        }
    }

    /** Достаёт содержимое blob: через JS и отдаёт в Bridge.saveDataUrl. */
    private void downloadBlob(String url, String name) {
        String js = "(function(){var x=new XMLHttpRequest();"
                + "x.open('GET'," + JSONObject.quote(url) + ",true);"
                + "x.responseType='blob';"
                + "x.onload=function(){var r=new FileReader();"
                + "r.onloadend=function(){TapShell.saveDataUrl(r.result," + JSONObject.quote(name) + ");};"
                + "r.readAsDataURL(x.response);};"
                + "x.onerror=function(){TapShell.fail();};"
                + "x.send();})();";
        web.evaluateJavascript(js, null);
    }

    private void saveDataUrl(String dataUrl, String name) {
        try {
            int comma = dataUrl.indexOf(',');
            String header = dataUrl.substring(5, comma);
            String payload = dataUrl.substring(comma + 1);
            boolean base64 = header.endsWith(";base64");
            String mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
            if (mime.isEmpty()) mime = "application/octet-stream";
            byte[] bytes = base64
                    ? Base64.decode(payload, Base64.DEFAULT)
                    : URLDecoder.decode(payload, "UTF-8").getBytes(StandardCharsets.UTF_8);

            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("insert failed");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException("no stream");
                os.write(bytes);
            }
            toast("Сохранено в Загрузки: " + name);
        } catch (Exception e) {
            toast("Не удалось сохранить файл");
        }
    }

    private void toast(String text) {
        runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    /** Доступен только странице tap.teamdream.online (другие адреса блокируются). */
    private class Bridge {
        @JavascriptInterface
        public void saveDataUrl(String dataUrl, String name) {
            MainActivity.this.saveDataUrl(dataUrl, name);
        }

        @JavascriptInterface
        public void fail() {
            toast("Не удалось скачать файл");
        }
    }

    // ---- Жизненный цикл ----

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        applyBars(fullscreen || customView != null);
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        web.destroy();
        super.onDestroy();
    }
}
