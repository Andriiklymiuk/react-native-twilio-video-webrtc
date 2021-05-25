/**
 * Component for Twilio Video local views.
 *
 * Authors:
 *   Jonathan Chang <slycoder@gmail.com>
 */

import { requireNativeComponent, View, findNodeHandle, NativeModules } from 'react-native'
import React from 'react'
const { TwilioModule } = NativeModules;


const acceptedFormats = ["png", "jpg"].concat(
  Platform.OS === "android" ? ["webm", "raw"] : []
);

const acceptedResults = ["tmpfile", "base64", "data-uri"].concat(
  Platform.OS === "android" ? ["zip-base64"] : []
);

const defaultOptions = {
  format: "png",
  quality: 100,
  result: "tmpfile",
  snapshotContentContainer: false
};

// validate and coerce options
function validateOptions(options) {
  options = { ...defaultOptions, ...options };
  const errors = [];
  if ("width" in options && (typeof options.width !== "number" || options.width <= 0)) {
    errors.push("option width should be a positive number");
    delete options.width;
  }
  if ("height" in options && (typeof options.height !== "number" || options.height <= 0)) {
    errors.push("option height should be a positive number");
    delete options.height;
  }
  if (typeof options.quality !== "number" || options.quality < 0 || options.quality > 100) {
    errors.push("option quality should be a number between 0 and 100");
    options.quality = defaultOptions.quality;
  }
  if (typeof options.snapshotContentContainer !== "boolean") {
    errors.push("option snapshotContentContainer should be a boolean");
  }
  if (acceptedFormats.indexOf(options.format) === -1) {
    options.format = defaultOptions.format;
    errors.push(
      "option format '" + options.format + "' is not in valid formats: " + acceptedFormats.join(" | ")
    );
  }
  if (acceptedResults.indexOf(options.result) === -1) {
    options.result = defaultOptions.result;
    errors.push(
      "option result '" + options.result + "' is not in valid formats: " + acceptedResults.join(" | ")
    );
  }
  return { options, errors };
}
function detectBarcode(view) {
  if (view && typeof view === "object" && "current" in view && view.current) { // React.RefObject
    view = view.current;
  }
  if (typeof view !== "number") {
    const node = findNodeHandle(view);
    if (!node)
      return Promise.reject(
        new Error("findNodeHandle failed to resolve view=" + String(view))
      );
    view = node;
  }
  return TwilioModule.detectBarcode(view);
}
import PropTypes from 'prop-types'

const propTypes = {
  ...View.propTypes,
  /**
   * How the video stream should be scaled to fit its
   * container.
   */
  scaleType: PropTypes.oneOf(['fit', 'fill'])
}

class TwilioVideoPreview extends React.Component {
  detectBarcode() {
    return detectBarcode(this);
  }
  render() {
    return <NativeTwilioVideoPreview {...this.props} />
  }
}

TwilioVideoPreview.propTypes = propTypes

const NativeTwilioVideoPreview = requireNativeComponent(
  'RNTwilioVideoPreview',
  TwilioVideoPreview
)

module.exports = TwilioVideoPreview
