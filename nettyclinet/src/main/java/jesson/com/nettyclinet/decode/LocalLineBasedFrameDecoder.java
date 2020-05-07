package jesson.com.nettyclinet.decode;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LineBasedFrameDecoder;

public class LocalLineBasedFrameDecoder extends LineBasedFrameDecoder {


    public LocalLineBasedFrameDecoder(int maxLength) {
        super(maxLength);
    }

    public LocalLineBasedFrameDecoder(int maxLength, boolean stripDelimiter, boolean failFast) {
        super(maxLength, stripDelimiter, failFast);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        return super.decode(ctx, buffer);
    }
}
