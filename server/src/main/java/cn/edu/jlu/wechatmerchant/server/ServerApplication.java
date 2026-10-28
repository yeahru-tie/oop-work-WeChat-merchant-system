package cn.edu.jlu.wechatmerchant.server;

import cn.edu.jlu.wechatmerchant.server.ui.ServerDesktop;
import cn.edu.jlu.wechatmerchant.server.transport.ProbeServer;
import cn.edu.jlu.wechatmerchant.server.application.ProductCatalog;
import cn.edu.jlu.wechatmerchant.server.application.ProductQueryHandler;
import cn.edu.jlu.wechatmerchant.server.application.AuthenticationService;
import cn.edu.jlu.wechatmerchant.server.application.IdentityHandler;
import cn.edu.jlu.wechatmerchant.server.application.CartHandler;
import cn.edu.jlu.wechatmerchant.server.application.CartService;
import cn.edu.jlu.wechatmerchant.server.application.PromotionManagementService;
import cn.edu.jlu.wechatmerchant.server.application.CouponService;
import cn.edu.jlu.wechatmerchant.server.application.CouponHandler;
import cn.edu.jlu.wechatmerchant.server.application.OrderService;
import cn.edu.jlu.wechatmerchant.server.application.OrderHandler;
import cn.edu.jlu.wechatmerchant.server.persistence.UserDao;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import cn.edu.jlu.wechatmerchant.protocol.NetworkDefaults;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.FontUIResource;

public final class ServerApplication {
    private ServerApplication() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("服务端管理窗口需要图形桌面环境。");
            System.exit(1);
            return;
        }
        final int port;
        final Path databaseFile;
        try {
            port = NetworkDefaults.configuredPort();
            databaseFile = SqliteDatabase.configuredPath();
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
            return;
        }
        SqliteDatabase database = new SqliteDatabase(databaseFile);
        SessionStore sessions = new SessionStore();
        AuthenticationService authentication = new AuthenticationService(new UserDao(database));
        IdentityHandler identities = new IdentityHandler(authentication, sessions);
        CouponService coupons=new CouponService(database,sessions);
        ProbeServer server = new ProbeServer(port, new ProductQueryHandler(new ProductCatalog(new ProductDao(database))),
                identities, new CartHandler(new CartService(database), identities),new CouponHandler(coupons,identities),new OrderHandler(new OrderService(database,sessions,coupons),identities));
        String initialStatus;
        boolean started = false;
        try {
            database.initialize();
            server.start();
            started = true;
            initialStatus = "商品数据库已就绪；通信已启动：" + NetworkDefaults.ADDRESS + ":" + server.port();
        } catch (IOException | SQLException exception) {
            server.close();
            initialStatus = "服务启动失败，请检查数据库或端口。";
            System.err.println("Server start failed: " + exception.getClass().getSimpleName());
        }
        final String status = initialStatus;
        final boolean ready = started;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException exception) {
                System.err.println("系统外观不可用，使用默认外观：" + exception.getMessage());
            }
            FontUIResource font = new FontUIResource("Microsoft YaHei UI", Font.PLAIN, 14);
            UIManager.put("Label.font", font);
            UIManager.put("TabbedPane.font", font);
            UIManager.put("Button.font", font);
            UIManager.put("TextField.font", font);
            UIManager.put("PasswordField.font", font);
            ServerDesktop desktop = null;
            try {
                desktop = new ServerDesktop(server, authentication, sessions, status, ready,
                        new PromotionManagementService(database,sessions),coupons);
                desktop.start();
            } catch (RuntimeException exception) {
                if (desktop != null) desktop.close();
                else { server.close(); sessions.clear(); }
                throw exception;
            }
        });
    }
}
