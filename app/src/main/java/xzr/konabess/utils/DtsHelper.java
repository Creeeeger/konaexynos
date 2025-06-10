package xzr.konabess.utils;

/**
 * Utility class for decoding various DTS-formatted integer and hex lines,
 * and converting string inputs to hexadecimal representation.
 * <p>
 * Note: Methods throw IllegalArgumentException for invalid formats or inputs.
 * Forward-thinking: consider refactoring replaceAll chains for performance
 * and extending to support larger-than-3-byte stringed ints.
 */
public class DtsHelper {

    /**
     * Decode a string-encoded integer consisting of exactly 3 characters.
     * Supports common escape sequences (e.g., \n, \t, etc.).
     * <p>
     * Algorithm: Clean input of quotes, semicolons, and escaped quotes,
     * translate escape sequences to their char equivalents, then combine
     * three bytes into a 24-bit integer.
     *
     * @param input Raw DTS string, possibly including escapes and quotes
     * @return 24-bit integer result from 3-character string
     * @throws IllegalArgumentException if cleaned input length != 3
     */
    public static int decode_stringed_int(String input) throws IllegalArgumentException {
        // Strip wrapping quotes, semicolons, and escaped double-quotes
        input = input.replaceAll("\"|;|\\\\\"", "")
                // Process common backslash escapes
                .replace("\\a", "\u0007")  // Bell
                .replace("\\b", "\b")     // Backspace
                .replace("\\f", "\f")     // Formfeed
                .replace("\\n", "\n")     // Newline
                .replace("\\r", "\r")     // Carriage return
                .replace("\\t", "\t")     // Horizontal tab
                .replace("\\v", "\u000B")  // Vertical tab
                .replace("\\\\", "\\")  // Literal backslash
                .replace("\\'", "'")       // Literal single-quote
                .trim();                         // Clean up whitespace

        if (input.length() != 3) {
            throw new IllegalArgumentException(
                    "Invalid input length. Expected 3 characters, got: " + input.length());
        }

        int result = 0;
        for (int i = 0; i < input.length(); i++) {
            // Shift left 8 bits and append this character's byte value
            result = (result << 8) | input.charAt(i);
        }
        return result;
    }

    /**
     * Decode a line representing an integer value in decimal, hex, or string format.
     * <p>
     * If the line contains a double-quote, it is treated as a 3-character string and
     * passed to {@link #decode_stringed_int(String)}. If it starts with 0x or 0X,
     * parsed as hexadecimal. Otherwise parsed as decimal.
     *
     * @param line Input line (name=value pair expected, but only value is processed)
     * @return intLine containing original line and parsed long value
     * @throws IllegalArgumentException for invalid number formats
     */
    public static intLine decode_int_line(String line) throws IllegalArgumentException {
        line = line.trim(); // Remove leading/trailing whitespace

        intLine intLine = new intLine();
        intLine.name = line;
        String value = line;

        try {
            if (value.contains("\"")) {
                // String-encoded integer
                intLine.value = decode_stringed_int(value);
            } else if (value.startsWith("0x") || value.startsWith("0X")) {
                // Hexadecimal literal
                intLine.value = Long.parseLong(value.substring(2).trim(), 16);
            } else {
                // Decimal literal
                intLine.value = Long.parseLong(value.trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid number format in line: " + line, e);
        }

        return intLine;
    }

    /**
     * Wrap a raw hex line into a hexLine object without parsing.
     * Useful for forwarding hex strings unmodified.
     *
     * @param line Raw hex string
     * @return hexLine containing name and unmodified hex value
     */
    public static hexLine decode_hex_line(String line) {
        line = line.trim();
        hexLine hexLine = new hexLine();
        hexLine.name = line;
        hexLine.value = line;
        return hexLine;
    }

    /**
     * Convert a decimal string into a hexadecimal string (uppercase) prefixed with 0x.
     *
     * @param input Decimal number as string
     * @return Hexadecimal representation, e.g. "0x1A"
     * @throws IllegalArgumentException if input is not a valid integer
     */
    public static String inputToHex(String input) {
        try {
            int parsed = Integer.parseInt(input);
            return String.format("0x%X", parsed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid input: " + input, e);
        }
    }

    /**
     * Simple holder for an integer line and its parsed value.
     */
    public static class intLine {
        public String name;  // Original line content
        public long value;   // Parsed numeric value
    }

    /**
     * Simple holder for a hex line, preserving raw value.
     */
    public static class hexLine {
        public String name;  // Original line content
        public String value; // Raw hex string
    }
}