package com.draftwa.mobile;

import android.content.ContentResolver;
import android.net.Uri;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

final class SpreadsheetImporter {
    static List<ProspectRecord> importFile(ContentResolver resolver, Uri uri, String name) throws Exception {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("Fichier illisible");
            List<String[]> rows = lower.endsWith(".csv") ? readCsv(in) : readXlsx(in);
            return validate(rows);
        }
    }

    private static List<ProspectRecord> validate(List<String[]> rows) {
        if (rows.isEmpty()) throw new IllegalArgumentException("Le fichier est vide");
        String[] h = rows.get(0);
        if (h.length < 4
                || !eq(h[0], "prospect_id")
                || !eq(h[1], "nom")
                || !eq(h[2], "telephones")
                || !eq(h[3], "message")) {
            throw new IllegalArgumentException("Colonnes requises: prospect_id | nom | telephones | message");
        }
        List<ProspectRecord> out = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            String id = cell(r, 0);
            String name = cell(r, 1);
            String phones = cell(r, 2);
            String msg = cell(r, 3);
            if (id.trim().isEmpty() && name.trim().isEmpty() && phones.trim().isEmpty() && msg.trim().isEmpty()) continue;
            if (id.trim().isEmpty()) throw new IllegalArgumentException("Ligne " + (i + 1) + ": prospect_id vide");
            if (phones.trim().isEmpty()) throw new IllegalArgumentException("Ligne " + (i + 1) + ": telephones vide");
            if (msg.trim().isEmpty()) throw new IllegalArgumentException("Ligne " + (i + 1) + ": message vide");
            ProspectRecord p = new ProspectRecord(id, name, phones, msg);
            if (p.phones.isEmpty()) {
                p.status = ProspectRecord.NO_WHATSAPP;
                p.lastError = "Aucun numéro mobile valide après normalisation";
            }
            out.add(p);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Aucun prospect exploitable");
        return out;
    }

    private static boolean eq(String a, String b) {
        String s = a == null ? "" : a.trim().toLowerCase(Locale.ROOT)
                .replace("é", "e").replace("è", "e").replace("ê", "e");
        return s.equals(b);
    }

    private static String cell(String[] row, int i) { return i < row.length && row[i] != null ? row[i] : ""; }

    private static List<String[]> readCsv(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        List<String[]> rows = new ArrayList<>();
        String line;
        char sep = ',';
        boolean first = true;
        while ((line = br.readLine()) != null) {
            if (first) {
                if (line.indexOf(';') >= 0 && line.indexOf(',') < 0) sep = ';';
                first = false;
            }
            rows.add(parseCsvLine(line, sep));
        }
        return rows;
    }

    private static String[] parseCsvLine(String line, char sep) {
        List<String> cells = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { b.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == sep && !quoted) {
                cells.add(b.toString()); b.setLength(0);
            } else b.append(c);
        }
        cells.add(b.toString());
        return cells.toArray(new String[0]);
    }

    private static List<String[]> readXlsx(InputStream in) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                if (!n.equals("xl/sharedStrings.xml") && !n.equals("xl/worksheets/sheet1.xml")) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buf)) > 0) out.write(buf, 0, read);
                entries.put(n, out.toByteArray());
            }
        }
        byte[] sheet = entries.get("xl/worksheets/sheet1.xml");
        if (sheet == null) throw new IllegalArgumentException("La première feuille Excel est introuvable");
        List<String> shared = parseShared(entries.get("xl/sharedStrings.xml"));
        Document doc = parseXml(sheet);
        NodeList rowNodes = doc.getElementsByTagName("row");
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element row = (Element) rowNodes.item(i);
            NodeList cells = row.getElementsByTagName("c");
            String[] values = new String[4];
            for (int c = 0; c < cells.getLength(); c++) {
                Element ce = (Element) cells.item(c);
                String ref = ce.getAttribute("r");
                int col = columnIndex(ref);
                if (col < 0 || col > 3) continue;
                String type = ce.getAttribute("t");
                String value = "";
                if ("inlineStr".equals(type)) {
                    NodeList ts = ce.getElementsByTagName("t");
                    if (ts.getLength() > 0) value = ts.item(0).getTextContent();
                } else {
                    NodeList vs = ce.getElementsByTagName("v");
                    if (vs.getLength() > 0) value = vs.item(0).getTextContent();
                    if ("s".equals(type)) {
                        try { value = shared.get(Integer.parseInt(value)); } catch (Throwable ignored) {}
                    }
                }
                values[col] = value == null ? "" : value;
            }
            for (int k = 0; k < values.length; k++) if (values[k] == null) values[k] = "";
            rows.add(values);
        }
        return rows;
    }

    private static List<String> parseShared(byte[] xml) throws Exception {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        Document doc = parseXml(xml);
        NodeList sis = doc.getElementsByTagName("si");
        for (int i = 0; i < sis.getLength(); i++) out.add(sis.item(i).getTextContent());
        return out;
    }

    private static Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(bytes)));
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

    private SpreadsheetImporter() {}
}
