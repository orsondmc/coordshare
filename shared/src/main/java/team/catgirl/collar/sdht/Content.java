package team.catgirl.collar.sdht;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.Hashing;

import java.util.Arrays;
import java.util.UUID;

public final class Content {

    private static final int MAX_SIZE = 2000;

    /**
     * Checksum of content
     */
    @JsonProperty("checksum")
    public final byte[] checksum;

    /**
     * Content
     */
    @JsonProperty("bytes")
    public final byte[] bytes;

    @JsonProperty("type")
    public final Class<?> type;

    private Content(@JsonProperty("checksum") byte[] checksum,
                    @JsonProperty("bytes") byte[] bytes,
                    @JsonProperty("type") Class<?> type) {
        this.checksum = checksum;
        this.bytes = bytes;
        this.type = type;
    }

    /**
     * Checks that the content within is valid
     * @return validity
     */
    public boolean isValid() {
        if (bytes.length > MAX_SIZE) {
            return false;
        }
        byte[] checksum = createChecksum(bytes);
        return Arrays.equals(this.checksum, checksum);
    }

    /**
     * Checks that the record checksum and the content checksum match
     * @param record to test
     * @return validity
     */
    public boolean isValid(Record record) {
        byte[] checksum = createChecksum(bytes);
        return Arrays.equals(record.checksum, checksum);
    }

    /**
     * Create a record based on the key and its content
     * @param key addressing the content
     * @return record
     */
    public Record toRecord(Key key) {
        return new Record(key, checksum);
    }

    /**
     * Create content from bytes
     * @param bytes to store
     * @param contentType type
     * @return content
     */
    public static Content from(byte[] bytes, Class<?> contentType) {
        assertSize(bytes);
        byte[] checksum = createChecksum(bytes);
        return new Content(checksum, bytes, contentType);
    }

    private static byte[] createChecksum(byte[] bytes) {
        return Hashing.sha256().hashBytes(bytes).asBytes();
    }

    private static void assertSize(byte[] bytes) {
        if (bytes.length > MAX_SIZE) {
            throw new IllegalStateException("content size exceeds " + MAX_SIZE + " bytes");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content content = (Content) o;
        return Arrays.equals(checksum, content.checksum) && Arrays.equals(bytes, content.bytes);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(checksum);
        result = 31 * result + Arrays.hashCode(bytes);
        return result;
    }
}
