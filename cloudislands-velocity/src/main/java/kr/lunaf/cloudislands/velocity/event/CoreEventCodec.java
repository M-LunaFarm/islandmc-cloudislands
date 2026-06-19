package kr.lunaf.cloudislands.velocity.event;

public interface CoreEventCodec {
    CoreEventBatch decodeBatch(String json);
}
