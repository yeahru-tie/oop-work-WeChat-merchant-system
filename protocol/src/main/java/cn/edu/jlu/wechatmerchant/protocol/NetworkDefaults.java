package cn.edu.jlu.wechatmerchant.protocol;

public final class NetworkDefaults {
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 18765;
    public static final int CONNECT_TIMEOUT_MS = 2000;
    public static final int RESPONSE_TIMEOUT_MS = 5000;
    public static final int SERVER_FRAME_TIMEOUT_MS = 30000;

    private NetworkDefaults() {
    }

    public static int configuredPort() {
        int port;
        try {
            port = Integer.parseInt(System.getProperty("merchant.port", Integer.toString(PORT)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("通信端口必须为 1 到 65535 的整数。", exception);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("通信端口必须为 1 到 65535 的整数。");
        }
        return port;
    }
}
