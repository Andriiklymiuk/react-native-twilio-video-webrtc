/**
 * Component for patching black screen bug coming from Twilio VideoView
 * Authors:
 * Aaron Alaniz (@aaalaniz) <aaron.a.alaniz@gmail.com>
 */

package com.twiliorn.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;

import com.twilio.video.I420Frame;
import com.twilio.video.VideoView;

import tvi.webrtc.RendererCommon;
import tvi.webrtc.YuvConverter;

import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static android.graphics.ImageFormat.NV21;

/*
 * VideoView that notifies Listener of the first frame rendered and the first frame after a reset
 * request.
 */
public class PatchedVideoView extends VideoView {
    private I420Frame currentFrame = null;
    private boolean notifyFrameRendered = false;
    private Listener listener;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public PatchedVideoView(Context context) {
        super(context);
    }

    public PatchedVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private Bitmap captureBitmapFromTexture(I420Frame i420Frame) {
        int width = i420Frame.rotatedWidth();
        int height = i420Frame.rotatedHeight();
        int outputFrameSize = width * height * 3 / 2;
        ByteBuffer outputFrameBuffer = ByteBuffer.allocateDirect(outputFrameSize);
        final float frameAspectRatio = (float) i420Frame.rotatedWidth() /
            (float) i420Frame.rotatedHeight();
        final float[] rotatedSamplingMatrix =
            RendererCommon.rotateTextureMatrix(i420Frame.samplingMatrix,
                i420Frame.rotationDegree);
        final float[] layoutMatrix = RendererCommon.getLayoutMatrix(false,
            frameAspectRatio,
            (float) width / height);
        final float[] texMatrix = RendererCommon.multiplyMatrices(rotatedSamplingMatrix,
            layoutMatrix);
        /*
         * YuvConverter must be instantiated on a thread that has an active EGL context. We know
         * that renderFrame is called from the correct render thread therefore
         * we defer instantiation of the converter until frame arrives.
         */
        YuvConverter yuvConverter = new YuvConverter();
        yuvConverter.convert(outputFrameBuffer,
            width,
            height,
            width,
            i420Frame.textureId,
            texMatrix);

        // Now we need to unpack the YUV data into planes
        byte[] data = outputFrameBuffer.array();
        int offset = outputFrameBuffer.arrayOffset();
        int stride = width;
        ByteBuffer[] yuvPlanes = new ByteBuffer[] {
            ByteBuffer.allocateDirect(width * height),
            ByteBuffer.allocateDirect(width * height / 4),
            ByteBuffer.allocateDirect(width * height / 4)
        };
        int[] yuvStrides = new int[] {
            width,
            (width + 1) / 2,
            (width + 1) / 2
        };

        // Write Y
        yuvPlanes[0].put(data, offset, width * height);

        // Write U
        for (int r = height ; r < height * 3 / 2; ++r) {
            yuvPlanes[1].put(data, offset + r * stride, stride / 2);
        }

        // Write V
        for (int r = height ; r < height * 3 / 2 ; ++r) {
            yuvPlanes[2].put(data, offset + r * stride + stride / 2, stride / 2);
        }

        // Convert the YuvImage
        YuvImage yuvImage = i420ToYuvImage(yuvPlanes, yuvStrides, width, height);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Rect rect = new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight());

        // Compress YuvImage to jpeg
        yuvImage.compressToJpeg(rect, 100, stream);

        // Convert jpeg to Bitmap
        byte[] imageBytes = stream.toByteArray();

        // Release YUV Converter
        yuvConverter.release();

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }
    private YuvImage i420ToYuvImage(ByteBuffer[] yuvPlanes,
                                                                    int[] yuvStrides,
                                                                    int width,
                                                                    int height) {
        if (yuvStrides[0] != width) {
            return fastI420ToYuvImage(yuvPlanes, yuvStrides, width, height);
        }
        if (yuvStrides[1] != width / 2) {
            return fastI420ToYuvImage(yuvPlanes, yuvStrides, width, height);
        }
        if (yuvStrides[2] != width / 2) {
            return fastI420ToYuvImage(yuvPlanes, yuvStrides, width, height);
        }

        byte[] bytes = new byte[yuvStrides[0] * height +
            yuvStrides[1] * height / 2 +
            yuvStrides[2] * height / 2];
        ByteBuffer tmp = ByteBuffer.wrap(bytes, 0, width * height);
        copyPlane(yuvPlanes[0], tmp);

        byte[] tmpBytes = new byte[width / 2 * height / 2];
        tmp = ByteBuffer.wrap(tmpBytes, 0, width / 2 * height / 2);

        copyPlane(yuvPlanes[2], tmp);
        for (int row = 0 ; row < height / 2 ; row++) {
            for (int col = 0 ; col < width / 2 ; col++) {
                bytes[width * height + row * width + col * 2]
                    = tmpBytes[row * width / 2 + col];
            }
        }
        copyPlane(yuvPlanes[1], tmp);
        for (int row = 0 ; row < height / 2 ; row++) {
            for (int col = 0 ; col < width / 2 ; col++) {
                bytes[width * height + row * width + col * 2 + 1] =
                    tmpBytes[row * width / 2 + col];
            }
        }
        return new YuvImage(bytes, NV21, width, height, null);
    }

    private YuvImage fastI420ToYuvImage(ByteBuffer[] yuvPlanes,
                                                                            int[] yuvStrides,
                                                                            int width,
                                                                            int height) {
        byte[] bytes = new byte[width * height * 3 / 2];
        int i = 0;
        for (int row = 0 ; row < height ; row++) {
            for (int col = 0 ; col < width ; col++) {
                bytes[i++] = yuvPlanes[0].get(col + row * yuvStrides[0]);
            }
        }
        for (int row = 0 ; row < height / 2 ; row++) {
            for (int col = 0 ; col < width / 2; col++) {
                bytes[i++] = yuvPlanes[2].get(col + row * yuvStrides[2]);
                bytes[i++] = yuvPlanes[1].get(col + row * yuvStrides[1]);
            }
        }
        return new YuvImage(bytes, NV21, width, height, null);
    }

    private Bitmap captureBitmapFromYuvFrame(I420Frame i420Frame) {
        YuvImage yuvImage = i420ToYuvImage(i420Frame.yuvPlanes,
            i420Frame.yuvStrides,
            i420Frame.width,
            i420Frame.height);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Rect rect = new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight());

        // Compress YuvImage to jpeg
        yuvImage.compressToJpeg(rect, 100, stream);

        // Convert jpeg to Bitmap
        byte[] imageBytes = stream.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Matrix matrix = new Matrix();

        // Apply any needed rotation
        matrix.postRotate(i420Frame.rotationDegree);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
            true);

        return bitmap;
    }

    private void copyPlane(ByteBuffer src, ByteBuffer dst) {
        src.position(0).limit(src.capacity());
        dst.put(src);
        dst.position(0).limit(dst.capacity());
    }

    @Override
    public void renderFrame(I420Frame frame) {
        if (notifyFrameRendered) {
            notifyFrameRendered = false;
            mainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onFirstFrame();
                }
            });
        }
        super.renderFrame(frame);
        currentFrame = frame;
    }

    /*
     * Set your listener
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /*
     * Reset the listener so next frame rendered results in callback
     */
    public void resetListener() {
        notifyFrameRendered = true;
    }

    public interface Listener {
        void onFirstFrame();
    }

    public Bitmap getCurrentFrameImage() {
        Bitmap bitmap = currentFrame.yuvPlanes == null ? captureBitmapFromTexture(currentFrame) : captureBitmapFromYuvFrame(currentFrame);
        return bitmap;
    }
}
