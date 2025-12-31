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

package main.ui.features.home;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.SwingWorker;
import javax.swing.*;
import main.core.service.SettingsStore;

public class BackgroundPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private Image backgroundImage;
	
	// Cached, already-scaled version of the background for the current panel size
	private BufferedImage cachedScaled;
	private int cachedPanelW = -1;
	private int cachedPanelH = -1;
	private int cachedX = 0;
	private int cachedY = 0;
	private float cachedOpacity = -1f;
	// Optional per-instance override for opacity; if non-null, used instead of SettingsStore
	private Float opacityOverride = null;
	// Debounce timer for resize/opacity changes to recompute cachedScaled off-EDT
	private javax.swing.Timer resizeDebounce;
	private static final int DEBOUNCE_MS = 150;
	private SwingWorker<BufferedImage, Void> currentWorker;
	
	// Reusable popup menu for removing the background
	private final JPopupMenu contextMenu = new JPopupMenu();

	{
		JMenuItem deleteItem = new JMenuItem("Remove Background");
		deleteItem.addActionListener(e -> clearBackground());
		contextMenu.add(deleteItem);
	}
	
	// Constructor: takes the path to the background image file
	public BackgroundPanel(String imagePath) {
		loadAndBlur(imagePath);
		commonInit();
	}

	// New constructor: accepts an already-loaded Image (e.g. bundled resource)
	public BackgroundPanel(java.awt.Image img) {
		if(img != null) {
			loadAndBlur(img);
		}
		commonInit();
	}

	// Shared initialisation for both constructors
	private void commonInit(){
		setLayout(new BorderLayout());

		// Add mouse listener to open the context menu on right-click
		addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				showContextMenuIfTrigger(e);
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				showContextMenuIfTrigger(e);
			}

			private void showContextMenuIfTrigger(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger() && backgroundImage != null) {
					contextMenu.show(BackgroundPanel.this, e.getX(), e.getY());
				}
			}
		});
		// Debounced recompute on resize
		addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override public void componentResized(java.awt.event.ComponentEvent e){
				scheduleRecompute();
			}
		});
	}
	
	private void clearBackground() {
		backgroundImage = null;
		cachedScaled = null;
		cachedOpacity = -1f;
		repaint();
	}
	
	/**
	 * Set a per-instance opacity override (0..1). If null, the global SettingsStore value is used.
	 * Calling this invalidates the cached scaled image so the new opacity is applied immediately.
	 */
	public void setOpacityOverride(Float value){
		this.opacityOverride = value;
		this.cachedScaled = null;
		this.cachedOpacity = -1f;
		scheduleRecompute();
	}
	
	// Overload accepting Image directly
	private void loadAndBlur(Image img){
		if(img==null) return;
		int w = img.getWidth(null);
		int h = img.getHeight(null);
		if(w <= 0 || h <= 0){ backgroundImage=null; return; }
		// Convert to ARGB BufferedImage
		BufferedImage src = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = src.createGraphics();
		g2.drawImage(img,0,0,null); g2.dispose();
		backgroundImage = applyBlur(src);
		cachedScaled=null;
		scheduleRecompute();
	}

	// Existing String-based loader now delegates to the new overload
	// Loads image from file path then blurs
	private void loadAndBlur(String path){
		Image img = new ImageIcon(path).getImage();
		loadAndBlur(img);
	}

	// Shared blur algorithm
	private BufferedImage applyBlur(BufferedImage src){
		// 5×5 uniform kernel (box blur) repeated for stronger effect
		int size = 5;
		float weight = 1f/(size*size);
		float[] kernel = new float[size*size];
		java.util.Arrays.fill(kernel, weight);
		java.awt.image.ConvolveOp op = new java.awt.image.ConvolveOp(new java.awt.image.Kernel(size,size,kernel), java.awt.image.ConvolveOp.EDGE_NO_OP, null);
		BufferedImage tmp = src;
		// Perform several passes for stronger blur
		for(int i=0;i<3;i++){
			BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
			op.filter(tmp,dst);
			tmp = dst;
		}
		return tmp;
	}
	
	// Debounce timer handler
	private void scheduleRecompute() {
		if (resizeDebounce == null) {
			resizeDebounce = new javax.swing.Timer(DEBOUNCE_MS, e -> {
				resizeDebounce = null;
				recomputeScaledImage();
			});
			resizeDebounce.setRepeats(false);
		}
		resizeDebounce.restart();
	}

	@Override
	public void removeNotify() {
		// Ensure background tasks are stopped so the JVM can exit cleanly
		try { if (resizeDebounce != null) { resizeDebounce.stop(); resizeDebounce = null; } } catch (Throwable ignored) {}
		try { if (currentWorker != null) { currentWorker.cancel(true); currentWorker = null; } } catch (Throwable ignored) {}
		// Dispose cached images to free memory
		disposeImages();
		super.removeNotify();
	}
	
	/**
	 * Dispose cached images to free memory.
	 */
	public void disposeImages() {
		if (cachedScaled != null) {
			cachedScaled.flush();
			cachedScaled = null;
		}
		cachedPanelW = cachedPanelH = -1;
		cachedOpacity = -1f;
	}
	
	// Recompute the cached scaled image off-EDT
	private void recomputeScaledImage() {
		if (currentWorker != null) {
			currentWorker.cancel(true);
		}
		currentWorker = new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() {
				if (backgroundImage == null) {
					return null;
				}
				int panelW = getWidth();
				int panelH = getHeight();
				if (panelW <= 0 || panelH <= 0) {
					return null;
				}
				// Get current opacity setting (per-instance override takes precedence)
				float currentOpacity = (opacityOverride != null) ? opacityOverride : SettingsStore.get().getBackgroundOpacity();
				// If opacity changed or we don't have a cached image, update the cache
				if (cachedScaled == null || panelW != cachedPanelW || panelH != cachedPanelH || currentOpacity != cachedOpacity) {
					int imgW = backgroundImage.getWidth(BackgroundPanel.this);
					int imgH = backgroundImage.getHeight(BackgroundPanel.this);
					if (imgW <= 0 || imgH <= 0) return null;
					// Calculate scale factor (cover the entire area)
					double scale = Math.max((double) panelW / imgW, (double) panelH / imgH);
					int drawW = (int) Math.round(imgW * scale);
					int drawH = (int) Math.round(imgH * scale);
					// Create a new image with the current opacity
					BufferedImage tmp = new BufferedImage(drawW, drawH, BufferedImage.TYPE_INT_ARGB);
					Graphics2D cg = tmp.createGraphics();
					// Set the composite with the current opacity
					AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentOpacity);
					cg.setComposite(ac);
					// Draw the image with the applied opacity
					cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					cg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
					cg.drawImage(backgroundImage, 0, 0, drawW, drawH, BackgroundPanel.this);
					cg.dispose();
					return tmp;
				}
				return null;
			}
			@Override
			protected void done() {
				try {
					BufferedImage result = get();
					if (result != null) {
						cachedScaled = result;
						cachedPanelW = getWidth();
						cachedPanelH = getHeight();
						cachedX = (getWidth() - result.getWidth()) / 2;
						cachedY = (getHeight() - result.getHeight()) / 2;
						cachedOpacity = (opacityOverride != null) ? opacityOverride : SettingsStore.get().getBackgroundOpacity();
						repaint();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				currentWorker = null;
			}
		};
		currentWorker.execute();
	}
	
	@Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw the image with uniform scaling (maintain aspect ratio) using cached pre-scaled bitmap
        if (backgroundImage != null) {
            int panelW = getWidth();
            int panelH = getHeight();
            if (panelW <= 0 || panelH <= 0) return;

            float currentOpacity = (opacityOverride != null) ? opacityOverride : SettingsStore.get().getBackgroundOpacity();

            // If cache invalid, schedule a background recompute
            if (cachedScaled == null || panelW != cachedPanelW || panelH != cachedPanelH || currentOpacity != cachedOpacity) {
                scheduleRecompute();
            }

            // Draw cached if available and fully covering. If underfills (edge case), draw a direct scaled fallback.
            if (cachedScaled != null && cachedScaled.getWidth() >= panelW && cachedScaled.getHeight() >= panelH) {
                g.drawImage(cachedScaled, cachedX, cachedY, this);
            } else {
                int imgW = backgroundImage.getWidth(this);
                int imgH = backgroundImage.getHeight(this);
                if (imgW > 0 && imgH > 0) {
                    double scale = Math.max((double) panelW / imgW, (double) panelH / imgH);
                    int drawW = (int) Math.round(imgW * scale);
                    int drawH = (int) Math.round(imgH * scale);
                    int x = (panelW - drawW) / 2;
                    int y = (panelH - drawH) / 2;
                    Graphics2D g2 = (Graphics2D) g.create();
                    // Apply current opacity on-the-fly for the fallback draw
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentOpacity));
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(backgroundImage, x, y, drawW, drawH, this);
                    g2.dispose();
                }
            }
        }
    }
}
