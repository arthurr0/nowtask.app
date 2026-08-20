package app.nowtask.exports;

import java.nio.charset.StandardCharsets;
import java.util.List;

final class Csv {

    private static final String NEWLINE = "\r\n";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final StringBuilder text = new StringBuilder();

    void row(List<String> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                text.append(',');
            }
            text.append(escape(cells.get(index)));
        }
        text.append(NEWLINE);
    }

    byte[] bytes() {
        byte[] body = text.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        return out;
    }

    private static String escape(String value) {
        String cell = value == null ? "" : value;
        boolean needsQuotes = cell.indexOf(',') >= 0
                || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0
                || cell.indexOf('\r') >= 0;

        if (!needsQuotes) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}
