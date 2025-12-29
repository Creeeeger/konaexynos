package xzr.konabess.utils;

/** Parses scalar values used by the decompiled device-tree tables. */
public class DtsHelper {
    /**
     * Decodes a quoted three-character DTS value into a packed integer.
     *
     * <p>DTS escape sequences are converted first. Each resulting character is appended by shifting
     * the previous value eight bits to the left.
     *
     * @param input quoted DTS value, optionally followed by a semicolon
     * @return packed value in source order
     * @throws IllegalArgumentException if normalization does not produce exactly three characters
     */
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
            throw new IllegalArgumentException(
                    "Invalid input length. Expected 3 characters, got: " + input.length());
        }

        int result = 0;
        for (int i = 0; i < input.length(); i++) {
            result = (result << 8) | input.charAt(i);
        }
        return result;
    }

    /**
     * Parses one scalar token as a quoted three-character value, hexadecimal integer, or decimal
     * integer.
     *
     * @param line scalar value token
     * @return holder containing the trimmed token and parsed value
     * @throws IllegalArgumentException if the token has an unsupported numeric form
     */
    public static intLine decode_int_line(String line) throws IllegalArgumentException {
        line = line.trim();

        intLine intLine = new intLine();
        intLine.name = line;
        String value = line;

        try {
            if (value.contains("\"")) {
                intLine.value = decode_stringed_int(value);
            } else if (value.startsWith("0x") || value.startsWith("0X")) {
                intLine.value = Long.parseLong(value.substring(2).trim(), 16);
            } else {
                intLine.value = Long.parseLong(value.trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid number format in line: " + line, e);
        }

        return intLine;
    }

    /**
     * Wraps a raw table token without converting its value.
     *
     * @param line token to preserve
     * @return holder whose name and value both contain the trimmed token
     */
    public static hexLine decode_hex_line(String line) {
        line = line.trim();
        hexLine hexLine = new hexLine();
        hexLine.name = line;
        hexLine.value = line;
        return hexLine;
    }

    /**
     * Converts a signed decimal integer string to an uppercase hexadecimal token.
     *
     * @param input decimal integer
     * @return value prefixed with {@code 0x}
     * @throws IllegalArgumentException if {@code input} is not a Java {@code int}
     */
    public static String inputToHex(String input) {
        try {
            int parsed = Integer.parseInt(input);
            return String.format("0x%X", parsed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid input: " + input, e);
        }
    }

    /** Parsed scalar token and its numeric value. */
    public static class intLine {
        /** Trimmed source token. */
        public String name;
        /** Parsed numeric representation. */
        public long value;
    }

    /** Raw token preserved for display or later serialization. */
    public static class hexLine {
        /** Trimmed source token. */
        public String name;
        /** Unparsed token value. */
        public String value;
    }
}
