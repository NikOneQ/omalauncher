import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the module's AndroidManifest.xml in Android's binary XML (what aapt2 would make), since there is no aapt2
 * for this machine. Strings with an android: resource id come first in the pool, in the order of the resource map;
 * each element's attributes are sorted by resource id, those without one after them.
 */
public class Axml {
    static final int NS = 0, NAME = 1;
    static final String ANDROID = "http://schemas.android.com/apk/res/android";
    static final int T_STRING = 0x03, T_INT = 0x10, T_BOOL = 0x12;

    // attribute names with resource ids, in this order at the start of the string pool
    static final String[] ATTRS = {"label", "name", "value", "minSdkVersion", "versionCode", "versionName", "targetSdkVersion"};
    static final int[] IDS = {0x01010001, 0x01010003, 0x01010024, 0x0101020c, 0x0101021b, 0x0101021c, 0x01010270};

    static List<String> pool = new ArrayList<>();
    static int str(String s) {
        int i = pool.indexOf(s);
        if (i >= 0) return i;
        pool.add(s);
        return pool.size() - 1;
    }

    record Attr(boolean android, String name, int type, Object value) {
        int resId() {
            if (!android) return Integer.MAX_VALUE;
            for (int i = 0; i < ATTRS.length; i++) if (ATTRS[i].equals(name)) return IDS[i];
            throw new IllegalArgumentException(name);
        }
    }

    static ByteArrayOutputStream body = new ByteArrayOutputStream();

    static void u32(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >> 8); o.write(v >> 16); o.write(v >> 24); }
    static void u16(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >> 8); }

    static void start(String tag, Attr... attrs) {
        Attr[] a = attrs.clone();
        java.util.Arrays.sort(a, (x, y) -> Integer.compareUnsigned(x.resId(), y.resId()));
        u16(body, 0x0102); u16(body, 16); u32(body, 36 + 20 * a.length);
        u32(body, 1); u32(body, -1);
        u32(body, -1); u32(body, str(tag));
        u16(body, 20); u16(body, 20); u16(body, a.length); u16(body, 0); u16(body, 0); u16(body, 0);
        for (Attr at : a) {
            u32(body, at.android ? str(ANDROID) : -1);
            u32(body, str(at.name));
            int data;
            if (at.type == T_STRING) { data = str((String) at.value); u32(body, data); }
            else { data = at.type == T_BOOL ? (((Boolean) at.value) ? -1 : 0) : (Integer) at.value; u32(body, -1); }
            u16(body, 8); body.write(0); body.write(at.type); u32(body, data);
        }
    }

    static void end(String tag) {
        u16(body, 0x0103); u16(body, 16); u32(body, 24);
        u32(body, 1); u32(body, -1); u32(body, -1); u32(body, str(tag));
    }

    static Attr a(String n, String v) { return new Attr(true, n, T_STRING, v); }
    static Attr a(String n, int v) { return new Attr(true, n, T_INT, v); }
    static Attr a(String n, boolean v) { return new Attr(true, n, T_BOOL, v); }

    public static void main(String[] args) throws Exception {
        String out = args[0], pkg = args[1], label = args[2], desc = args[3];
        int versionCode = Integer.parseInt(args[4]);
        String versionName = args[5];
        for (String s : ATTRS) str(s);
        str("android"); str(ANDROID);

        // namespace start
        u16(body, 0x0100); u16(body, 16); u32(body, 24); u32(body, 1); u32(body, -1); u32(body, str("android")); u32(body, str(ANDROID));
        start("manifest", a("versionCode", versionCode), a("versionName", versionName), new Attr(false, "package", T_STRING, pkg));
        start("uses-sdk", a("minSdkVersion", 35), a("targetSdkVersion", 35));
        end("uses-sdk");
        start("application", a("label", label));
        start("meta-data", a("name", "xposedmodule"), a("value", true)); end("meta-data");
        start("meta-data", a("name", "xposeddescription"), a("value", desc)); end("meta-data");
        start("meta-data", a("name", "xposedminversion"), a("value", 82)); end("meta-data");
        end("application");
        end("manifest");
        u16(body, 0x0101); u16(body, 16); u32(body, 24); u32(body, 1); u32(body, -1); u32(body, str("android")); u32(body, str(ANDROID));

        // string pool, UTF-16
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offs = new int[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            offs[i] = data.size();
            String s = pool.get(i);
            u16(data, s.length());
            for (char c : s.toCharArray()) u16(data, c);
            u16(data, 0);
        }
        while (data.size() % 4 != 0) data.write(0);
        ByteArrayOutputStream sp = new ByteArrayOutputStream();
        int spHeader = 28, stringsStart = spHeader + 4 * pool.size();
        u16(sp, 0x0001); u16(sp, spHeader); u32(sp, stringsStart + data.size());
        u32(sp, pool.size()); u32(sp, 0); u32(sp, 0); u32(sp, stringsStart); u32(sp, 0);
        for (int o : offs) u32(sp, o);
        data.writeTo(sp);

        ByteArrayOutputStream rm = new ByteArrayOutputStream();
        u16(rm, 0x0180); u16(rm, 8); u32(rm, 8 + 4 * IDS.length);
        for (int id : IDS) u32(rm, id);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        u16(file, 0x0003); u16(file, 8); u32(file, 8 + sp.size() + rm.size() + body.size());
        sp.writeTo(file); rm.writeTo(file); body.writeTo(file);
        try (FileOutputStream f = new FileOutputStream(out)) { file.writeTo(f); }
        System.out.println("wrote " + out + ": " + file.size() + " bytes, " + pool.size() + " strings");
    }
}
