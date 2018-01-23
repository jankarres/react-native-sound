#if __has_include(<React/RCTBridgeModule.h>)
#import <React/RCTBridgeModule.h>
#import "React/RCTEventEmitter.h"
#else
#import "RCTBridgeModule.h"
#import "RCTEventEmitter.h"
#endif

#import <AVFoundation/AVFoundation.h>

@interface RNSound : RCTEventEmitter <RCTBridgeModule, AVAudioPlayerDelegate>
@property(nonatomic, weak) NSNumber *_key;
@property(nonatomic, weak) NSTimer *_playbackTimer;
@end
