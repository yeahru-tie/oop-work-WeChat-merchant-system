package cn.edu.jlu.wechatmerchant.client.application;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import java.io.IOException;
import java.util.Optional;

/** Durable recovery boundary; all methods are called off the Swing event thread. */
public interface CheckoutJournal {
    Optional<PendingCheckouts.Entry> first(UserIdentity owner) throws IOException;
    void save(UserIdentity owner,PendingCheckouts.Entry entry) throws IOException;
    void remove(UserIdentity owner,PendingCheckouts.Entry entry) throws IOException;
}
