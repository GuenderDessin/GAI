package gtok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.*;
import java.net.URI;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

/** Desktop trending-feed explorer. Run this class or the GTok project. */
public class TrendingFrame extends JFrame {
    private static final Color BG = new Color(15, 16, 22);
    private static final Color PANEL = new Color(25, 27, 37);
    private static final Color TEXT = new Color(235, 237, 245);
    private static final Color MUTED = new Color(159, 166, 188);
    private static final Color ACCENT = new Color(116, 94, 242);
    private final JComboBox<String> region = new JComboBox<>(new String[]{"US", "GB", "CA", "FR", "DE", "BR", "AU", "JP"});
    private final JButton fetch = button("Fetch trending feed");
    private final JButton watch = button("Watch video here");
    private final JLabel status = label("Ready. Fetch a feed to begin.", 13, MUTED);
    private final JLabel count = label("0 videos", 24, TEXT);
    private final JLabel credits = label("Credits remaining: --", 14, MUTED);
    private final JLabel selected = label("Select a video", 20, TEXT);
    private final JTextArea details = new JTextArea();
    private final JProgressBar progress = new JProgressBar();
    private final FeedModel model = new FeedModel();
    private final JTable table = new JTable(model);
    private final JDesktopPane desktop = new JDesktopPane();
    private VideoBrowserFrame videoBrowser;
    private List<Trending.TrendRow> rows = new ArrayList<>();

    public TrendingFrame() {
        super("GTok | Trending Studio");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 650));
        setSize(1250, 820);
        setLocationRelativeTo(null);
        JPanel root = panel(new BorderLayout(22, 22));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(26, 26, 20, 26));
        desktop.setBackground(BG);
        setContentPane(desktop);
        desktop.add(root, JLayeredPane.DEFAULT_LAYER);
        desktop.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                root.setBounds(0, 0, desktop.getWidth(), desktop.getHeight());
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (videoBrowser != null) videoBrowser.dispose();
            }
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                if (videoBrowser != null) videoBrowser.dispose();
            }
        });

        JPanel header = panel(new BorderLayout());
        header.setBackground(BG);
        JPanel titles = panel(new GridLayout(2, 1, 0, 6));
        titles.setBackground(BG);
        titles.add(label("GTok / Trending Studio", 28, TEXT));
        titles.add(label("Discover the feed. Explore the numbers. Watch the story.", 14, MUTED));
        header.add(titles, BorderLayout.WEST);
        header.add(credits, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel sidebar = panel(new BorderLayout(0, 20));
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(22, 18, 22, 18));
        JPanel controls = panel(new GridLayout(0, 1, 0, 14));
        controls.add(label("PLAYGROUND", 12, MUTED));
        controls.add(label("TikTok trending feed", 17, TEXT));
        controls.add(label("Proxy region", 13, MUTED));
        region.setBackground(PANEL);
        region.setForeground(TEXT);
        controls.add(region);
        controls.add(fetch);
        JTextArea hint = new JTextArea("Region selects the proxy location, not the creator's country.\n\nOne request per click. Results are deduplicated by video ID and sorted by views.");
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setEditable(false);
        hint.setBackground(PANEL);
        hint.setForeground(MUTED);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sidebar.add(controls, BorderLayout.NORTH);
        sidebar.add(hint, BorderLayout.CENTER);
        sidebar.add(count, BorderLayout.SOUTH);
        root.add(sidebar, BorderLayout.WEST);

        table.setRowHeight(38);
        table.setBackground(PANEL);
        table.setForeground(TEXT);
        table.setSelectionBackground(new Color(65, 53, 119));
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(new Color(40, 43, 58));
        table.setShowVerticalLines(false);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setBackground(new Color(36, 39, 53));
        table.getTableHeader().setForeground(TEXT);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        DefaultTableCellRenderer numbers = new DefaultTableCellRenderer() {
            @Override public void setValue(Object value) {
                setHorizontalAlignment(SwingConstants.RIGHT);
                setText(value instanceof Number ? NumberFormat.getIntegerInstance().format(value) : "");
            }
        };
        for (int i = 1; i <= 5; i++) table.getColumnModel().getColumn(i).setCellRenderer(numbers);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel detailPanel = panel(new BorderLayout(10, 12));
        detailPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        detailPanel.add(selected, BorderLayout.NORTH);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        details.setForeground(TEXT);
        details.setBackground(PANEL);
        details.setText("Select a row to see the full caption, publication date and video link.");
        JScrollPane detailScroll = new JScrollPane(details);
        detailScroll.setBorder(BorderFactory.createEmptyBorder());
        detailPanel.add(detailScroll, BorderLayout.CENTER);
        watch.setEnabled(false);
        detailPanel.add(watch, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, detailPanel);
        split.setResizeWeight(0.65);
        split.setDividerLocation(350);
        split.setBorder(BorderFactory.createEmptyBorder());
        root.add(split, BorderLayout.CENTER);
        JPanel footer = panel(new BorderLayout(15, 0));
        footer.setBackground(BG);
        progress.setPreferredSize(new Dimension(140, 6));
        progress.setVisible(false);
        footer.add(status, BorderLayout.CENTER);
        footer.add(progress, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        fetch.addActionListener(e -> loadFeed());
        watch.addActionListener(e -> openVideo());
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showSelection(); });
    }

    private void loadFeed() {
        String key = System.getenv("SCRAPE_CREATORS_API_KEY");
        if (key == null || key.isBlank()) {
            JOptionPane.showMessageDialog(this, "Set SCRAPE_CREATORS_API_KEY in Windows, then restart NetBeans.", "API key missing", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String chosenRegion = (String) region.getSelectedItem();
        fetch.setEnabled(false);
        region.setEnabled(false);
        progress.setVisible(true);
        progress.setIndeterminate(true);
        status.setText("Fetching " + chosenRegion + " feed...");
        new SwingWorker<FeedResult, Void>() {
            @Override protected FeedResult doInBackground() throws Exception {
                String json = Trending.getTrendingFeed(key.trim(), chosenRegion);
                JsonNode root = new ObjectMapper().readTree(json);
                List<Trending.TrendRow> fetched = Trending.convertJsonToRows(json);
                Map<String, Trending.TrendRow> unique = new LinkedHashMap<>();
                for (Trending.TrendRow row : fetched) {
                    if (!row.getVideoId().isBlank()) {
                        unique.merge(row.getVideoId(), row, (a, b) -> a.getViews() >= b.getViews() ? a : b);
                    }
                }
                List<Trending.TrendRow> result = new ArrayList<>(unique.values());
                result.sort(Comparator.comparingLong(Trending.TrendRow::getViews).reversed());
                return new FeedResult(result, root.path("credits_remaining").asText("--"), root.path("credits_charged").asText("--"), fetched.size());
            }
            @Override protected void done() {
                try {
                    FeedResult result = get();
                    table.clearSelection();
                    rows = result.rows;
                    model.fireTableDataChanged();
                    count.setText(rows.size() + " unique videos");
                    credits.setText("Credits remaining: " + result.remaining);
                    status.setText(chosenRegion + " feed loaded | " + result.rawCount + " returned | " + result.charged + " credit(s) charged");
                    showSelection();
                    if (!rows.isEmpty()) table.setRowSelectionInterval(0, 0);
                    else status.setText("No videos returned for " + chosenRegion + ". Credits charged: " + result.charged);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    message = message.replace(key, "[redacted]").replace(key.trim(), "[redacted]");
                    status.setText("Request failed. Previous results retained.");
                    JOptionPane.showMessageDialog(TrendingFrame.this, message, "Unable to load feed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    fetch.setEnabled(true);
                    region.setEnabled(true);
                    progress.setIndeterminate(false);
                    progress.setVisible(false);
                }
            }
        }.execute();
    }

    private Trending.TrendRow selectedRow() {
        int view = table.getSelectedRow();
        return view < 0 ? null : rows.get(table.convertRowIndexToModel(view));
    }

    private void showSelection() {
        Trending.TrendRow row = selectedRow();
        watch.setEnabled(row != null && videoUri(row) != null);
        if (row == null) {
            selected.setText(rows.isEmpty() ? "No video selected" : "Select a video");
            details.setText("Select a row to see its full caption and watch it inside this dashboard.");
            return;
        }
        selected.setText("@" + row.getUsername());
        details.setText("Published UTC: " + (row.getCreateTimeUtc() == null ? "Unknown" : row.getCreateTimeUtc()) + "    |    Region: " + row.getRegion()
                + "\nVideo ID: " + row.getVideoId()
                + "\nEngagement rate: " + row.getEngagementRate() + "% (likes + comments + shares + saves) / views"
                + "\n\n" + row.getDescription() + "\n\n" + row.getVideoUrl());
        details.setCaretPosition(0);
    }

    private static URI videoUri(Trending.TrendRow row) {
        try {
            URI uri = URI.create(row.getVideoUrl());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("tiktok.com") || host.toLowerCase(Locale.ROOT).endsWith(".tiktok.com")) ? uri : null;
        } catch (Exception ex) { return null; }
    }

    private void openVideo() {
        Trending.TrendRow row = selectedRow();
        URI uri = row == null ? null : videoUri(row);
        if (uri == null) return;
        try {
            if (videoBrowser == null || videoBrowser.isClosed()) {
                videoBrowser = new VideoBrowserFrame();
                desktop.add(videoBrowser, JLayeredPane.PALETTE_LAYER);
                int width = Math.min(580, desktop.getWidth() - 30);
                int height = Math.min(720, desktop.getHeight() - 30);
                videoBrowser.setBounds(Math.max(10, desktop.getWidth() - width - 15), 15, width, height);
            }
            videoBrowser.showVideo(row.getVideoId(), uri, row.getUsername());
            videoBrowser.setVisible(true);
            videoBrowser.setIcon(false);
            videoBrowser.moveToFront();
            videoBrowser.setSelected(true);
        } catch (Exception | LinkageError ex) {
            if (videoBrowser != null) videoBrowser.dispose();
            videoBrowser = null;
            JOptionPane.showMessageDialog(this,
                    "The embedded browser could not start. Clean and Build the project and check that Microsoft Edge WebView2 Runtime is installed.",
                    "Video browser", JOptionPane.ERROR_MESSAGE);
        }
    }
    private class FeedModel extends AbstractTableModel {
        private final String[] columns = {"Creator", "Views", "Likes", "Comments", "Shares", "Saves", "Rate %"};
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public Class<?> getColumnClass(int col) { return col == 0 ? String.class : col == 6 ? java.math.BigDecimal.class : Long.class; }
        @Override public Object getValueAt(int index, int col) {
            Trending.TrendRow r = rows.get(index);
            return switch (col) {
                case 0 -> "@" + r.getUsername(); case 1 -> r.getViews(); case 2 -> r.getLikes();
                case 3 -> r.getComments(); case 4 -> r.getShares(); case 5 -> r.getSaves();
                default -> r.getEngagementRate();
            };
        }
    }

    private record FeedResult(List<Trending.TrendRow> rows, String remaining, String charged, int rawCount) {}
    private static JPanel panel(LayoutManager layout) { JPanel p = new JPanel(layout); p.setBackground(PANEL); return p; }
    private static JLabel label(String text, int size, Color color) {
        JLabel l = new JLabel(text); l.setFont(new Font("Segoe UI", Font.PLAIN, size)); l.setForeground(color); return l;
    }
    private static JButton button(String text) {
        JButton b = new JButton(text); b.setBackground(ACCENT); b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13)); b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12)); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TrendingFrame().setVisible(true));
    }
}
