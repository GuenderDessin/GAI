package gtok;

import ca.weblite.webview.WebViewPopupEvent;
import ca.weblite.webview.WebViewPopupHandler;
import ca.weblite.webview.swing.WebViewComponent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

/** Edge WebView2 on Windows, hosted entirely inside a Swing internal frame. */
public final class VideoBrowserFrame extends JInternalFrame {
    private static final ObjectMapper JSON = new ObjectMapper();
    private WebViewComponent browser = WebViewComponent.create(WebViewComponent.Mode.HEAVYWEIGHT);
    private final JLabel status = new JLabel("Starting embedded browser...");
    private final JTextField address = new JTextField();
    private final Timer monitor = new Timer(1500, e -> updateMediaStatus());
    private volatile boolean disposed;
    private boolean checking;
    private int failedChecks;
    private String playerUrl;
    private String postUrl;
    private int navigationGeneration;

    public VideoBrowserFrame() {
        super("Video browser", true, true, true, true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(420, 360));
        setSize(540, 680);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(new Color(25, 27, 37));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        status.setForeground(new Color(235, 237, 245));
        JPanel toolbar = new JPanel(new BorderLayout(6, 6));
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        JButton player = new JButton("Video player");
        JButton original = new JButton("Original post");
        JButton reload = new JButton("Reload");
        buttons.add(player); buttons.add(original); buttons.add(reload);
        toolbar.add(buttons, BorderLayout.NORTH);
        address.setEditable(false);
        toolbar.add(address, BorderLayout.SOUTH);
        content.add(toolbar, BorderLayout.NORTH);
        content.add(browser, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        setContentPane(content);
        configureBrowser();
        player.addActionListener(e -> navigate(playerUrl));
        original.addActionListener(e -> navigate(postUrl));
        reload.addActionListener(e -> {
            if (!disposed) {
                navigationGeneration++;
                status.setText("Reloading...");
                browser.eval("location.reload();");
            }
        });
        addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameClosed(InternalFrameEvent e) { release(); }
            @Override public void internalFrameIconified(InternalFrameEvent e) {
                monitor.stop();
                navigationGeneration++;
                // Removing the internal frame destroys WebView's native peer.
                // Restoration must use fresh navigation and popup dispatchers.
            }
            @Override public void internalFrameDeiconified(InternalFrameEvent e) {
                if (!disposed) {
                    String url = address.getText();
                    browser.dispose();
                    content.remove(browser);
                    browser = WebViewComponent.create(WebViewComponent.Mode.HEAVYWEIGHT);
                    configureBrowser();
                    content.add(browser, BorderLayout.CENTER);
                    content.revalidate();
                    content.repaint();
                    navigate(url);
                    monitor.start();
                }
            }
        });
        monitor.start();
    }

    private void configureBrowser() {
        browser.setNavigationHandler(e -> !disposed && (isWebUrl(e.url())
                || "about:blank".equals(e.url()) || "about:srcdoc".equals(e.url())));
        browser.setPopupHandler(new WebViewPopupHandler() {
            @Override public boolean popupRequested(WebViewPopupEvent event) {
                if (!disposed && event.userGesture() && isWebUrl(event.targetUrl())) {
                    SwingUtilities.invokeLater(() -> navigate(event.targetUrl()));
                }
                return false;
            }
        });
        browser.setDownloadHandler(null);
        browser.setPasswordManagerEnabled(false);
    }

    public void showVideo(String id, URI original, String creator) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Swing EDT required");
        if (disposed) throw new IllegalStateException("Browser is closed");
        if (original == null || !isTikTokUrl(original)) throw new IllegalArgumentException("Invalid TikTok URL");
        playerUrl = playerUrl(id);
        postUrl = original.toString();
        setTitle("Watch @" + creator);
        navigate(playerUrl);
    }

    static String playerUrl(String id) {
        if (id == null || !id.matches("[0-9]{1,30}")) throw new IllegalArgumentException("Invalid video ID");
        return "https://www.tiktok.com/player/v1/" + id
                + "?controls=1&description=1&autoplay=0&fullscreen_button=0";
    }

    private void navigate(String url) {
        if (disposed || url == null || !isWebUrl(url)) return;
        navigationGeneration++;
        failedChecks = 0;
        address.setText(url);
        address.setCaretPosition(0);
        status.setText("Loading video...");
        browser.setUrl(url);
    }

    private void updateMediaStatus() {
        if (disposed || checking || !browser.isShowing()) return;
        checking = true;
        int generation = navigationGeneration;
        browser.evalAsync("var v=document.querySelector('video'); return {url:location.href,"
                + "loaded:document.readyState==='complete',video:!!v,ready:v?v.readyState:0,"
                + "error:v&&v.error?v.error.code:0,paused:v?v.paused:true,time:v?v.currentTime:0};")
                .orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                    checking = false;
                    if (disposed || generation != navigationGeneration) return;
                    if (error != null) {
                        failedChecks++;
                        status.setText(failedChecks >= 3
                                ? "Browser unavailable. Check WebView2 Runtime, then close and reopen this window."
                                : "Browser is starting or busy...");
                        return;
                    }
                    failedChecks = 0;
                    try {
                        JsonNode data = JSON.readTree(result);
                        String url = data.path("url").asText("");
                        if (isWebUrl(url) && !address.getText().equals(url)) {
                            address.setText(url); address.setCaretPosition(0);
                        }
                        if (data.path("error").asInt() != 0) {
                            status.setText("TikTok video unavailable. Try Reload or Original post.");
                        } else if (data.path("video").asBoolean()) {
                            if (data.path("ready").asInt() < 2) status.setText("Buffering video...");
                            else if (data.path("paused").asBoolean()) status.setText("Video ready. Click Play inside the player.");
                            else status.setText(String.format(Locale.ROOT, "Playing inside GTok - %.0f seconds", data.path("time").asDouble()));
                        } else if (data.path("loaded").asBoolean()) {
                            status.setText("Page loaded. Click Play inside the player, or complete any TikTok prompt.");
                        }
                    } catch (Exception ignored) {
                        status.setText("Loading page...");
                    }
                }));
    }

    static boolean isTikTokUrl(URI uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && (host.equalsIgnoreCase("tiktok.com") || host.toLowerCase(Locale.ROOT).endsWith(".tiktok.com"));
    }

    private static boolean isWebUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (RuntimeException ex) { return false; }
    }

    private void release() {
        if (disposed) return;
        disposed = true;
        monitor.stop();
        browser.dispose();
    }

    @Override public void dispose() {
        release();
        super.dispose();
    }
}
