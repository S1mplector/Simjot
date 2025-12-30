/*
 * SIMJOT - PROPRIETARY
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 * 
 * This source code is licensed under the Simjot Source-Available Personal Use License.
 * You may view and study this code for personal, non-commercial use.
 * Distribution, commercial use, and derivative works are strictly prohibited.
 * 
 * See LICENSE.md for full terms.
 */

package main.ui.sim.overlay;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Scrollable chat view that renders a ChatTranscriptModel as styled bubbles with timestamps.
 */
public final class ChatViewPanel extends JPanel implements ChatTranscriptModel.Listener {
    private final ChatTranscriptModel model;
    private final JScrollPane scroll;
    private final BubbleCanvas canvas;
    private boolean autoScroll = true;

    public ChatViewPanel(ChatTranscriptModel model) {
        super(new BorderLayout());
        this.model = model;
        this.model.addListener(this);

        canvas = new BubbleCanvas();
        canvas.setOpaque(false);

        scroll = new JScrollPane(canvas,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        // Visually hide scrollbars but keep scrolling via mouse wheel/trackpad
        InvisibleScrollBarUI invisibleUI = new InvisibleScrollBarUI();
        scroll.getVerticalScrollBar().setUI(invisibleUI);
        scroll.getHorizontalScrollBar().setUI(invisibleUI);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getHorizontalScrollBar().setOpaque(false);

        add(scroll, BorderLayout.CENTER);

        // Track user scroll to disable auto-scroll when scrolled up
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            int extent = e.getAdjustable().getVisibleAmount();
            int max = e.getAdjustable().getMaximum();
            int value = e.getValue();
            autoScroll = (value + extent + 8) >= max; // near bottom
        });
    }

    public JComponent getScrollPane() { return scroll; }

    @Override
    public void onTranscriptChanged() {
        SwingUtilities.invokeLater(() -> {
            if (!isDisplayable() || scroll == null || !scroll.isDisplayable()) return;
            var bar = scroll.getVerticalScrollBar();
            if (bar == null || !bar.isDisplayable()) return;
            int prevMax = bar.getMaximum();
            int prevVal = bar.getValue();
            boolean wasAtBottom = autoScroll || (prevMax - (prevVal + bar.getVisibleAmount()) < 8);

            canvas.relayoutForWidth(getAvailableWidth());
            canvas.revalidate();
            canvas.repaint();

            if (wasAtBottom) {
                SwingUtilities.invokeLater(() -> {
                    if (bar.isDisplayable()) {
                        bar.setValue(bar.getMaximum());
                    }
                });
            }
        });
    }

    private int getAvailableWidth() {
        int vw = scroll.getViewport().getExtentSize().width;
        if (vw <= 0) vw = getWidth();
        if (vw <= 0) vw = 300;
        return vw;
    }

    /**
     * Canvas that draws message bubbles with timestamps and manages its preferred size.
     */
    private final class BubbleCanvas extends JComponent {
        private static final int PAD = 10;
        private static final int GAP = 8;
        private static final int BUBBLE_ARC = 12;
        private static final int TEXT_SIZE = 13;
        private static final int TS_SIZE = 10;
        private static final int MAX_WIDTH = 460; // hard cap for really wide containers

        private int contentWidth = 320;
        private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm")
                .withZone(ZoneId.systemDefault());

        void relayoutForWidth(int viewportWidth) {
            int avail = Math.max(200, Math.min(MAX_WIDTH, viewportWidth));
            this.contentWidth = avail - PAD * 2; // space inside scroll
            // recompute height
            int totalH = PAD;
            Graphics2D g2 = (Graphics2D) getGraphics();
            if (g2 == null) {
                // graphics not ready yet; set a reasonable guess
                setPreferredSize(new Dimension(avail, 200));
                return;
            }
            g2.setFont(new Font("SansSerif", Font.PLAIN, TEXT_SIZE));
            FontMetrics fm = g2.getFontMetrics();
            int lineH = fm.getHeight();
            int maxBubbleW = Math.min((int)(contentWidth * 0.82), contentWidth);

            List<ChatTranscriptModel.Entry> items = model.snapshot();
            for (ChatTranscriptModel.Entry e : items) {
                java.util.List<String> rows = wrapText(e.text, maxBubbleW - 16, fm);
                if (rows.isEmpty()) {
                    // Skip blank entries entirely (no bubble, no timestamp)
                    continue;
                }
                int textH = rows.size() * lineH;
                totalH += textH + 16; // vertical padding inside bubble
                totalH += 14; // timestamp line area
                totalH += GAP;
            }
            totalH += PAD;
            setPreferredSize(new Dimension(avail, Math.max(120, totalH)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int xPad = PAD;
            int y = PAD;

            g2.setFont(new Font("SansSerif", Font.PLAIN, TEXT_SIZE));
            FontMetrics fm = g2.getFontMetrics();
            int lineH = fm.getHeight();

            int maxBubbleW = Math.min((int)(contentWidth * 0.82), contentWidth);

            List<ChatTranscriptModel.Entry> items = model.snapshot();
            for (ChatTranscriptModel.Entry e : items) {
                java.util.List<String> rows = wrapText(e.text, maxBubbleW - 16, fm);
                if (rows.isEmpty()) {
                    // Do not render empty/blank bubbles
                    continue;
                }
                boolean isUser = (e.role == ChatTranscriptModel.Role.USER);
                Color bubble = isUser ? new Color(230,243,255) : new Color(245,245,245);
                Color textCol = isUser ? new Color(25,25,25) : new Color(20,60,140);
                int bx; // bubble x

                int textW = rows.stream().mapToInt(fm::stringWidth).max().orElse(0);
                int bubbleW = Math.min(maxBubbleW, Math.max(80, textW + 16));
                int textX = isUser ? (xPad + contentWidth - bubbleW + 8) : (xPad + 8);
                bx = isUser ? (xPad + contentWidth - bubbleW) : xPad;
                int textY = y + 10 + fm.getAscent();
                int bubbleH = rows.size() * lineH + 12 + 12; // text pad + timestamp area

                // Bubble shape
                Shape rr = new RoundRectangle2D.Float(bx, y, bubbleW, bubbleH - 12, BUBBLE_ARC, BUBBLE_ARC);
                g2.setColor(bubble);
                g2.fill(rr);

                // Text
                g2.setColor(textCol);
                for (String r : rows) {
                    g2.drawString(r, textX, textY);
                    textY += lineH;
                }

                // Timestamp under bubble, small and light
                g2.setFont(new Font("SansSerif", Font.PLAIN, TS_SIZE));
                FontMetrics tfm = g2.getFontMetrics();
                String ts = timeFmt.format(java.time.Instant.ofEpochMilli(e.ts));
                int tsW = tfm.stringWidth(ts);
                int tsX = isUser ? (bx + bubbleW - tsW - 4) : (bx + 4);
                int tsY = y + bubbleH - 2;
                g2.setColor(new Color(120,120,120));
                g2.drawString(ts, tsX, tsY);

                // advance y
                y += bubbleH + GAP;
                // restore main font
                g2.setFont(new Font("SansSerif", Font.PLAIN, TEXT_SIZE));
            }

            g2.dispose();
        }

        private java.util.List<String> wrapText(String text, int maxWidth, FontMetrics fm) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (text == null) return lines;
            String trimmed = text.trim();
            if (trimmed.isEmpty()) return lines; // treat blank as no content
            String[] words = text.split("\\s+");
            StringBuilder cur = new StringBuilder();
            for (String w : words) {
                if (cur.length() == 0) {
                    cur.append(w);
                } else {
                    String cand = cur + " " + w;
                    if (fm.stringWidth(cand) <= maxWidth) {
                        cur.append(' ').append(w);
                    } else {
                        lines.add(cur.toString());
                        cur = new StringBuilder(w);
                    }
                }
            }
            if (cur.length() > 0) lines.add(cur.toString());
            return lines;
        }
    }

    /** A ScrollBarUI that paints nothing to visually hide the scrollbar. */
    private static final class InvisibleScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void layoutHScrollbar(JScrollBar sb) {
            if (decrButton == null || incrButton == null) return; // UI may be uninstalling during shutdown
            super.layoutHScrollbar(sb);
        }

        @Override
        protected void layoutVScrollbar(JScrollBar sb) {
            if (decrButton == null || incrButton == null) return; // UI may be uninstalling during shutdown
            super.layoutVScrollbar(sb);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            // no-op
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // no-op
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setOpaque(false);
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setContentAreaFilled(false);
            return b;
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setOpaque(false);
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setContentAreaFilled(false);
            return b;
        }
    }

    @Override
    public void removeNotify() {
        try { model.removeListener(this); } catch (Throwable ignored) {}
        super.removeNotify();
    }
}
