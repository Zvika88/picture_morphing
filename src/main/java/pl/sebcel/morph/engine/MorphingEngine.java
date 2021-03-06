package pl.sebcel.morph.engine;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.jdelaunay.delaunay.ConstrainedMesh;
import org.jdelaunay.delaunay.geometries.DPoint;
import org.jdelaunay.delaunay.geometries.DTriangle;

import pl.sebcel.morph.engine.DataCache.CacheKey;
import pl.sebcel.morph.gui.MainFrame;
import pl.sebcel.morph.gui.PicturePane;
import pl.sebcel.morph.model.TransformAnchor;
import pl.sebcel.morph.model.TransformData;
import pl.sebcel.morph.model.TriangleToTriangleTransformer;

public class MorphingEngine {

	public enum Quality {
		LOW(0, 1.0), MEDIUM(1, 0.5), HIGH(2, 0.25);

		private int idx;
		private double delta;

		Quality(int idx, double delta) {
			this.idx = idx;
			this.delta = delta;
		}

		public static Quality fromIdx(int idx) {
			for (Quality value : Quality.values()) {
				if (value.idx == idx) {
					return value;
				}
			}
			throw new IllegalArgumentException("Invalid idx: " + idx);
		}

		public double getDelta() {
			return delta;
		}
	}

	private BufferedImage sourceImage;

	private BufferedImage targetImage;

	private BufferedImage sourceTransformedImage;

	private BufferedImage targetTransformedImage;

	private BufferedImage outputImage;

	private List<DTriangle> sourceTriangles;

	private List<DTriangle> targetTriangles;

	private List<double[]> sourceTrianglesEdges;

	private List<double[]> targetTrianglesEdges;

	private List<double[]> currentTrianglesEdges;

	private DataCache dataCache = new DataCache();

	private double phase;

	private Quality quality = Quality.LOW;

	private MainFrame mainFrame;

	private TransformData project;

	private TransformAnchor selectedAnchor;

	public void setProject(TransformData project) {
		this.project = project;

		if (project != null) {
			loadSourceImage(project.getSourceImagePath());
			loadTargetImage(project.getTargetImagePath());
		} else {
			sourceImage = null;
			targetImage = null;
			sourceTransformedImage = null;
			targetTransformedImage = null;
			outputImage = null;
		}

		dataCache.clearAll();

		setPhase(0.5);
	}

	public void setMainFrame(MainFrame mainFrame) {
		this.mainFrame = mainFrame;
	}

	public void loadSourceImage(String path) {
		sourceImage = loadImage(path);
	}

	public void loadTargetImage(String path) {
		targetImage = loadImage(path);
	}

	public void setSourceImage(File file) {
		project.setSourceImagePath(file.getAbsolutePath());
		loadSourceImage(file.getAbsolutePath());
	}

	public void setTargetImage(File file) {
		project.setTargetImagePath(file.getAbsolutePath());
		loadTargetImage(file.getAbsolutePath());
	}

	private BufferedImage loadImage(String path) {
		System.out.println("Loading image from " + path);
		if (path == null) {
			return null;
		}
		try {
			return ImageIO.read(new File(path));
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load image from file " + path + ": " + ex.getMessage(), ex);
		}
	}

	public BufferedImage getImage(PicturePane.Role role) {
		switch (role) {
		case SOURCE:
			return sourceImage;
		case TARGET:
			return targetImage;
		case SOURCE_TRANSFORMED:
			return sourceTransformedImage;
		case TARGET_TRANSFORMED:
			return targetTransformedImage;
		case OUTPUT:
			return outputImage;
		}
		throw new RuntimeException("Invalid role: " + role);
	}

	public List<double[]> getTriangles(PicturePane.Role role) {
		switch (role) {
		case SOURCE:
			return sourceTrianglesEdges;
		case TARGET:
			return targetTrianglesEdges;
		default:
			return currentTrianglesEdges;
		}
	}

	public void setPhase(double phase) {
		this.phase = phase;
		processImages();
		mainFrame.repaint();
	}

	public double getPhase() {
		return phase;
	}

	public List<TransformAnchor> getAnchors() {
		if (project != null) {
			return project.getAnchors();
		} else {
			return null;
		}
	}

	public void addAnchor(TransformAnchor anchor) {
		project.getAnchors().add(anchor);
		mainFrame.repaint();

		dataCache.clearAll();
	}

	public void anchorMoved() {
		dataCache.clearAll();
	}

	private void processImages() {
		if (project != null) {
			if (dataCache.containsImagesForPhase(phase)) {
				sourceTransformedImage = dataCache.getSourceTransformedImageForPhase(phase);
				targetTransformedImage = dataCache.getTargetTransformedImageForPhase(phase);
				outputImage = dataCache.getOutputTransformedImageForPhase(phase);
				sourceTrianglesEdges = dataCache.getSourceTrianglesForPhase(phase);
				targetTrianglesEdges = dataCache.getTargetTrianglesForPhase(phase);
				currentTrianglesEdges = dataCache.getCurrentTrianglesForPhase(phase);
			} else {
				sourceTriangles = triangulate();
				targetTriangles = calculateTargetTriangles();
				sourceTrianglesEdges = calculateSourceTrianglesEdges();
				targetTrianglesEdges = calculateTargetTrianglesEdges();
				currentTrianglesEdges = calculateCurrentTrianglesEdges();
				sourceTransformedImage = transformSourceImage();
				targetTransformedImage = transformTargetImage();
				outputImage = blendTransformedImages();
				dataCache.putImagesForPhase(phase, sourceTransformedImage, targetTransformedImage, outputImage);
				dataCache.putTrianglesForPhase(phase, sourceTrianglesEdges, targetTrianglesEdges, currentTrianglesEdges);
			}
		}
	}

	private List<DTriangle> calculateTargetTriangles() {
		try {
			List<DTriangle> result = new ArrayList<>();
			for (DTriangle sourceTriangle : sourceTriangles) {
				List<TransformAnchor> anchorsForTriangle = getAnchorsForTriangle(sourceTriangle);
				double x0 = anchorsForTriangle.get(0).getX(1.0);
				double y0 = anchorsForTriangle.get(0).getY(1.0);
				double x1 = anchorsForTriangle.get(1).getX(1.0);
				double y1 = anchorsForTriangle.get(1).getY(1.0);
				double x2 = anchorsForTriangle.get(2).getX(1.0);
				double y2 = anchorsForTriangle.get(2).getY(1.0);
				DTriangle targetTriangle = new DTriangle(new DPoint(x0, y0, 0), new DPoint(x1, y1, 0), new DPoint(x2, y2, 0));
				result.add(targetTriangle);
			}
			return result;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to calculate target triangles from source triangles: " + ex.getMessage(), ex);
		}
	}

	private List<double[]> calculateCurrentTrianglesEdges() {
		List<double[]> edges = new ArrayList<double[]>();
		for (DTriangle triangle : sourceTriangles) {
			List<TransformAnchor> anchorsForTriangle = getAnchorsForTriangle(triangle);
			double x0 = anchorsForTriangle.get(0).getX(phase);
			double y0 = anchorsForTriangle.get(0).getY(phase);
			double x1 = anchorsForTriangle.get(1).getX(phase);
			double y1 = anchorsForTriangle.get(1).getY(phase);
			double x2 = anchorsForTriangle.get(2).getX(phase);
			double y2 = anchorsForTriangle.get(2).getY(phase);

			edges.add(new double[] { x0, y0, x1, y1 });
			edges.add(new double[] { x1, y1, x2, y2 });
			edges.add(new double[] { x2, y2, x0, y0 });
		}
		return edges;
	}

	private BufferedImage transformSourceImage() {
		if (sourceImage == null) {
			return null;
		}
		int width = sourceImage.getWidth();
		int height = sourceImage.getHeight();
		int type = sourceImage.getType();

		BufferedImage result = new BufferedImage(width, height, type);

		double delta = quality.getDelta();

		for (double x = 0; x < width; x += delta) {
			for (double y = 0; y < height; y += delta) {
				double newX = x;
				double newY = y;
				DTriangle triangle = getSourceTriangleForPoint(x, y);

				if (triangle != null) {
					List<TransformAnchor> anchorsForTriangle = getAnchorsForTriangle(triangle);
					TriangleToTriangleTransformer transformer = getTransformer1ForTriangle(triangle, anchorsForTriangle, phase);
					newX = (int) transformer.transformX(x, y);
					newY = (int) transformer.transformY(x, y);
				}

				if (newX >= width) {
					newX = width - 1;
				}
				if (newX < 0) {
					newX = 0;
				}
				if (newY >= height) {
					newY = height - 1;
				}
				if (newY < 0) {
					newY = 0;
				}

				int rgb = sourceImage.getRGB((int) x, (int) y);
				result.setRGB((int) newX, (int) newY, rgb);
			}
		}

		return result;
	}

	private BufferedImage transformTargetImage() {
		if (targetImage == null) {
			return null;
		}
		int width = sourceImage.getWidth();
		int height = sourceImage.getHeight();
		int type = sourceImage.getType();

		BufferedImage result = new BufferedImage(width, height, type);

		double delta = quality.getDelta();

		if (selectedAnchor != null && 1 == 2) {
			double sx = selectedAnchor.getX(0);
			double sy = selectedAnchor.getY(0);

			double cx = selectedAnchor.getX(phase);
			double cy = selectedAnchor.getY(phase);

			double dx = selectedAnchor.getX(1);
			double dy = selectedAnchor.getY(1);

			System.out.print(phase + "," + sx + "," + sy + "," + cx + "," + cy + "," + dx + "," + dy);

			try {
				DPoint point = new DPoint(sx, sy, 0);
				for (DTriangle triangle : targetTriangles) {
					if (triangle.isInside(point)) {
						List<TransformAnchor> anchorsForTriangle = getAnchorsForTriangle(triangle);
						TriangleToTriangleTransformer transformer = getTransformer2ForTriangle(triangle, anchorsForTriangle, phase);
						int newsx = (int) transformer.transformX(sx, sy);
						int newsy = (int) transformer.transformY(sx, sy);
						System.out.print("," + newsx + "," + newsy);
					}
				}
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			System.out.println();
		}

		for (double x = 0; x < width; x += delta) {
			for (double y = 0; y < height; y += delta) {
				double newX = x;
				double newY = y;
				DTriangle triangle = getTargetTriangleForPoint(x, y);

				if (triangle != null) {
					List<TransformAnchor> anchorsForTriangle = getAnchorsForTargetTriangle(triangle);
					if (anchorsForTriangle.size() != 3) {
						System.out.println("Warining: Found only " + anchorsForTriangle.size() + " anchors for a triangle.");
					}
					TriangleToTriangleTransformer transformer = getTransformer2ForTriangle(triangle, anchorsForTriangle, phase);
					newX = (int) transformer.transformX(x, y);
					newY = (int) transformer.transformY(x, y);
				}

				if (newX >= width) {
					newX = width - 1;
				}
				if (newX < 0) {
					newX = 0;
				}
				if (newY >= height) {
					newY = height - 1;
				}
				if (newY < 0) {
					newY = 0;
				}

				try {
					// int rgb = targetImage.getRGB((int) newX, (int) newY);
					// result.setRGB((int) x, (int) y, rgb);
					int rgb = targetImage.getRGB((int) x, (int) y);
					result.setRGB((int) newX, (int) newY, rgb);
				} catch (Exception ex) {
					// System.out.println(x+","+y+" -> "+newX+","+newY+" width: "+width+", height: "+height);
				}
			}
		}

		return result;
	}

	private BufferedImage blendTransformedImages() {
		if (sourceImage == null || targetImage == null) {
			return null;
		}
		int width = sourceImage.getWidth();
		int height = sourceImage.getHeight();
		int type = sourceImage.getType();

		BufferedImage result = new BufferedImage(width, height, type);

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {

				int sourceRGB = sourceTransformedImage.getRGB(x, y);
				int targetRGB = targetTransformedImage.getRGB(x, y);

				Color sourceColor = new Color(sourceRGB);
				Color targetColor = new Color(targetRGB);

				int diffBlue = targetColor.getBlue() - sourceColor.getBlue();
				int diffGreen = targetColor.getGreen() - sourceColor.getGreen();
				int diffRed = targetColor.getRed() - sourceColor.getRed();
				int diffAlpha = targetColor.getAlpha() - sourceColor.getAlpha();

				int mixBlue = sourceColor.getBlue() + (int) (diffBlue * phase);
				int mixGreen = sourceColor.getGreen() + (int) (diffGreen * phase);
				int mixRed = sourceColor.getRed() + (int) (diffRed * phase);
				int mixAlpha = sourceColor.getAlpha() + (int) (diffAlpha * phase);

				Color mixColor = new Color(mixRed, mixGreen, mixBlue, mixAlpha);
				int mixRGB = mixColor.getRGB();

				result.setRGB(x, y, mixRGB);
			}
		}

		return result;
	}

	private List<DTriangle> triangulate() {
		if (project.getAnchors().size() < 3) {
			return new ArrayList<DTriangle>();
		}
		try {
			ConstrainedMesh mesh = new ConstrainedMesh();
			for (TransformAnchor anchor : project.getAnchors()) {
				double x = anchor.getX(0);
				double y = anchor.getY(0);
				DPoint point = new DPoint(x, y, 0);
				mesh.addPoint(point);
			}
			mesh.processDelaunay();
			return mesh.getTriangleList();
		} catch (Exception ex) {
			ex.printStackTrace();
			return new ArrayList<DTriangle>();
		}
	}

	private List<double[]> calculateSourceTrianglesEdges() {
		List<double[]> edges = new ArrayList<double[]>();
		for (DTriangle triangle : sourceTriangles) {
			List<TransformAnchor> anchorsForTriangle = getAnchorsForTriangle(triangle);
			double x0 = anchorsForTriangle.get(0).getX(0d);
			double y0 = anchorsForTriangle.get(0).getY(0d);
			double x1 = anchorsForTriangle.get(1).getX(0d);
			double y1 = anchorsForTriangle.get(1).getY(0d);
			double x2 = anchorsForTriangle.get(2).getX(0d);
			double y2 = anchorsForTriangle.get(2).getY(0d);
			edges.add(new double[] { x0, y0, x1, y1 });
			edges.add(new double[] { x1, y1, x2, y2 });
			edges.add(new double[] { x2, y2, x0, y0 });
		}
		return edges;
	}

	private List<double[]> calculateTargetTrianglesEdges() {
		List<double[]> edges = new ArrayList<double[]>();
		for (DTriangle triangle : sourceTriangles) {
			List<TransformAnchor> anchorsForTriangle = getAnchorsForTriangle(triangle);
			double x0 = anchorsForTriangle.get(0).getX(1d);
			double y0 = anchorsForTriangle.get(0).getY(1d);
			double x1 = anchorsForTriangle.get(1).getX(1d);
			double y1 = anchorsForTriangle.get(1).getY(1d);
			double x2 = anchorsForTriangle.get(2).getX(1d);
			double y2 = anchorsForTriangle.get(2).getY(1d);
			edges.add(new double[] { x0, y0, x1, y1 });
			edges.add(new double[] { x1, y1, x2, y2 });
			edges.add(new double[] { x2, y2, x0, y0 });
		}
		return edges;
	}

	private TriangleToTriangleTransformer getTransformer1ForTriangle(DTriangle triangle, List<TransformAnchor> anchorsForTriangle, double phase) {
		try {
			CacheKey key = new CacheKey(triangle, phase);
			if (!dataCache.containsSourceImageTransformers(key)) {
				DPoint t1p1 = new DPoint(anchorsForTriangle.get(0).getX(0.0), anchorsForTriangle.get(0).getY(0.0), 0);
				DPoint t1p2 = new DPoint(anchorsForTriangle.get(1).getX(0.0), anchorsForTriangle.get(1).getY(0.0), 0);
				DPoint t1p3 = new DPoint(anchorsForTriangle.get(2).getX(0.0), anchorsForTriangle.get(2).getY(0.0), 0);

				DPoint t2p1 = new DPoint(anchorsForTriangle.get(0).getX(phase), anchorsForTriangle.get(0).getY(phase), 0);
				DPoint t2p2 = new DPoint(anchorsForTriangle.get(1).getX(phase), anchorsForTriangle.get(1).getY(phase), 0);
				DPoint t2p3 = new DPoint(anchorsForTriangle.get(2).getX(phase), anchorsForTriangle.get(2).getY(phase), 0);

				DTriangle t1 = new DTriangle(t1p1, t1p2, t1p3);
				DTriangle t2 = new DTriangle(t2p1, t2p2, t2p3);

				TriangleToTriangleTransformer transformer = new TriangleToTriangleTransformer(t1, t2);
				dataCache.putsSourceImageTransformers(key, transformer);
			}

			return dataCache.getSourceImageTransformers(key);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private TriangleToTriangleTransformer getTransformer2ForTriangle(DTriangle triangle, List<TransformAnchor> anchorsForTriangle, double phase) {
		try {
			CacheKey key = new CacheKey(triangle, phase);
			if (!dataCache.containsTargetImageTransformers(key)) {
				DPoint t1p1 = new DPoint(anchorsForTriangle.get(0).getX(1.0), anchorsForTriangle.get(0).getY(1.0), 0);
				DPoint t1p2 = new DPoint(anchorsForTriangle.get(1).getX(1.0), anchorsForTriangle.get(1).getY(1.0), 0);
				DPoint t1p3 = new DPoint(anchorsForTriangle.get(2).getX(1.0), anchorsForTriangle.get(2).getY(1.0), 0);

				DPoint t2p1 = new DPoint(anchorsForTriangle.get(0).getX(phase), anchorsForTriangle.get(0).getY(phase), 0);
				DPoint t2p2 = new DPoint(anchorsForTriangle.get(1).getX(phase), anchorsForTriangle.get(1).getY(phase), 0);
				DPoint t2p3 = new DPoint(anchorsForTriangle.get(2).getX(phase), anchorsForTriangle.get(2).getY(phase), 0);

				DTriangle t1 = new DTriangle(t1p1, t1p2, t1p3);
				DTriangle t2 = new DTriangle(t2p1, t2p2, t2p3);

				TriangleToTriangleTransformer transformer = new TriangleToTriangleTransformer(t1, t2);
				dataCache.putsTargetImageTransformers(key, transformer);
			}

			return dataCache.getTargetImageTransformers(key);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get target transformer for triangle: " + ex.getMessage(), ex);
		}
	}

	private DTriangle getSourceTriangleForPoint(double x, double y) {
		if (sourceTriangles == null) {
			return null;
		}
		try {
			DPoint point = new DPoint(x, y, 0);

			for (DTriangle triangle : sourceTriangles) {
				if (triangle.isInside(point)) {
					return triangle;
				}
			}
			return null;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to find source triangle for a point " + x + "," + y + ": " + ex.getMessage());
		}
	}

	private DTriangle getTargetTriangleForPoint(double x, double y) {
		if (targetTriangles == null) {
			return null;
		}
		try {
			DPoint point = new DPoint(x, y, 0);

			for (DTriangle triangle : targetTriangles) {
				if (triangle.isInside(point)) {
					return triangle;
				}
			}
			return null;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to find target triangle for a point " + x + "," + y + ": " + ex.getMessage());
		}
	}

	private List<TransformAnchor> getAnchorsForTriangle(DTriangle triangle) {
		if (!dataCache.containsAnchorsForTriangle(triangle)) {
			List<TransformAnchor> anchorsForTriangle = new ArrayList<TransformAnchor>();
			if (triangle != null) {
				for (DPoint point : triangle.getPoints()) {
					double px = point.getX();
					double py = point.getY();

					for (TransformAnchor anchor : project.getAnchors()) {
						if (Math.abs(anchor.getX(0.0) - px) < 0.01 && Math.abs(anchor.getY(0.0) - py) < 0.01) {
							anchorsForTriangle.add(anchor);
						}
					}
				}
			}
			dataCache.putAnchorsForTriangle(triangle, anchorsForTriangle);
		}

		return dataCache.getAnchorsForTriangle(triangle);
	}

	private List<TransformAnchor> getAnchorsForTargetTriangle(DTriangle triangle) {
		if (!dataCache.containsAnchorsForTriangle(triangle)) {
			List<TransformAnchor> anchorsForTriangle = new ArrayList<TransformAnchor>();
			if (triangle != null) {
				for (DPoint point : triangle.getPoints()) {
					double px = point.getX();
					double py = point.getY();

					for (TransformAnchor anchor : project.getAnchors()) {
						if (Math.abs(anchor.getX(1.0) - px) < 0.01 && Math.abs(anchor.getY(1.0) - py) < 0.01) {
							anchorsForTriangle.add(anchor);
						}
					}
				}
			}
			dataCache.putAnchorsForTriangle(triangle, anchorsForTriangle);
		}

		return dataCache.getAnchorsForTriangle(triangle);
	}

	public TransformData getProject() {
		return project;
	}

	public void setQuality(int sliderValue) {
		quality = Quality.fromIdx(sliderValue);
		dataCache.clearAll();
	}

	public void setSelectedAnchor(TransformAnchor anchor) {
		this.selectedAnchor = anchor;
	}

	public TransformAnchor getSelectedAnchor() {
		return selectedAnchor;
	}
}