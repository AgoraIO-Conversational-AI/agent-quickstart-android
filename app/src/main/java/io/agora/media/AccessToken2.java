package io.agora.media;

import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AccessToken2 {
    public enum PrivilegeRtc {
        PRIVILEGE_JOIN_CHANNEL(1),
        PRIVILEGE_PUBLISH_AUDIO_STREAM(2),
        PRIVILEGE_PUBLISH_VIDEO_STREAM(3),
        PRIVILEGE_PUBLISH_DATA_STREAM(4);

        public short intValue;

        PrivilegeRtc(int value) {
            intValue = (short) value;
        }
    }

    public enum PrivilegeRtm {
        PRIVILEGE_LOGIN(1);

        public short intValue;

        PrivilegeRtm(int value) {
            intValue = (short) value;
        }
    }

    private static final String VERSION = "007";
    public static final short SERVICE_TYPE_RTC = 1;
    public static final short SERVICE_TYPE_RTM = 2;

    public String appCert = "";
    public String appId = "";
    public int expire;
    public int issueTs;
    public int salt;
    public Map<Short, Service> services = new TreeMap<>();

    public AccessToken2() {
    }

    public AccessToken2(String appId, String appCert, int expire) {
        this.appCert = appCert;
        this.appId = appId;
        this.expire = expire;
        this.issueTs = Utils.getTimestamp();
        this.salt = Utils.randomInt();
    }

    public void addService(Service service) {
        this.services.put(service.getServiceType(), service);
    }

    public String build() throws Exception {
        if (!Utils.isUUID(this.appId) || !Utils.isUUID(this.appCert)) {
            return "";
        }

        ByteBuf buf = new ByteBuf()
                .put(this.appId)
                .put(this.issueTs)
                .put(this.expire)
                .put(this.salt)
                .put((short) this.services.size());
        byte[] signing = getSign();

        this.services.forEach((k, v) -> v.pack(buf));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signing, "HmacSHA256"));
        byte[] signature = mac.doFinal(buf.asBytes());

        ByteBuf bufferContent = new ByteBuf();
        bufferContent.put(signature);
        bufferContent.buffer.put(buf.asBytes());

        return getVersion() + Utils.base64Encode(Utils.compress(bufferContent.asBytes()));
    }

    public byte[] getSign() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new ByteBuf().put(this.issueTs).asBytes(), "HmacSHA256"));
        byte[] signing = mac.doFinal(this.appCert.getBytes());
        mac.init(new SecretKeySpec(new ByteBuf().put(this.salt).asBytes(), "HmacSHA256"));
        return mac.doFinal(signing);
    }

    public static String getUidStr(int uid) {
        if (uid == 0) {
            return "";
        }
        return String.valueOf(uid & 0xFFFFFFFFL);
    }

    public static String getVersion() {
        return VERSION;
    }

    public static class Service {
        public short type;
        public TreeMap<Short, Integer> privileges = new TreeMap<>();

        public Service() {
        }

        public Service(short serviceType) {
            this.type = serviceType;
        }

        public void addPrivilegeRtc(PrivilegeRtc privilege, int expire) {
            this.privileges.put(privilege.intValue, expire);
        }

        public void addPrivilegeRtm(PrivilegeRtm privilege, int expire) {
            this.privileges.put(privilege.intValue, expire);
        }

        public short getServiceType() {
            return this.type;
        }

        public ByteBuf pack(ByteBuf buf) {
            return buf.put(this.type).putIntMap(this.privileges);
        }
    }

    public static class ServiceRtc extends Service {
        public String channelName;
        public String uid;

        public ServiceRtc() {
            this.type = SERVICE_TYPE_RTC;
        }

        public ServiceRtc(String channelName, String uid) {
            this.type = SERVICE_TYPE_RTC;
            this.channelName = channelName;
            this.uid = uid;
        }

        public ByteBuf pack(ByteBuf buf) {
            return super.pack(buf).put(this.channelName).put(this.uid);
        }
    }

    public static class ServiceRtm extends Service {
        public String userId;

        public ServiceRtm() {
            this.type = SERVICE_TYPE_RTM;
        }

        public ServiceRtm(String userId) {
            this.type = SERVICE_TYPE_RTM;
            this.userId = userId;
        }

        public ByteBuf pack(ByteBuf buf) {
            return super.pack(buf).put(this.userId);
        }
    }
}
