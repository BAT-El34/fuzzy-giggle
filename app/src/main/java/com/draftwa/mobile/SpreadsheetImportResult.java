package com.draftwa.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SpreadsheetImportResult {
    final List<ProspectRecord> records;
    final int sourceRows;
    final int imported;
    final int skippedEmpty;
    final int skippedNoPhone;
    final int skippedInvalidPhone;
    final int skippedMalformed;
    final int headerRow;

    SpreadsheetImportResult(List<ProspectRecord> records, int sourceRows, int skippedEmpty,
                            int skippedNoPhone, int skippedInvalidPhone, int skippedMalformed,
                            int headerRow) {
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
        this.sourceRows = sourceRows;
        this.imported = records.size();
        this.skippedEmpty = skippedEmpty;
        this.skippedNoPhone = skippedNoPhone;
        this.skippedInvalidPhone = skippedInvalidPhone;
        this.skippedMalformed = skippedMalformed;
        this.headerRow = headerRow;
    }

    int skippedTotal() {
        return skippedEmpty + skippedNoPhone + skippedInvalidPhone + skippedMalformed;
    }

    String shortSummary() {
        return imported + " prospect" + (imported == 1 ? "" : "s") + " prêt" + (imported == 1 ? "" : "s")
                + " • " + skippedTotal() + " ligne" + (skippedTotal() == 1 ? "" : "s") + " ignorée" + (skippedTotal() == 1 ? "" : "s");
    }
}
