package cn.edu.jlu.wechatmerchant.server.cli;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.application.PromotionPreviewService;
import cn.edu.jlu.wechatmerchant.server.domain.Product;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionCalculator;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionTerms;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PromotionPreviewCliTest {
    @Test void outputExplainsActualSavingsAndSeparatesPreviewFromPublication() {
        var terms = PromotionTerms.parse("10", "9");
        var result = new PromotionCalculator().calculate(10000, 2, terms);
        String output = PromotionPreviewCli.format(new PromotionPreviewService.Preview(
                new Product(1, "试算商品", 10000, 2, ""), terms, result));
        assertTrue(output.contains("原价合计：¥ 200.00"));
        assertTrue(output.contains("本步优惠 ¥ 20.00"));
        assertTrue(output.contains("本步优惠 ¥ 18.00"));
        assertTrue(output.contains("优惠合计：¥ 38.00"));
        assertTrue(output.contains("预估金额：¥ 162.00"));
        assertTrue(output.indexOf("每件直降") < output.indexOf("统一 9 折"));
        assertTrue(output.contains("未保存或发布活动"));
    }

    @Test void outputDoesNotClaimTheConfiguredReductionWhenThePriceFloorClampsIt() {
        var terms = PromotionTerms.parse("20", "10");
        var result = new PromotionCalculator().calculate(1000, 1, terms);
        String output = PromotionPreviewCli.format(new PromotionPreviewService.Preview(
                new Product(1, "试算商品", 1000, 1, ""), terms, result));
        assertTrue(output.contains("每件直降 ¥ 20.00"));
        assertTrue(output.contains("本步优惠 ¥ 10.00"));
        assertTrue(output.contains("预估金额：¥ 0.00"));
    }

    @Test void malformedArgumentsFailBeforeReadingAnyDatabase() {
        var output = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        try (var out = new PrintStream(output, true, StandardCharsets.UTF_8);
                var err = new PrintStream(error, true, StandardCharsets.UTF_8)) {
            assertEquals(2, PromotionPreviewCli.run(new String[]{}, out, err));
            assertEquals(2, PromotionPreviewCli.run(new String[]{"1001", "1", "0", "0"}, out, err));
            assertEquals(2, PromotionPreviewCli.run(new String[]{"0", "1", "0", "9"}, out, err));
            assertEquals(2, PromotionPreviewCli.run(new String[]{"1001", "0", "0", "9"}, out, err));
        }
        assertEquals(0, output.size());
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("预览参数无效"));
    }
}
