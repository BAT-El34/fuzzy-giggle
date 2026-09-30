package com.draftwa.mobile;

import android.content.ContentResolver;
import android.net.Uri;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.SAXParserFactory;

final class SpreadsheetImporter {
    private static final int HEADER_SCAN_LIMIT = 50;

    static SpreadsheetImportResult importFile(ContentResolver resolver, Uri uri, String name, File cacheDir) throws Exception {
        File temp = File.createTempFile("draftwa_import_", ".bin", cacheDir);
        try {
            try (InputStream in = resolver.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new IllegalArgumentException("Fichier illisible");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }

            String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
            boolean csv = lower.endsWith(".csv") || lower.endsWith(".txt");
            if (!csv && !looksLikeZip(temp)) {
                throw new IllegalArgumentException("Format non pris en charge. Utilise un fichier .xlsx ou .csv.");
            }

            RowCollector collector = new RowCollector();
            if (csv) readCsv(temp, collector);
            else readXlsx(temp, collector);
            return collector.finish();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static boolean looksLikeZip(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            int a = in.read(), b = in.read();
            return a == 'P' && b == 'K';
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void readXlsx(File file, RowCollector collector) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            List<String> shared = readSharedStrings(zip);
            ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheet == null) {
                for (java.util.Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                    ZipEntry candidate = e.nextElement();
                    if (!candidate.isDirectory()
                            && candidate.getName().startsWith("xl/worksheets/sheet")
                            && candidate.getName().endsWith(".xml")) {
                        sheet = candidate;
                        break;
                    }
                }
            }
            if (sheet == null) throw new IllegalArgumentException("Aucune feuille Excel lisible n’a été trouvée");

            SAXParserFactory factory = secureSaxFactory();
            try (InputStream in = zip.getInputStream(sheet)) {
                factory.newSAXParser().parse(in, new WorksheetHandler(shared, collector));
            }
        }
    }

    private static List<String> readSharedStrings(ZipFile zip) throws Exception {
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        List<String> shared = new ArrayList<>();
        if (entry == null) return shared;

        SAXParserFactory factory = secureSaxFactory();
        try (InputStream in = zip.getInputStream(entry)) {
            factory.newSAXParser().parse(in, new DefaultHandler() {
                boolean inSi = false;
                boolean inText = false;
                StringBuilder current = new StringBuilder();

                @Override public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    if ("si".equals(qName)) {
                        inSi = true;
                        current.setLength(0);
                    } else if (inSi && "t".equals(qName)) {
                        inText = true;
                    }
                }

                @Override public void characters(char[] ch, int start, int length) {
                    if (inSi && inText) current.append(ch, start, length);
                }

                @Override public void endElement(String uri, String localName, String qName) {
                    if ("t".equals(qName)) inText = false;
                    else if ("si".equals(qName)) {
                        shared.add(current.toString());
                        inSi = false;
                    }
                }
            });
        }
        return shared;
    }

    private static SAXParserFactory secureSaxFactory() throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(false);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Throwable ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Throwable ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Throwable ignored) {}
        return f;
    }

    private static final class WorksheetHandler extends DefaultHandler {
        private final List<String> shared;
        private final RowCollector collector;
        private String[] row = new String[4];
        private int rowNumber = 0;
        private int cellColumn = -1;
        private String cellType = "";
        private boolean collectingValue = false;
        private boolean collectingInlineText = false;
        private final StringBuilder value = new StringBuilder();

        WorksheetHandler(List<String> shared, RowCollector collector) {
            this.shared = shared;
            this.collector = collector;
        }

        @Override public void startElement(String uri, String localName, String qName, Attributes a) throws SAXException {
            if ("row".equals(qName)) {
                row = new String[] {"", "", "", ""};
                String r = a.getValue("r");
                try { rowNumber = r == null ? rowNumber + 1 : Integer.parseInt(r); }
                catch (Throwable ignored) { rowNumber++; }
            } else if ("c".equals(qName)) {
                cellColumn = columnIndex(a.getValue("r"));
                cellType = a.getValue("t") == null ? "" : a.getValue("t");
                value.setLength(0);
            } else if ("v".equals(qName)) {
                collectingValue = true;
                value.setLength(0);
            } else if ("t".equals(qName) && "inlineStr".equals(cellType)) {
                collectingInlineText = true;
            }
        }

        @Override public void characters(char[] ch, int start, int length) {
            if (collectingValue || collectingInlineText) value.append(ch, start, length);
        }

        @Override public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("v".equals(qName)) {
                collectingValue = false;
            } else if ("t".equals(qName) && collectingInlineText) {
                collectingInlineText = false;
            } else if ("c".equals(qName)) {
                if (cellColumn >= 0 && cellColumn < 4) {
                    String raw = value.toString();
                    String resolved = raw;
                    if ("s".equals(cellType)) {
                        try {
                            int index = Integer.parseInt(raw.trim());
                            resolved = index >= 0 && index < shared.size() ? shared.get(index) : "";
                        } catch (Throwable ignored) {
                            resolved = "";
                        }
                    }
                    row[cellColumn] = resolved == null ? "" : resolved;
                }
                cellColumn = -1;
                cellType = "";
                value.setLength(0);
            } else if ("row".equals(qName)) {
                collector.accept(rowNumber, row);
            }
        }
    }

    private static void readCsv(File file, RowCollector collector) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), 64 * 1024)) {
            br.mark(64 * 1024);
            char separator = detectSeparator(br);
            br.reset();
            parseCsv(br, separator, collector);
        }
    }

    private static char detectSeparator(BufferedReader br) throws Exception {
        int comma = 0, semicolon = 0, tab = 0;
        boolean quoted = false;
        int c;
        while ((c = br.read()) != -1) {
            char ch = (char) c;
            if (ch == '"') quoted = !quoted;
            else if (!quoted) {
                if (ch == ',') comma++;
                else if (ch == ';') semicolon++;
                else if (ch == '\t') tab++;
                else if (ch == '\n' || ch == '\r') {
                    if (comma + semicolon + tab > 0) break;
                }
            }
        }
        if (tab >= comma && tab >= semicolon && tab > 0) return '\t';
        if (semicolon > comma) return ';';
        return ',';
    }

    private static void parseCsv(Reader reader, char separator, RowCollector collector) throws Exception {
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int rowNumber = 1;
        int c;
        while ((c = reader.read()) != -1) {
            char ch = (char) c;
            if (ch == '"') {
                if (quoted) {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') cell.append('"');
                    else {
                        quoted = false;
                        if (next != -1) reader.reset();
                    }
                } else {
                    quoted = true;
                }
            } else if (ch == separator && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.reset();
                }
                row.add(cell.toString());
                cell.setLength(0);
                collector.accept(rowNumber++, firstFour(row));
                row.clear();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            collector.accept(rowNumber, firstFour(row));
        }
    }

    private static String[] firstFour(List<String> row) {
        String[] out = new String[] {"", "", "", ""};
        for (int i = 0; i < Math.min(4, row.size()); i++) out[i] = row.get(i) == null ? "" : row.get(i);
        return out;
    }

    private static int columnIndex(String ref) {
        if (ref == null || ref.isEmpty()) return -1;
        int i = 0, v = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) {
            v = v * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
            i++;
        }
        return v - 1;
    }

    private static String normalizeHeader(String value) {
        String s = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        return s.replace("é", "e").replace("è", "e").replace("ê", "e");
    }

    private static boolean headerMatches(String[] row) {
        return row != null && row.length >= 4
                && "prospect_id".equals(normalizeHeader(row[0]))
                && "nom".equals(normalizeHeader(row[1]))
                && "telephones".equals(normalizeHeader(row[2]))
                && "message".equals(normalizeHeader(row[3]));
    }

    private static boolean allBlank(String[] row) {
        if (row == null) return true;
        for (String s : row) if (s != null && !s.trim().isEmpty()) return false;
        return true;
    }

    private static final class RowCollector {
        final List<ProspectRecord> records = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        boolean headerFound = false;
        int headerRow = -1;
        int sourceRows = 0;
        int skippedEmpty = 0;
        int skippedNoPhone = 0;
        int skippedInvalidPhone = 0;
        int skippedMalformed = 0;

        void accept(int rowNumber, String[] row) throws SAXException {
            if (!headerFound) {
                if (headerMatches(row)) {
                    headerFound = true;
                    headerRow = rowNumber;
                    return;
                }
                if (!allBlank(row) && rowNumber >= HEADER_SCAN_LIMIT) {
                    throw new SAXException("En-têtes introuvables. Les 4 premières colonnes doivent être : prospect_id | nom | telephones | message");
                }
                return;
            }

            sourceRows++;
            if (allBlank(row)) {
                skippedEmpty++;
                return;
            }

            String id = row[0] == null ? "" : row[0].trim();
            String name = row[1] == null ? "" : row[1].trim();
            String phones = row[2] == null ? "" : row[2].trim();
            String message = row[3] == null ? "" : row[3];

            if (phones.isEmpty()) {
                skippedNoPhone++;
                return;
            }
            if (id.isEmpty() || message.trim().isEmpty() || ids.contains(id)) {
                skippedMalformed++;
                return;
            }

            ProspectRecord prospect = new ProspectRecord(id, name, phones, message);
            if (prospect.phones.isEmpty()) {
                skippedInvalidPhone++;
                return;
            }

            ids.add(id);
            records.add(prospect);
        }

        SpreadsheetImportResult finish() {
            if (!headerFound) {
                throw new IllegalArgumentException("En-têtes introuvables. Les 4 premières colonnes doivent être : prospect_id | nom | telephones | message");
            }
            return new SpreadsheetImportResult(records, sourceRows, skippedEmpty, skippedNoPhone,
                    skippedInvalidPhone, skippedMalformed, headerRow);
        }
    }

    private SpreadsheetImporter() {}
}
