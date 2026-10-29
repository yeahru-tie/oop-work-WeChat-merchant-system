package cn.edu.jlu.wechatmerchant.server.ui;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.JButton;
import java.awt.FlowLayout;

public final class ServerWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    private final cn.edu.jlu.wechatmerchant.desktop.NavigationPane navigation = new cn.edu.jlu.wechatmerchant.desktop.NavigationPane("店铺管理");
    private final JButton logout = new JButton("退出管理身份");

    public ServerWindow(String status, UserIdentity administrator) {
        super(authorizedTitle(administrator));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(640, 420));

        var runtime=new cn.edu.jlu.wechatmerchant.desktop.SurfacePanel(new BorderLayout(0,24));
        runtime.add(cn.edu.jlu.wechatmerchant.desktop.Ui.title("服务已就绪"),BorderLayout.NORTH);
        var ready=new cn.edu.jlu.wechatmerchant.desktop.StatusPill("运行中",cn.edu.jlu.wechatmerchant.desktop.StatusPill.Tone.SUCCESS);
        runtime.add(cn.edu.jlu.wechatmerchant.desktop.Ui.row(ready),BorderLayout.CENTER);
        var details=cn.edu.jlu.wechatmerchant.desktop.Ui.text(status,2);runtime.add(details,BorderLayout.SOUTH);
        var runtimePage=new JPanel(new BorderLayout());runtimePage.add(runtime,BorderLayout.NORTH);
        navigation.addTab("运行状态",runtimePage);
        navigation.addTab("交易与售后",unavailablePage("暂无交易"));
        navigation.addTab("优惠公告",unavailablePage("暂无公告"));
        navigation.addTab("显示设置",new cn.edu.jlu.wechatmerchant.desktop.ThemeSettingsPanel());
        navigation.accountArea().add(cn.edu.jlu.wechatmerchant.desktop.Ui.label(administrator.displayName()));
        logout.setText("退出登录");logout.putClientProperty("theme.ghost",true);navigation.accountArea().add(logout);
        setContentPane(navigation);
        var screen=java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1020,screen.width-32),Math.min(700,screen.height-32)));
        setSize(Math.min(1320,screen.width-40),Math.min(880,screen.height-40));
        setLocationRelativeTo(null);
    }

    public void bindLogoutAction(Runnable action) {
        logout.addActionListener(event -> action.run());
    }

    public void installPromotions(PromotionManagementPanel panel) {
        navigation.insertTab("促销管理",null,panel,"维护商品直降与折扣",1);

    }

    public void installCoupons(CouponManagementPanel panel) {
        navigation.insertTab("优惠券管理",null,panel,"草稿、发布及暂停领取",Math.min(2,navigation.getTabCount()));
    }
    public void installAfterSales(AfterSaleManagementPanel panel){for(int i=0;i<navigation.getTabCount();i++)if(navigation.getTitleAt(i).equals("交易与售后")){navigation.setComponentAt(i,panel);return;}throw new IllegalStateException("Missing management tab.");}
    public void installAnnouncements(AnnouncementPanel panel){for(int i=0;i<navigation.getTabCount();i++)if(navigation.getTitleAt(i).equals("优惠公告")){navigation.setComponentAt(i,panel);return;}throw new IllegalStateException("Missing announcement tab.");}

    private static String authorizedTitle(UserIdentity administrator) {
        if (administrator == null || administrator.role() != UserIdentity.Role.ADMIN) {
            throw new SecurityException("Administrator identity is required to create the management window.");
        }
        return "微商系统 · 服务端";
    }

    private static JPanel unavailablePage(String message) {
        JPanel page = new JPanel(new BorderLayout());
        page.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        page.add(new JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER);
        return page;
    }
}
