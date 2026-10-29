package cn.edu.jlu.wechatmerchant.server.ui;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.application.AdminAccessGate;
import cn.edu.jlu.wechatmerchant.server.application.AdminController;
import cn.edu.jlu.wechatmerchant.server.application.AuthenticationService;
import cn.edu.jlu.wechatmerchant.server.application.PromotionManagementService;
import cn.edu.jlu.wechatmerchant.server.application.CouponService;
import cn.edu.jlu.wechatmerchant.server.application.AfterSaleService;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.transport.ProbeServer;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Owns application lifetime; window replacement alone never stops the shop service. */
public final class ServerDesktop implements AutoCloseable {
    private final ProbeServer server;
    private final SessionStore sessions;
    private final String status;
    private final AdminController admin;
    private final AdminAccessGate gate;
    private final Timer expiry;
    private ServerLoginWindow login;
    private ServerWindow management;
    private final PromotionManagementService promotions;
    private PromotionManagementPanel promotionPanel;
    private final CouponService coupons;
    private CouponManagementPanel couponPanel;
    private final AfterSaleService returns;
    private AfterSaleManagementPanel returnPanel;
    private boolean closed;
    private final cn.edu.jlu.wechatmerchant.server.application.InboxService inbox;
    private AnnouncementPanel announcements;

    public ServerDesktop(ProbeServer server, AuthenticationService authentication, SessionStore sessions,
            String status, boolean ready) {
        this(server,authentication,sessions,status,ready,null);
    }

    public ServerDesktop(ProbeServer server, AuthenticationService authentication, SessionStore sessions,
            String status, boolean ready, PromotionManagementService promotions) {
        this(server,authentication,sessions,status,ready,promotions,null);
    }
    public ServerDesktop(ProbeServer server,AuthenticationService authentication,SessionStore sessions,
            String status,boolean ready,PromotionManagementService promotions,CouponService coupons) {
        this(server,authentication,sessions,status,ready,promotions,coupons,null);
    }
    public ServerDesktop(ProbeServer server,AuthenticationService authentication,SessionStore sessions,
            String status,boolean ready,PromotionManagementService promotions,CouponService coupons,AfterSaleService returns) {
        this(server,authentication,sessions,status,ready,promotions,coupons,returns,null);
    }
    public ServerDesktop(ProbeServer server,AuthenticationService authentication,SessionStore sessions,
            String status,boolean ready,PromotionManagementService promotions,CouponService coupons,AfterSaleService returns,cn.edu.jlu.wechatmerchant.server.application.InboxService inbox) {
        requireEdt();
        this.server = server;
        this.sessions = sessions;
        this.status = status;
        this.promotions = promotions;
        this.coupons=coupons;
        this.returns=returns;
        this.inbox=inbox;
        gate = new AdminAccessGate(new AdminAccessGate.Views() {
            @Override public void showLogin(AdminController.State state) { showLoginEntry(state); }
            @Override public void showManagement(UserIdentity identity) { showManagementWindow(identity); }
            @Override public void closeWindows() { disposeManagement(); disposeLogin(); }
        }, ready);
        admin = new AdminController(authentication, sessions, this::onAdminState);
        expiry = new Timer(1000, event -> admin.checkExpiry());
    }

    public void start() {
        requireEdt();
        if (closed) return;
        onAdminState(admin.state());
        expiry.start();
    }

    private void onAdminState(AdminController.State state) {
        if (closed) return;
        try {
            gate.accept(state);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    private void showLoginEntry(AdminController.State state) {
        disposeManagement();
        if (login == null) login = new ServerLoginWindow(admin::login, this::close);
        login.showState(state);
        if (!login.isVisible()) login.setVisible(true);
    }

    private void showManagementWindow(UserIdentity identity) {
        disposeManagement();
        management = new ServerWindow(status, identity);
        if (promotions != null) {
            promotionPanel = new PromotionManagementPanel(promotions,admin::sessionToken,admin::logout);
            management.installPromotions(promotionPanel);
            promotionPanel.start();
        }
        management.bindLogoutAction(admin::logout);
        if(inbox!=null){announcements=new AnnouncementPanel(inbox,admin::sessionToken,admin::logout,promotions);management.installAnnouncements(announcements);announcements.start();}
        if(returns!=null){returnPanel=new AfterSaleManagementPanel(returns,admin::sessionToken,admin::logout);management.installAfterSales(returnPanel);returnPanel.start();}
        if(coupons!=null){
            couponPanel=new CouponManagementPanel(coupons,admin::sessionToken,admin::logout);
            management.installCoupons(couponPanel); couponPanel.start();
        }
        management.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close(); }
        });
        disposeLogin();
        management.setVisible(true);
    }

    private void disposeManagement() {
        if(announcements!=null){announcements.close();announcements=null;}
        if(returnPanel!=null){returnPanel.close();returnPanel=null;}
        if(couponPanel!=null){couponPanel.close();couponPanel=null;}
        if (promotionPanel != null) { promotionPanel.close(); promotionPanel=null; }
        if (management == null) return;
        management.dispose();
        management = null;
    }

    private void disposeLogin() {
        if (login == null) return;
        login.dispose();
        login = null;
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        expiry.stop();
        admin.close();
        try {
            gate.close();
        } finally {
            server.close();
            sessions.clear();
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Server desktop must run on the EDT.");
    }
}
