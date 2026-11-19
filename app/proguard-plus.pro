# Omalauncher+: the framework's hidden window classes are compiled against stubs (hidden-api-stubs/) and come from
# the device at run time.
-dontwarn android.window.**
-dontwarn android.view.IRemoteAnimationRunner**
-dontwarn android.view.IRemoteAnimationFinishedCallback
-dontwarn android.view.RemoteAnimationTarget
# The framework calls the launch and back animations through its hidden binder stubs, which R8 can't see.
-keep class dev.omalauncher.anim.** { *; }
