package cn.edu.jlu.wechatmerchant.client.transport;

import java.io.IOException;

/** Carries a validated code only; untrusted remote error text is never displayed. */
public final class RemoteServiceException extends IOException {
    private static final long serialVersionUID = 1L;
    private final String code;

    public RemoteServiceException(String code) {
        super("Server rejected the request: " + code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
