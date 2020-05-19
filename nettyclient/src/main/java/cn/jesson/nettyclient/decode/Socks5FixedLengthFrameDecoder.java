/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package cn.jesson.nettyclient.decode;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import cn.jesson.nettyclient.utils.LogUtil;

public class Socks5FixedLengthFrameDecoder extends LocalByteToMessageDecoder {

    private final String TAG = "Socks5FixedLengthFrameDecoder";

    private final int frameLength;
    private boolean mProxy;

    /**
     * Creates a new instance.
     *
     * @param frameLength the length of the frame
     */
    public Socks5FixedLengthFrameDecoder(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException(
                    "frameLength must be a positive integer: " + frameLength);
        }
        this.frameLength = frameLength;
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in  the {@link ByteBuf} from which to read data
     * @return frame           the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     * be created.
     */
    protected Object decode(
            @SuppressWarnings("UnusedParameters") ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (mProxy) {
            return in.readSlice(in.readableBytes()).retain();
        } else {
            if (in.readableBytes() < frameLength) {
                return null;
            } else {
                return in.readSlice(frameLength).retain();
            }
        }
    }

    @Override
    public void notifyProxyStateChange(boolean state) {
        LogUtil.d(TAG, "proxyStateChange::state is: " + state);
        mProxy = state;
    }

    public static class Builder {
        private Socks5FixedLengthFrameDecoder decoder;

        public Builder(int frameLength) {
            decoder = new Socks5FixedLengthFrameDecoder(frameLength);
        }

        public Socks5FixedLengthFrameDecoder.Builder setProxyState(boolean proxy) {
            decoder.mProxy = proxy;
            return this;
        }

        public Socks5FixedLengthFrameDecoder build() {
            return decoder;
        }
    }

}
