# Omalauncher+: the framework's hidden window classes are compiled against stubs (hidden-api-stubs/) and come from
# the device at run time.
-dontwarn android.window.**
# The framework calls the launch transition's methods through its hidden binder stub, which R8 can't see.
-keep class dev.omalauncher.anim.LaunchTransition { *; }
