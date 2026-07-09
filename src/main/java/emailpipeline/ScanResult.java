package emailpipeline;

import java.util.List;

/**
 * The workflow's OUTPUT contract.
 *
 * A workflow that returns void looks like a toy. Real orchestration produces a
 * result other systems consume, and treating that result as a versioned API is
 * a senior habit. This carries the final verdict plus the per-scanner detail so
 * a caller (or the Temporal UI) can see exactly why a message was allowed or
 * blocked.
 */
public class ScanResult {

    public enum Verdict { CLEAN, MALICIOUS }

    private Verdict verdict;
    private String extractedTextSummary; // e.g. "1423 chars extracted"
    private int urlsScanned;
    private String sandboxVerdict;       // e.g. "safe"
    private List<String> notes;

    public ScanResult() {
    }

    public ScanResult(Verdict verdict, String extractedTextSummary,
                      int urlsScanned, String sandboxVerdict, List<String> notes) {
        this.verdict = verdict;
        this.extractedTextSummary = extractedTextSummary;
        this.urlsScanned = urlsScanned;
        this.sandboxVerdict = sandboxVerdict;
        this.notes = notes;
    }

    public Verdict getVerdict() { return verdict; }
    public void setVerdict(Verdict verdict) { this.verdict = verdict; }

    public String getExtractedTextSummary() { return extractedTextSummary; }
    public void setExtractedTextSummary(String s) { this.extractedTextSummary = s; }

    public int getUrlsScanned() { return urlsScanned; }
    public void setUrlsScanned(int urlsScanned) { this.urlsScanned = urlsScanned; }

    public String getSandboxVerdict() { return sandboxVerdict; }
    public void setSandboxVerdict(String v) { this.sandboxVerdict = v; }

    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }

    @Override
    public String toString() {
        return "ScanResult{verdict=" + verdict
                + ", text=" + extractedTextSummary
                + ", urlsScanned=" + urlsScanned
                + ", sandbox=" + sandboxVerdict
                + ", notes=" + notes + "}";
    }
}
