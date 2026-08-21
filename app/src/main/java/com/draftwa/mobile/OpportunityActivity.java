package com.draftwa.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * In-app shell for the server-first opportunity mapping module.
 *
 * The WebView is deliberately restricted to DraftWA's HTTPS origin. External
 * links (Google Maps, company sites, OSM) are opened by the system browser.
 * No JavaScript bridge is exposed to the page, so the web module cannot call
 * privileged Android methods or touch the AccessibilityService.
 */
public class OpportunityActivity extends Activity {
    private static final String ALLOWED_HOST = "draftwa-mobile-five.vercel.app";
    private WebView webView;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLog.event(this, "OPPORTUNITY_OPEN", BuildConfig.OPPORTUNITY_URL);
        setContentView(buildUi());
        loadHome();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 248));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(Color.WHITE);

        Button back = button("← Retour");
        back.setOnClickListener(v -> finish());
        bar.addView(back, wrap());

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMargins(dp(8), 0, dp(8), 0);
        TextView title = new TextView(this);
        title.setText("Prospection serveur");
        title.setTextColor(Color.rgb(17, 27, 33));
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        status = new TextView(this);
        status.setText("Connexion au backend…");
        status.setTextColor(Color.rgb(84, 101, 111));
        status.setTextSize(11);
        titleBlock.addView(title);
        titleBlock.addView(status);
        bar.addView(titleBlock, titleLp);

        Button browser = button("Navigateur");
        browser.setOnClickListener(v -> openExternal(BuildConfig.OPPORTUNITY_URL));
        bar.addView(browser, wrap());
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        configureWebView(webView);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void configureWebView(WebView view) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(false);
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);
        view.setWebChromeClient(new WebChromeClient());
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return route(request == null ? null : request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return route(url == null ? null : Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                if (status != null) status.setText("Backend chargé • calcul côté serveur");
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(v, request, error);
                if (request != null && request.isForMainFrame() && status != null) {
                    status.setText("Web indisponible • DraftWA principal reste hors ligne");
                }
            }

            @Override
            public void onSafeBrowsingHit(WebView v, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
                DiagnosticLog.event(OpportunityActivity.this, "OPPORTUNITY_SAFE_BROWSING_BLOCK", String.valueOf(threatType));
                if (callback != null) callback.backToSafety(true);
            }
        });
    }

    private boolean route(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean allowedHost = ALLOWED_HOST.equalsIgnoreCase(host);
        if (https && allowedHost) return false;
        if ("http".equalsIgnoreCase(scheme) || https) {
            openExternal(uri.toString());
        } else {
            Toast.makeText(this, "Lien externe bloqué", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void loadHome() {
        Uri uri = Uri.parse(BuildConfig.OPPORTUNITY_URL);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ALLOWED_HOST.equalsIgnoreCase(uri.getHost())) {
            status.setText("URL backend invalide");
            DiagnosticLog.event(this, "OPPORTUNITY_URL_REJECTED", BuildConfig.OPPORTUNITY_URL);
            return;
        }
        webView.loadUrl(uri.toString());
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            Toast.makeText(this, "Navigateur indisponible", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinHeight(dp(44));
        return b;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }
}
