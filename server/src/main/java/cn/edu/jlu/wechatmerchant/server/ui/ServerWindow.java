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
    private final JTabbedPane navigation = new JTabbedPane();
    private final JButton logout = new JButton("退出管理身份");

    public ServerWindow(String status, UserIdentity administrator) {
        super(authorizedTitle(administrator));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(640, 420));

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        JLabel heading = new JLabel("店铺管理");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        content.add(heading, BorderLayout.NORTH);

        navigation.addTab("运行状态", unavailablePage(status));
        navigation.addTab("交易与售后", unavailablePage("交易查询与售后受理功能尚未开放。"));
        navigation.addTab("优惠公告", unavailablePage("优惠公告发布功能尚未开放。"));
        content.add(navigation, BorderLayout.CENTER);
        JPanel identityBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        JLabel identity = new JLabel();
        identity.putClientProperty("html.disable", Boolean.TRUE);
        identity.setText("管理员：" + administrator.displayName());
        identityBar.add(identity);
        identityBar.add(logout);
        content.add(identityBar, BorderLayout.SOUTH);

        setContentPane(content);
        setSize(780, 500);
        setLocationRelativeTo(null);
    }

    public void bindLogoutAction(Runnable action) {
        logout.addActionListener(event -> action.run());
    }

    public void installPromotions(PromotionManagementPanel panel) {
        navigation.insertTab("促销管理",null,panel,"维护商品直降与折扣",1);
        setMinimumSize(new Dimension(860,660));
        setSize(980,740);
        setLocationRelativeTo(null);
    }

    public void installCoupons(CouponManagementPanel panel) {
        navigation.insertTab("优惠券管理",null,panel,"草稿、发布及暂停领取",Math.min(2,navigation.getTabCount()));
    }

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
