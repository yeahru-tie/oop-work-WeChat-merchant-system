package cn.edu.jlu.wechatmerchant.client.ui;

import cn.edu.jlu.wechatmerchant.protocol.ProductPage;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** Small immutable cache, loaded before the EDT starts. Reads packaged demo resources only. */
public final class ProductImages {
    public record Pictures(ImageIcon thumbnail, ImageIcon preview, boolean demo) {
    }

    private record Key(long productId, String name) {
    }

    private record Entry(long productId, String name, String file) {
        Entry {
            if (productId <= 0 || name == null || name.isBlank() || file == null
                    || !file.matches("[a-z0-9-]+\\.png")) {
                throw new IllegalArgumentException("Invalid demo image mapping.");
            }
        }
    }

    private final Map<Key, Pictures> pictures;
    private final Pictures missing = new Pictures(placeholder(88), placeholder(176), false);

    private ProductImages(Map<Key, Pictures> pictures) {
        this.pictures = Map.copyOf(pictures);
    }

    public static ProductImages load() {
        Map<Key, Pictures> result = new HashMap<>();
        try (var stream = ProductImages.class.getResourceAsStream("/products/demo-images.json")) {
            if (stream == null) throw new IOException("Missing demo image map.");
            Entry[] entries = new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), Entry[].class);
            if (entries == null || entries.length > 20) throw new IOException("Invalid demo image map.");
            for (Entry entry : entries) {
                Key key = new Key(entry.productId(), entry.name());
                if (result.containsKey(key)) throw new IOException("Duplicate demo image mapping.");
                try (var image = ProductImages.class.getResourceAsStream("/products/" + entry.file())) {
                    if (image == null) continue;
                    BufferedImage original = ImageIO.read(image);
                    if (original == null) continue;
                    result.put(key, new Pictures(scaled(original, 88), scaled(original, 176), true));
                } catch (IOException exception) {
                    System.err.println("Demo image unavailable: " + entry.file());
                }
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("Demo image mapping unavailable: " + exception.getClass().getSimpleName());
            result.clear();
        }
        return new ProductImages(result);
    }

    public Pictures forProduct(ProductPage.Item product) {
        return forProduct(product.productId(), product.name());
    }

    public Pictures forProduct(long productId, String name) {
        return pictures.getOrDefault(new Key(productId, name), missing);
    }

    public Pictures missing() {
        return missing;
    }

    private static ImageIcon scaled(BufferedImage original, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var graphics = target.createGraphics();
        try {
            graphics.setColor(new Color(247, 245, 241));
            graphics.fillRect(0, 0, size, size);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale = Math.min((double) size / original.getWidth(), (double) size / original.getHeight());
            int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
            graphics.drawImage(original, (size - width) / 2, (size - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
        }
        return new ImageIcon(target, "演示商品图片");
    }

    private static ImageIcon placeholder(int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var graphics = target.createGraphics();
        try {
            graphics.setColor(new Color(243, 244, 246));
            graphics.fillRect(0, 0, size, size);
            graphics.setColor(new Color(155, 163, 175));
            graphics.setStroke(new BasicStroke(Math.max(1, size / 70f)));
            graphics.drawRoundRect(size / 4, size / 4, size / 2, size / 2, 5, 5);
            graphics.drawOval(size * 11 / 20, size * 7 / 20, size / 12, size / 12);
            graphics.drawPolyline(new int[] {size / 4, size * 2 / 5, size * 11 / 20, size * 3 / 4},
                    new int[] {size * 2 / 3, size * 9 / 20, size * 3 / 5, size * 9 / 20}, 4);
        } finally {
            graphics.dispose();
        }
        return new ImageIcon(target, "暂无图片");
    }
}
