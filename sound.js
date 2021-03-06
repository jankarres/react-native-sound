'use strict';

var RNSound = require('react-native').NativeModules.RNSound;
var IsAndroid = RNSound.IsAndroid;
var IsWindows = RNSound.IsWindows;
var resolveAssetSource = require("react-native/Libraries/Image/resolveAssetSource");
import {
  DeviceEventEmitter,
  NativeModules,
  NativeEventEmitter,
  Platform
} from 'react-native';

var onPlayingCallbacks = new Map();
var onProgressCallbacks = new Map();
var nextKey = 0;

function isRelativePath(path) {
  return !/^(\/|http(s?)|asset)/.test(path);
}

function Sound(filename, basePath, onError, options) {
  var asset = resolveAssetSource(filename);
  if (asset) {
    this._filename = asset.uri;
    onError = basePath;
  } else {
    this._filename = basePath ? basePath + '/' + filename : filename;

    if (IsAndroid && !basePath && isRelativePath(filename)) {
      this._filename = filename.toLowerCase().replace(/\.[^.]+$/, '');
    }
  }

  this._loaded = false;
  this._key = nextKey++;
  this._duration = -1;
  this._numberOfChannels = -1;
  this._volume = 1;
  this._pan = 0;
  this._numberOfLoops = 0;
  this._speed = 1;
  RNSound.prepare(this._filename, this._key, options || {}, (error, props) => {
    if (props) {
      if (typeof props.duration === 'number') {
        this._duration = props.duration;
      }
      if (typeof props.numberOfChannels === 'number') {
        this._numberOfChannels = props.numberOfChannels;
      }
    }
    if (error === null) {
      this._loaded = true;
    }
    onError && onError(error, props);
  });
}

Sound.prototype.isLoaded = function () {
  return this._loaded;
};

Sound.prototype.play = function (onEnd) {
  if (this._loaded) {
    RNSound.play(this._key, (successfully) => onEnd && onEnd(successfully));
  } else {
    onEnd && onEnd(false);
  }
  return this;
};

Sound.prototype.pause = function (callback) {
  if (this._loaded) {
    RNSound.pause(this._key, () => {
      callback && callback()
    });
  }
  return this;
};

Sound.prototype.stop = function (callback) {
  if (this._loaded) {
    RNSound.stop(this._key, () => {
      callback && callback()
    });
  }
  return this;
};

Sound.prototype.on = function (event, callback) {
  if (event == "playing") {
    if (IsAndroid) {
      onPlayingCallbacks.set(this._key, callback);
    }

    return;
  } else if (event == "progress") {
    if (Platform.OS == "android") {
      onProgressCallbacks.set(this._key, callback);
    } else { // iOS
      onProgressCallbacks.set(0, callback);
    }

    return;
  }

  console.warn(`RNSound: Methode 'on' event '${String(event)}' is unknown.`);
};

if (Platform.OS == "android") {
  DeviceEventEmitter.addListener("RNSound-playing", (options) => {
    if (!options || options.key == null || options.key == undefined) {
      return;
    }

    var callback = onPlayingCallbacks.get(options.key);

    if (callback) {
      callback(options.isPlaying, options.currentTime);
    }
  });

  DeviceEventEmitter.addListener("RNSound-progress", (options) => {
    if (!options || options.key == null || options.key == undefined) {
      return;
    }

    var callback = onProgressCallbacks.get(options.key);

    if (callback) {
      callback(options.progress);
    }
  });
}

if (Platform.OS == "ios") {
  const moduleEvent = new NativeEventEmitter(NativeModules.RNSound);

  moduleEvent.addListener("RNSound-progress", (options) => {
    if (!options) {
      return;
    }

    var callback = onProgressCallbacks.get(0);

    if (callback) {
      callback(options);
    }
  });
}

Sound.prototype.reset = function () {
  console.warn("RNSound: Methode 'reset' is deprecated, because MediaPlayer have been replaced with ExoPlayer where no reset methode is required.");

  return this;
};

Sound.prototype.release = function () {
  if (this._loaded) {
    RNSound.release(this._key);
    this._loaded = false;
  }
  return this;
};

Sound.prototype.getDuration = function () {
  return this._duration;
};

Sound.prototype.getNumberOfChannels = function () {
  return this._numberOfChannels;
};

Sound.prototype.getVolume = function () {
  return this._volume;
};

Sound.prototype.setVolume = function (value) {
  this._volume = value;
  if (this._loaded) {
    if (IsWindows) {
      RNSound.setVolume(this._key, value, value);
    } else {
      RNSound.setVolume(this._key, value);
    }
  }
  return this;
};

Sound.prototype.getSystemVolume = function (callback) {
  if (IsAndroid) {
    RNSound.getSystemVolume(callback);
  }
  return this;
};

Sound.prototype.setSystemVolume = function (value) {
  if (IsAndroid) {
    RNSound.setSystemVolume(value);
  }
  return this;
};

Sound.prototype.getPan = function () {
  return this._pan;
};

Sound.prototype.setPan = function (value) {
  if (this._loaded) {
    RNSound.setPan(this._key, this._pan = value);
  }
  return this;
};

Sound.prototype.getNumberOfLoops = function () {
  return this._numberOfLoops;
};

Sound.prototype.setNumberOfLoops = function (value) {
  this._numberOfLoops = value;
  if (this._loaded) {
    if (IsAndroid || IsWindows) {
      RNSound.setLooping(this._key, !!value);
    } else {
      RNSound.setNumberOfLoops(this._key, value);
    }
  }
  return this;
};

Sound.prototype.setSpeed = function (value) {
  this._setSpeed = value;
  if (this._loaded) {
    if (!IsWindows) {
      RNSound.setSpeed(this._key, value);
    }
  }
  return this;
};

Sound.prototype.getCurrentTime = function (callback) {
  if (this._loaded) {
    RNSound.getCurrentTime(this._key, callback);
  }
};

Sound.prototype.setCurrentTime = function (value) {
  if (this._loaded) {
    RNSound.setCurrentTime(this._key, value);
  }
  return this;
};

// android only
Sound.prototype.setSpeakerphoneOn = function (value) {
  if (IsAndroid) {
    RNSound.setSpeakerphoneOn(this._key, value);
  }
};

// ios only

// This is deprecated.  Call the static one instead.

Sound.prototype.setCategory = function (value) {
  Sound.setCategory(value, false);
}

Sound.enable = function (enabled) {
  if (!IsAndroid) {
    RNSound.enable(enabled);
  }
};

Sound.enableInSilenceMode = function (enabled) {
  if (!IsAndroid && !IsWindows) {
    RNSound.enableInSilenceMode(enabled);
  }
};

Sound.setActive = function (value) {
  if (!IsAndroid && !IsWindows) {
    RNSound.setActive(value);
  }
};

Sound.setCategory = function (value, mixWithOthers = false) {
  if (!IsWindows) {
    RNSound.setCategory(value, mixWithOthers);
  }
};

Sound.setMode = function (value) {
  if (!IsAndroid && !IsWindows) {
    RNSound.setMode(value);
  }
};

Sound.MAIN_BUNDLE = RNSound.MainBundlePath;
Sound.DOCUMENT = RNSound.NSDocumentDirectory;
Sound.LIBRARY = RNSound.NSLibraryDirectory;
Sound.CACHES = RNSound.NSCachesDirectory;

export default Sound;