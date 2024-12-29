package xzr.konabess.utils;

public class DtsHelper {
    public static int decode_stringed_int(String input) throws IllegalArgumentException {
        input = input.replaceAll("\"|;|\\\\\"", "")
                .replace("\\a", "\7")
                .replace("\\b", "\b")
                .replace("\\f", "\f")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\v", "\11")
                .replace("\\\\", "\\")
                .replace("\\'", "'")
                .trim();

        if (input.length() != 3) {
            throw new IllegalArgumentException("Invalid input length. Expected 3 characters, got: " + input.length());
        }

        int result = 0;
        for (int i = 0; i < input.length(); i++) {
            result = (result << 8) | input.charAt(i); // Shift by 8 bits
        }
        return result;
    }

    public static intLine decode_int_line(String line) throws IllegalArgumentException {
        line = line.trim(); // Trim once at the start

        intLine intLine = new intLine();
        intLine.name = line; // Assign name directly

        // Extract value
        String value = line;

        try {
            if (value.contains("\"")) { // Handle stringed integer
                intLine.value = decode_stringed_int(value);
            } else if (value.startsWith("0x") || value.startsWith("0X")) { // Hexadecimal
                intLine.value = Long.parseLong(value.substring(2).trim(), 16);
            } else { // Decimal
                intLine.value = Long.parseLong(value.trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in line: " + line, e);
        }

        return intLine;
    }

    public static hexLine decode_hex_line(String line) {
        line = line.trim();
        hexLine hexLine = new hexLine();
        hexLine.name = line;
        hexLine.value = line;
        return hexLine;
    }

    public static String inputToHex(String input) {
        try {
            return String.format("0x%X", Integer.parseInt(input)); // Uppercase hex formatting
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid input: " + input, e);
        }
    }

    public static class intLine {
        public String name;
        public long value;
    }

    public static class hexLine {
        public String name;
        public String value;
    }
}