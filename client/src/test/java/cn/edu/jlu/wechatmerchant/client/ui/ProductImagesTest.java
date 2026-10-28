package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.protocol.ProductPage;
import org.junit.jupiter.api.Test;

class ProductImagesTest {
    private static final ProductImages IMAGES = ProductImages.load();

    @Test
    void allPackagedDemoPhotosDecodeIntoCachedThumbnailAndPreview() {
        String[] names = {"演示笔记本", "演示水杯", "演示帆布袋"};
        for (int index = 0; index < names.length; index++) {
            var item = item(1001 + index, names[index]);
            var pictures = IMAGES.forProduct(item);
            assertTrue(pictures.demo(), names[index]);
            assertEquals(88, pictures.thumbnail().getIconWidth());
            assertEquals(88, pictures.thumbnail().getIconHeight());
            assertEquals(176, pictures.preview().getIconWidth());
            assertEquals(176, pictures.preview().getIconHeight());
            assertSame(pictures, IMAGES.forProduct(item));
        }
    }

    @Test
    void unknownOrRenamedProductsUseThePlaceholderInsteadOfAnotherPhoto() {
        assertSame(IMAGES.missing(), IMAGES.forProduct(item(9999, "未知商品")));
        assertSame(IMAGES.missing(), IMAGES.forProduct(item(1001, "演示水杯")));
        assertSame(IMAGES.missing(), IMAGES.forProduct(item(1001, "../../demo-notebook.png")));
        assertFalse(IMAGES.missing().demo());
        assertEquals("暂无图片", IMAGES.missing().preview().getDescription());
    }

    private static ProductPage.Item item(long id, String name) {
        return new ProductPage.Item(id, name, 1290, 20, "");
    }
}
