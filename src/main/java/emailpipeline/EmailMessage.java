package emailpipeline;

/**
 * A minimal inbound email message.
 *
 * In a real system (like Mimecast) this carries the raw message, headers,
 * attachments, etc. For the demo we keep just an id, sender, and subject so
 * the panel isn't distracted by email plumbing.
 *
 * NOTE: Temporal serializes this to/from JSON automatically, so it needs a
 * no-arg constructor and public getters/setters (standard POJO).
 */
public class EmailMessage {
    private String id;
    private String from;
    private String subject;

    public EmailMessage() {
    }

    public EmailMessage(String id, String from, String subject) {
        this.id = id;
        this.from = from;
        this.subject = subject;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    @Override
    public String toString() {
        return "EmailMessage{id=" + id + ", from=" + from + ", subject='" + subject + "'}";
    }
}
