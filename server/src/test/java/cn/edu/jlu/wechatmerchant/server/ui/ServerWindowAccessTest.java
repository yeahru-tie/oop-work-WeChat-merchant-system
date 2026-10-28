package cn.edu.jlu.wechatmerchant.server.ui;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import org.junit.jupiter.api.Test;

class ServerWindowAccessTest {
    @Test
    void anonymousConstructionIsRejectedBeforeCreatingAnyNativeFrame() {
        assertThrows(SecurityException.class, () -> new ServerWindow("运行状态", null));
    }

    @Test
    void buyerIdentityCannotBeUsedToCreateManagementEvenInHeadlessMode() {
        var buyer = new UserIdentity(1, "buyer_a", "买家", UserIdentity.Role.BUYER);
        assertThrows(SecurityException.class, () -> new ServerWindow("运行状态", buyer));
    }
}
