package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionConfiguration;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionTerms;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** One worker per authenticated management view; late results after logout are discarded. */
public final class PromotionManagementController implements AutoCloseable {
    public record Status(boolean busy, String message, boolean reloadRequired) { }
    private final PromotionManagementService service;
    private final Supplier<String> token;
    private final Consumer<Status> listener;
    private final Runnable unauthorized;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("promotion-admin-",0).factory());
    private boolean closed;
    private boolean busy;

    public PromotionManagementController(PromotionManagementService service, Supplier<String> token,
            Consumer<Status> listener, Runnable unauthorized) {
        this.service=service; this.token=token; this.listener=listener; this.unauthorized=unauthorized;
    }

    public void list(int offset, Consumer<PromotionManagementService.Page> success) {
        run(false, credential -> service.list(credential,offset), success, "商品已载入");
    }
    public void load(long product, Consumer<PromotionManagementService.Editor> success) {
        run(false, credential -> service.load(credential,product),success,"已载入当前配置；编辑后须保存才发布");
    }
    public void preview(long product, int quantity, PromotionTerms terms, Consumer<PromotionPreviewService.Preview> success) {
        run(false, credential -> service.preview(credential,product,quantity,terms),success,"试算完成，未保存或发布");
    }
    public void save(PromotionConfiguration draft, Consumer<PromotionManagementService.Editor> success) {
        run(true, credential -> service.save(credential,draft),success,"配置已保存；买家下次刷新取得新优惠");
    }

    private <T> void run(boolean writing, Task<T> task, Consumer<T> success, String message) {
        requireEdt();
        if (closed || busy) return;
        String credential=token.get();
        busy=true;
        listener.accept(new Status(true,"正在处理…",false));
        worker.submit(() -> {
            try {
                T result=task.execute(credential);
                SwingUtilities.invokeLater(() -> {
                    if (closed) return;
                    busy=false;
                    listener.accept(new Status(false,message,false));
                    success.accept(result);
                });
            } catch (Exception exception) {
                String detail = exception instanceof SecurityException || exception instanceof IllegalArgumentException
                        || exception instanceof PromotionManagementService.Conflict ? exception.getMessage()
                        : writing ? "保存结果未确认，请重新载入核对；不要重复保存。" : "促销服务暂不可用，请稍后重新载入。";
                if (exception instanceof SQLException sql) System.err.println("Promotion operation failed, database code: "+sql.getErrorCode());
                SwingUtilities.invokeLater(() -> {
                    if (closed) return;
                    busy=false;
                    listener.accept(new Status(false,detail,writing));
                    if (exception instanceof SecurityException) unauthorized.run();
                });
            }
        });
    }
    @FunctionalInterface private interface Task<T> { T execute(String token) throws Exception; }
    @Override public void close() { requireEdt(); closed=true; worker.shutdownNow(); }
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Management actions require EDT.");
    }
}
