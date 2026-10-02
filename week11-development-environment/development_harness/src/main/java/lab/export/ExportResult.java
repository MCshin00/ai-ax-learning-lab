package lab.export;

public record ExportResult(String csv, int excludedDuplicateCount, int exportedCount) {}
