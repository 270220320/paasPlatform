import com.dji.flighthubsdk.FlightHubSDK;
import com.dji.flighthubsdk.bean.FlightHubSdkConfig;
import com.dji.flighthubsdk.core.JniLibraryLoader;
import com.dji.flighthubsdk.generated.FlightHubSdkErrorCode;

public class InitTest {
    public static void main(String[] args) {
        System.out.println("os=" + System.getProperty("os.name") + " arch=" + System.getProperty("os.arch"));
        try {
            JniLibraryLoader.loadLibrary();
            System.out.println("JNI loaded=" + JniLibraryLoader.isLibraryLoaded());
        } catch (Throwable t) {
            System.out.println("JNI load FAILED");
            t.printStackTrace(System.out);
            return;
        }
        FlightHubSdkConfig cfg = new FlightHubSdkConfig();
        String address = args.length > 0 ? args[0] : "http://192.168.76.141";
        String port = args.length > 1 ? args[1] : "30812";
        cfg.setAddress(address);
        cfg.setPort(port);
        cfg.setToken(args.length > 2 ? args[2] : "dummy");
        cfg.setNotificationServerAddress(args.length > 3 ? args[3]
                : "ws://192.168.76.141:30812/openapi/v0.9/notification/ws/v1/notifications");
        cfg.setLogLevel("info");
        cfg.setLogToConsole(true);
        cfg.setShowVersionInfo(true);
        cfg.setLogFilePath("/tmp/fh2-sdk-logs");
        System.out.println("address=" + cfg.getAddress() + " port=" + cfg.getPort());
        try {
            FlightHubSdkErrorCode r = FlightHubSDK.initialize(cfg);
            System.out.println("initialize => " + r + " swig=" + r.swigValue());
            System.out.println("isInitialized=" + FlightHubSDK.isInitialized());
        } catch (Throwable t) {
            System.out.println("initialize threw");
            t.printStackTrace(System.out);
        }
    }
}
