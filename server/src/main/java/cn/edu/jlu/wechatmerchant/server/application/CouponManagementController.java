package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.server.domain.promotion.CouponCampaign;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.SwingUtilities;

public final class CouponManagementController implements AutoCloseable {
    public record Status(boolean busy,String message,boolean reloadRequired) { }
    private final CouponService service;
    private final Supplier<String> token;
    private final Consumer<Status> listener;
    private final Runnable denied;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("coupon-admin-",0).factory());
    private boolean busy,closed;
    public CouponManagementController(CouponService service,Supplier<String> token,Consumer<Status> listener,Runnable denied) {
        this.service=service; this.token=token; this.listener=listener; this.denied=denied;
    }
    public void list(int offset,Consumer<CouponService.AdminPage> success) { run(false,t -> service.adminPage(t,offset),success,"已载入券活动"); }
    public void save(CouponCampaign draft,String key,Consumer<CouponCampaign> success) { run(true,t -> service.saveDraft(t,draft,key),success,"草稿已保存，尚未发布"); }
    public void publish(CouponCampaign draft,Consumer<CouponCampaign> success) { run(true,t -> service.publish(t,draft.campaignId(),draft.version()),success,"已发布；条款已锁定"); }
    public void pause(CouponCampaign item,boolean pause,Consumer<CouponCampaign> success) { run(true,t -> service.pause(t,item.campaignId(),item.version(),pause),success,pause?"已暂停领取，已领券仍可按原条款使用":"已恢复领取（仍须在有效期内且有余量）"); }
    private <T> void run(boolean writing,Task<T> task,Consumer<T> success,String message) {
        requireEdt(); if(closed || busy)return;
        String credential=token.get(); busy=true; listener.accept(new Status(true,"正在处理…",false));
        worker.submit(() -> {
            try {
                T value=task.run(credential);
                SwingUtilities.invokeLater(() -> { if(closed)return; busy=false; listener.accept(new Status(false,message,false)); success.accept(value); });
            } catch(Exception ex) {
                String text=ex instanceof CouponService.Rejected ? switch(ex.getMessage()) {
                    case "CONFIG_CHANGED" -> "活动已变化，请重新载入核对。";
                    case "PUBLISHED_LOCKED" -> "已发布条款不能修改，请新建另一种券。";
                    default -> "券状态不允许此操作，请重新载入。";
                } : ex instanceof IllegalArgumentException ? ex.getMessage() : ex instanceof SecurityException ? "管理员身份已失效，请重新登录。"
                        : writing?"操作结果未确认，请重新载入核对，勿重复操作。":"优惠券服务暂不可用，请重新载入。";
                if(ex instanceof java.sql.SQLException sql)System.err.println("Coupon management failed, database code: "+sql.getErrorCode());
                SwingUtilities.invokeLater(() -> { if(closed)return; busy=false; listener.accept(new Status(false,text,writing)); if(ex instanceof SecurityException)denied.run(); });
            }
        });
    }
    @Override public void close() { requireEdt(); closed=true; worker.shutdownNow(); }
    private static void requireEdt() { if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required."); }
    @FunctionalInterface private interface Task<T> { T run(String token) throws Exception; }
}
