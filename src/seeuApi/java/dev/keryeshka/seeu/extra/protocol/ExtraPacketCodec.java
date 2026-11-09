package dev.keryeshka.seeu.extra.protocol;

/** SeeU Extra 0.3.0 codec signatures; actual implementation comes from SeeU Extra. */
public final class ExtraPacketCodec {
    private ExtraPacketCodec() {}
    public static EntitySnapshotPacket decodeSnapshotPacket(byte[] payload) {
        throw new AssertionError("Compile-only SeeU Extra stub");
    }
    public static byte[] encodeSnapshotPacket(EntitySnapshotPacket packet) {
        throw new AssertionError("Compile-only SeeU Extra stub");
    }
}
