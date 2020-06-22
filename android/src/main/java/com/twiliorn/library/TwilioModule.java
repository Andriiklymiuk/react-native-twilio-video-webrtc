package com.twiliorn.library;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.uimanager.UIManagerModule;

public class TwilioModule extends ReactContextBaseJavaModule {
    private ReactApplicationContext context;
    private TwilioShot twilioShot;

    public TwilioModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    @Override
    public String getName() {
        return "TwilioModule";
    }

    @ReactMethod
    public void detectBarcode(int tag, Promise promise) {
        final ReactApplicationContext context = getReactApplicationContext();
        try {
            if (twilioShot == null) {
                UIManagerModule uiManager = this.context.getNativeModule(UIManagerModule.class);
                twilioShot = new TwilioShot(context);
                uiManager.addUIBlock(twilioShot);
            }
        } catch (final Throwable e) {
            e.printStackTrace();
        }
        try {
            twilioShot.detectBarcode(tag, promise);
        } catch (final Throwable e) {
            e.printStackTrace();
            promise.reject(TwilioShot.ERROR_UNABLE_TO_SNAPSHOT, "Failed to snapshot view tag " + tag);
        }
    }
}
