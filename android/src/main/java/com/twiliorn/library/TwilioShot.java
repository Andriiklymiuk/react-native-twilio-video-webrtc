package com.twiliorn.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.SparseArray;
import android.view.View;

import com.facebook.react.bridge.Promise;
import com.facebook.react.uimanager.NativeViewHierarchyManager;
import com.facebook.react.uimanager.UIBlock;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

/**
 * Snapshot utility class allow to screenshot a view.
 */
public class TwilioShot implements UIBlock {
    private BarcodeDetector detector;
    static final String ERROR_UNABLE_TO_SNAPSHOT = "E_UNABLE_TO_SNAPSHOT";
    private static final String ERROR_UNABLE_TO_DETECT_BARCODE = "E_UNABLE_TO_DETECT_BARCODE";

    private NativeViewHierarchyManager nativeViewHierarchyManager;
    TwilioShot(Context context) {
        detector = new BarcodeDetector.Builder(context)
           .setBarcodeFormats(Barcode.EAN_13 | Barcode.UPC_A | Barcode.UPC_E)
            .build();
    }

    @Override
    public void execute(NativeViewHierarchyManager nativeViewHierarchyManager) {
        this.nativeViewHierarchyManager = nativeViewHierarchyManager;
    }

    void detectBarcode(int tag, Promise promise) {
        if (tag != -1 && this.nativeViewHierarchyManager != null) {
            View view = this.nativeViewHierarchyManager.resolveView(tag);
            if (view instanceof TwilioVideoPreview) {
                Bitmap bitmap = ((TwilioVideoPreview)view).getSurfaceViewRenderer().getCurrentFrameImage();
                Frame frame = new Frame.Builder().setBitmap(bitmap).build();
                SparseArray<Barcode> barCodes = detector.detect(frame);
                if (barCodes.size() != 0) {
                    Barcode barcode = barCodes.valueAt(0);
                    promise.resolve(barcode.displayValue);
                } else {
                    promise.reject(ERROR_UNABLE_TO_DETECT_BARCODE, "No barcode detected");
                }
                return;
            }
        }
        promise.reject(ERROR_UNABLE_TO_SNAPSHOT, "No view found with reactTag: " + tag);
    }
}
