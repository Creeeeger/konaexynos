package xzr.konabess.utils;

public class DtsHelper {
    public static boolean shouldUseHex() {
        return false;
    }

    public static class intLine {
        public String name;
        public long value;
    }

    public static class hexLine {
        public String name;
        public String value;
    }

    public static int decode_stringed_int(String input) throws Exception {
        input = input.replace("\"", "")
                .replace(";", "")
                .replace("\\a", "\7")
                .replace("\\b", "\b")
                .replace("\\f", "\f")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\v", "\11")
                .replace("\\\\", "\\")
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .trim();
        char[] chars = input.toCharArray();
        if (chars.length != 3)
            throw new Exception();
        int ret = 0;
        for (int i = 1; i <= chars.length; i++) {
            ret += (int) chars[chars.length - i] * Math.pow(256, i);
        }
        return ret;
    }

    public static intLine decode_int_line(String line) throws Exception {
        intLine intLine = new intLine();
        line = line.trim();
        intLine.name = line;
        intLine.name = intLine.name.trim();
        String value = line;
        if (value.contains("\"")) {
            intLine.value = decode_stringed_int(value);
            return intLine;
        }
        if (value.contains("0x")) {
            value = value.replace("0x", "").trim();
            intLine.value = Long.parseLong(value, 16);
        } else {
            value = value.trim();
            intLine.value = Long.parseLong(value);
        }
        return intLine;
    }

    public static hexLine decode_hex_line(String line) {
        hexLine hexLine = new hexLine();
        line = line.trim();
        hexLine.name = line;
        hexLine.name = hexLine.name.trim();
        hexLine.value = line;
        return hexLine;
    }

    public static String encodeIntOrHexLine(String name, String value) {
        return name + " = <" + value + ">;";
    }

    public static String inputToHex(String input) {
        int intValue = Integer.parseInt(input);
        String hexValue = Integer.toHexString(intValue);
        return "0x" + hexValue;
    }
}